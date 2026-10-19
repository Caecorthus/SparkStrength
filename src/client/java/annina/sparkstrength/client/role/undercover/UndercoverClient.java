package annina.sparkstrength.client.role.undercover;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.role.corruptcop.CorruptCopClientHooks;
import annina.sparkstrength.client.role.perfumer.PerfumerClientEffects;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.undercover.UndercoverPlayerComponent;
import annina.sparkstrength.network.undercover.UndercoverMaskOffC2SPacket;
import annina.sparkstrength.role.undercover.UndercoverRoles;
import annina.sparkstrength.role.undercover.UndercoverRules;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import annina.sparkstrength.SparkStrength;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.client.NoellesrolesClient;

/**
 * Client side of the reworked Undercover.
 * <ul>
 *   <li>Killers' view of a stage-2 Undercover: silver in line of sight, nothing otherwise (instinct key or not), and no
 *   red "cohort" label any more.</li>
 *   <li>The stage-2 Undercover's thermal vision: the NoellesRoles ability key toggles it, no cooldown; living players in
 *   line of sight (invisible ones too) turn magenta, which the thermal post shader renders as white heat.</li>
 *   <li>Empty hand, Shift + right-click at nothing: take the mask off (stage 2 stays).</li>
 * </ul>
 * 重做后卧底的客户端：
 * 杀手眼中的二阶段卧底：视线内为银色，否则什么都没有（按不按本能键都一样），也不再显示红色「同伙」标签；
 * 二阶段卧底的热成像：NoellesRoles 技能键开关，没有冷却；视线内的活人（含隐身者）变成品红，由热成像后处理渲染为白热；
 * 空手 Shift + 右键空处：摘下面具（第二阶段不变）。
 */
public final class UndercoverClient {
    /** "This rule has nothing to say": let the original highlight run. / 「本规则不适用」：交给原本的高亮逻辑。 */
    public static final int NO_DECISION = Integer.MIN_VALUE;
    private static final int HUD_COLOR = 0xC0C0C0;

    private static boolean registered;
    private static boolean thermalOn;
    private static boolean abilityKeyWasDown;
    private static boolean useKeyWasDown;

    private UndercoverClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(UndercoverClient::tick);
        ShouldShowCohort.EVENT.register(UndercoverClient::cohort);
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            thermalOn = false;
            UndercoverThermalRenderer.close();
        }));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> UndercoverThermalRenderer.close());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return SparkStrength.id("undercover_thermal");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().execute(UndercoverThermalRenderer::close);
                    }
                });
    }

    public static boolean isThermalOn() {
        return thermalOn;
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !UndercoverRoles.isLivingStage2(player)) {
            thermalOn = false;
            abilityKeyWasDown = false;
            useKeyWasDown = false;
            return;
        }
        KeyBinding abilityBind = NoellesrolesClient.abilityBind;
        boolean abilityDown = abilityBind != null && abilityBind.isPressed();
        if (abilityDown && !abilityKeyWasDown && client.currentScreen == null) {
            thermalOn = !thermalOn;
            player.sendMessage(Text.translatable(thermalOn
                    ? "message.sparkstrength.undercover.thermal_on" : "message.sparkstrength.undercover.thermal_off"),
                    true);
        }
        abilityKeyWasDown = abilityDown;

        boolean useDown = client.options.useKey.isPressed();
        if (useDown && !useKeyWasDown && client.currentScreen == null && player.isSneaking()
                && player.getMainHandStack().isEmpty()
                && player.getEquippedStack(EquipmentSlot.HEAD).isOf(SparkStrengthItems.cqcmMask())
                && (client.crosshairTarget == null || client.crosshairTarget.getType() == HitResult.Type.MISS)) {
            ClientPlayNetworking.send(new UndercoverMaskOffC2SPacket());
        }
        useKeyWasDown = useDown;
    }

    /**
     * Colour decision for {@code getInstinctHighlight}, called from UndercoverInstinctArbiterMixin. Returns
     * {@link #NO_DECISION} when neither rule applies.
     * getInstinctHighlight 的颜色裁决，由 UndercoverInstinctArbiterMixin 调用；两条规则都不适用时返回 NO_DECISION。
     */
    public static int highlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer) || targetPlayer == viewer) {
            return NO_DECISION;
        }
        // SparkStrength's own vetoes still win, whatever order the wrappers end up in.
        // 无论包装器最终顺序如何，SparkStrength 自己的否决依然优先。
        if (PerfumerClientEffects.blocksInstinct() || CorruptCopClientHooks.shouldConcealInstinct(target)) {
            return NO_DECISION;
        }
        boolean targetAlive = GameFunctions.isPlayerPlayingAndAlive(targetPlayer);
        double distance = viewer.distanceTo(targetPlayer);
        if (targetAlive && UndercoverRoles.isUndercover(targetPlayer)
                && UndercoverPlayerComponent.KEY.get(targetPlayer).isStage2()) {
            Integer killerView = UndercoverRules.killerViewOfStage2(isLivingKiller(viewer), true,
                    targetPlayer.isInvisible(), distance, UndercoverSight.canSee(viewer, targetPlayer));
            if (killerView != null) {
                return killerView;
            }
        }
        if (thermalOn && UndercoverRoles.isLivingStage2(viewer)) {
            Integer thermal = UndercoverRules.thermalView(true, targetAlive, distance,
                    targetAlive && distance <= UndercoverRules.SIGHT_RANGE && UndercoverSight.canSee(viewer, targetPlayer));
            if (thermal != null) {
                return thermal;
            }
        }
        return NO_DECISION;
    }

    private static boolean isLivingKiller(PlayerEntity viewer) {
        return GameFunctions.isPlayerPlayingAndAlive(viewer) && SparkTraitsCompat.isEffectiveKiller(
                GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer), viewer);
    }

    /**
     * A stage-2 Undercover is no killer's "cohort" any more. Priority 110 beats both NoellesRoles' show (0) and
     * SparkTraits' Impostor-viewer show (100).
     * 二阶段卧底不再是任何杀手的「同伙」。优先级 110 压过 NoellesRoles 的显示（0）与 SparkTraits 内鬼观察者的显示（100）。
     */
    private static ShouldShowCohort.CohortResult cohort(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null || !UndercoverRoles.isUndercover(target)
                || !UndercoverPlayerComponent.KEY.get(target).isStage2() || !isLivingKiller(viewer)) {
            return null;
        }
        return ShouldShowCohort.CohortResult.hide(110);
    }

    private static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || !UndercoverRoles.isLivingStage2(player)
                || NoellesrolesClient.abilityBind == null) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        Text text = Text.translatable(thermalOn ? "tip.sparkstrength.undercover.thermal_on"
                : "tip.sparkstrength.undercover.thermal_off", NoellesrolesClient.abilityBind.getBoundKeyLocalizedText());
        int x = context.getScaledWindowWidth() - renderer.getWidth(text);
        int y = context.getScaledWindowHeight() - renderer.fontHeight;
        context.drawTextWithShadow(renderer, text, x, y, HUD_COLOR);
    }
}
