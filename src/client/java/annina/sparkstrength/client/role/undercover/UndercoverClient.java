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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.sound.PositionedSoundInstance;
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

import java.util.function.IntSupplier;

/**
 * Client side of the reworked Undercover.
 * <ul>
 *   <li>Nobody sees a stage-2 Undercover through walls, whatever their faction or ability. In view: killers see
 *   silver (no key needed), anyone whose ability would have revealed them sees silver, everyone else nothing. No red
 *   "cohort" label for killers any more.</li>
 *   <li>The stage-2 Undercover's thermal vision (T-7 style): the NoellesRoles ability key toggles it, no cooldown; the
 *   image itself comes from UndercoverThermalRenderer.</li>
 *   <li>Empty hand, Shift + right-click (not at a player): take the mask off (stage 2 stays).</li>
 * </ul>
 * 重做后卧底的客户端：
 * 任何阵营、任何能力都不能透视二阶段卧底。视野内：杀手看到银色（不用按键），原本能揭示他的人看到银色，其他人什么都没有；
 * 杀手也不再看到红色「同伙」标签；
 * 二阶段卧底的热成像（T-7 风格）：NoellesRoles 技能键开关，没有冷却；画面由 UndercoverThermalRenderer 生成；
 * 空手 Shift + 右键（不对着玩家）：摘下面具（第二阶段不变）。
 */
public final class UndercoverClient {
    /** "This rule has nothing to say": let the original highlight run. / 「本规则不适用」：交给原本的高亮逻辑。 */
    public static final int NO_DECISION = Integer.MIN_VALUE;
    private static final int HUD_COLOR = 0xC0C0C0;
    private static final int HUD_PLATE_COLOR = 0xA0000000;
    /** Wathe's ordinary instinct colours (NoellesRoles paints the Undercover in the killer red). / Wathe 普通本能颜色。 */
    private static final int INSTINCT_KILLER_RED = 0x990000;
    private static final int INSTINCT_CIVILIAN_GREEN = 0x4EDD35;
    private static final int HOTBAR_HEIGHT = 22;

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
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(UndercoverThermalRenderer::captureHeat);
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
            // The HUD line already says ON/OFF; the switch only clicks. / HUD 已显示开关状态，切换时只响一声。
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_LEVER_CLICK,
                    thermalOn ? 1.3F : 0.9F, 0.35F));
        }
        abilityKeyWasDown = abilityDown;

        boolean useDown = client.options.useKey.isPressed();
        if (useDown && !useKeyWasDown && client.currentScreen == null && player.isSneaking()
                && player.getMainHandStack().isEmpty()
                && player.getEquippedStack(EquipmentSlot.HEAD).isOf(SparkStrengthItems.cqcmMask())
                // Any aim but a player: indoors a wall or ceiling is almost always within reach.
                // 只要不是对着玩家都行：室内几乎总有墙或天花板在触及范围内。
                && (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY)) {
            ClientPlayNetworking.send(new UndercoverMaskOffC2SPacket());
        }
        useKeyWasDown = useDown;
    }

    /**
     * Colour decision for {@code getInstinctHighlight}, called from UndercoverInstinctArbiterMixin. Returns
     * {@link #NO_DECISION} when neither rule applies.
     * getInstinctHighlight 的颜色裁决，由 UndercoverInstinctArbiterMixin 调用；两条规则都不适用时返回 NO_DECISION。
     */
    public static int highlight(Entity target, IntSupplier original) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer) || targetPlayer == viewer) {
            return NO_DECISION;
        }
        // SparkStrength's own vetoes still win, whatever order the wrappers end up in.
        // 无论包装器最终顺序如何，SparkStrength 自己的否决依然优先。
        boolean vetoed = PerfumerClientEffects.blocksInstinct() || CorruptCopClientHooks.shouldConcealInstinct(target);
        if (isLivingStage2Target(targetPlayer)) {
            // No faction sees a stage-2 Undercover through walls; whoever would reveal them sees silver in view. Decided
            // here for every viewer, never handed back to the original, so no ability inside can leak them.
            // 任何阵营都不能透视二阶段卧底；原本能揭示他的人在视野内看到银色。对所有观察者都在此裁决，绝不交回原逻辑，
            // 内层任何能力都泄露不了。
            if (vetoed || !inSight(viewer, targetPlayer)) {
                return -1;
            }
            boolean killer = isLivingKiller(viewer);
            return UndercoverRules.viewOfStage2(killer, true, killer ? -1 : original.getAsInt());
        }
        if (vetoed) {
            return NO_DECISION;
        }
        boolean targetAlive = GameFunctions.isPlayerPlayingAndAlive(targetPlayer);
        double distance = viewer.distanceTo(targetPlayer);
        // Thermal draws its own heat image; only when it can't (an Iris shader pack, or a failed post chain) does it
        // fall back to white outlines on the living players in sight. / 热成像自己绘制热图；只有画不了时（Iris 光影包，
        // 或后处理加载失败）才退回为视线内活人的白色描边。
        if (thermalOn && UndercoverRoles.isLivingStage2(viewer) && UndercoverThermalRenderer.outlineFallback()) {
            Integer thermal = UndercoverRules.thermalView(true, targetAlive, distance,
                    targetAlive && distance <= UndercoverRules.SIGHT_RANGE && UndercoverSight.canSee(viewer, targetPlayer));
            if (thermal != null) {
                return specialRevealOr(original, thermal);
            }
        }
        return NO_DECISION;
    }

    /**
     * Special reveals (Final Moment, Jester or Fiend moments...) paint their own colours; when the original answer is
     * neither nothing nor Wathe's ordinary killer/civilian instinct colour, that reveal wins over this rule.
     * 特殊揭示（最终时刻、小丑时刻、魔人时刻……）有自己的颜色；原本的答案既不是「无」也不是 Wathe 普通的杀手/平民本能色时，
     * 以该揭示为准。
     */
    private static int specialRevealOr(IntSupplier original, int rule) {
        int base = original.getAsInt();
        return base == -1 || base == INSTINCT_KILLER_RED || base == INSTINCT_CIVILIAN_GREEN ? rule : base;
    }

    /**
     * MinecraftClient#hasOutline veto: vanilla glowing (Noisemaker backpack, Conscience bomb...) never outlines a
     * stage-2 Undercover the viewer can't plainly see. / 原版发光（噪音制造者背包、良心炸弹……）不会给观察者看不见的二阶段卧底描边。
     */
    public static boolean hidesOutline(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        return viewer != null && target instanceof PlayerEntity targetPlayer && targetPlayer != viewer
                && isLivingStage2Target(targetPlayer) && !inSight(viewer, targetPlayer);
    }

    private static boolean isLivingStage2Target(PlayerEntity target) {
        return GameFunctions.isPlayerPlayingAndAlive(target) && UndercoverRoles.isUndercover(target)
                && UndercoverPlayerComponent.KEY.get(target).isStage2();
    }

    private static boolean inSight(PlayerEntity viewer, PlayerEntity target) {
        return UndercoverRules.stage2InSight(target.isInvisible(), viewer.distanceTo(target),
                UndercoverSight.canSee(viewer, target));
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
        // Right edge, just above the hotbar row, so it never runs under the hotbar at large GUI scales. A dark plate keeps
        // it readable over the white-hot arm under thermal.
        // 右侧、快捷栏一行的上方，GUI 缩放较大时也不会压在快捷栏下面。深色底板让它在热成像下压在白热的手臂上也看得清。
        int x = context.getScaledWindowWidth() - renderer.getWidth(text) - 4;
        int y = context.getScaledWindowHeight() - HOTBAR_HEIGHT - renderer.fontHeight - 4;
        context.fill(x - 2, y - 2, x + renderer.getWidth(text) + 2, y + renderer.fontHeight + 1, HUD_PLATE_COLOR);
        context.drawTextWithShadow(renderer, text, x, y, HUD_COLOR);
    }
}
