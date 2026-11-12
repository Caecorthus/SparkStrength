package annina.sparkstrength.client.role.bandit;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.screen.bandit.BanditWorkbenchScreen;
import annina.sparkstrength.item.BanditRevolverItem;
import annina.sparkstrength.network.bandit.BanditRevolverActionC2SPayload;
import annina.sparkstrength.network.bandit.BanditRevolverShootC2SPayload;
import annina.sparkstrength.role.bandit.BanditAmmo;
import annina.sparkstrength.role.bandit.BanditAttachment;
import annina.sparkstrength.role.bandit.BanditRevolverRules;
import annina.sparkstrength.role.bandit.BanditRevolverService;
import annina.sparkstrength.role.bandit.BanditRevolverState;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.item.RevolverItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.client.NoellesrolesClient;

/**
 * Client half of the Bandit's moddable revolver: the shot (with the long barrel's 0.2 s held trigger), loading with the
 * NoellesRoles ability key, the cylinder HUD under the crosshair, the item model predicates and the workbench opener.
 * 强盗改装左轮的客户端部分：开火（含长枪管须按住 0.2 秒的扳机）、用 NoellesRoles 技能键装填、准星下方的弹巢 HUD、
 * 物品模型谓词以及改装界面的打开入口。
 */
public final class BanditRevolverClient {
    private static final int COLOR_PS12 = 0xFFE0B050;
    private static final int COLOR_PS12B = 0xFF6FB6E8;
    private static final int COLOR_EMPTY = 0x70000000;
    private static final int COLOR_FRAME = 0xA0FFFFFF;
    private static final int PIP = 4;
    private static final int PIP_GAP = 2;

    private static boolean registered;
    /** Trigger held for a long-barrel shot. / 长枪管射击的扳机按住中。 */
    private static boolean holding;
    private static int holdTicks;
    private static boolean abilityKeyWasDown;
    private static long lastDryFireTick = Long.MIN_VALUE;
    /** Local mirror of the server's seating progress, for the HUD only. / 服务端到位进度的本地镜像，仅用于 HUD。 */
    private static int loadProgress;
    private static int lastPending = -1;
    private static int lastChambered = -1;

    private BanditRevolverClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BanditRevolverItem.installClientBridge(new BanditRevolverItem.ClientBridge() {
            @Override
            public void use(PlayerEntity user, ItemStack stack) {
                BanditRevolverClient.use(user, stack);
            }

            @Override
            public void openWorkbench() {
                // After the click finished: opening a screen inside the slot click would tear down the inventory
                // screen that is still handling it. / 等点击处理完再打开：在格子点击内部换屏会拆掉仍在处理它的背包界面。
                MinecraftClient client = MinecraftClient.getInstance();
                client.send(() -> client.setScreen(new BanditWorkbenchScreen()));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(BanditRevolverClient::tick);
        HudRenderCallback.EVENT.register(BanditRevolverClient::renderHud);
        // Thresholds used by models/item/bandit_revolver.json: 0 = stock, 0.5 / 1 = the two parts of the slot.
        // models/item/bandit_revolver.json 使用的阈值：0 为原装，0.5 / 1 为该部位的两种配件。
        ModelPredicateProviderRegistry.register(SparkStrengthItems.banditRevolver(),
                SparkStrength.id("bandit_barrel"), (stack, world, entity, seed) -> switch (state(stack).barrelPart()) {
                    case LONG_BARREL -> 0.5F;
                    case SHORT_BARREL -> 1.0F;
                    case null, default -> 0.0F;
                });
        ModelPredicateProviderRegistry.register(SparkStrengthItems.banditRevolver(),
                SparkStrength.id("bandit_grip"), (stack, world, entity, seed) -> switch (state(stack).gripPart()) {
                    case SPEED_GRIP -> 0.5F;
                    case RELOAD_GRIP -> 1.0F;
                    case null, default -> 0.0F;
                });
        ModelPredicateProviderRegistry.register(SparkStrengthItems.banditRevolver(),
                SparkStrength.id("bandit_cylinder"), (stack, world, entity, seed) ->
                        state(stack).cylinderPart() == BanditAttachment.EXPANDED_CYLINDER ? 1.0F : 0.0F);
    }

    private static BanditRevolverState state(ItemStack stack) {
        return BanditRevolverItem.state(stack);
    }

    /** Right-click with the revolver in the main hand (vanilla skips this while the item cools down). / 主手持枪右键。 */
    private static void use(PlayerEntity user, ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (user != client.player) {
            return;
        }
        BanditRevolverState state = state(stack);
        if (state.rounds().isEmpty()) {
            dryFire(client.player, state);
            return;
        }
        if (BanditRevolverRules.holdTicks(state.barrelPart()) <= 0) {
            fire(client.player, state);
        } else if (!holding) {
            holding = true;
            holdTicks = 0;
        }
    }

    private static void dryFire(ClientPlayerEntity player, BanditRevolverState state) {
        long now = player.getWorld().getTime();
        if (now - lastDryFireTick < 8) {
            return;
        }
        lastDryFireTick = now;
        player.playSound(WatheSounds.ITEM_REVOLVER_CLICK, 0.5F, 1.2F);
        KeyBinding ability = NoellesrolesClient.abilityBind;
        player.sendMessage(state.pending().isEmpty()
                ? Text.translatable("tip.sparkstrength.bandit_revolver.empty",
                ability == null ? Text.literal("G") : ability.getBoundKeyLocalizedText())
                : Text.translatable("tip.sparkstrength.bandit_revolver.seating"), true);
    }

    private static void fire(ClientPlayerEntity player, BanditRevolverState state) {
        HitResult aim = BanditRevolverItem.gunTarget(player, BanditRevolverRules.rangeBlocks(state.barrelPart()));
        int targetId = RevolverItem.resolveTargetFromHitResult(player.getWorld(), aim);
        ClientPlayNetworking.send(new BanditRevolverShootC2SPayload(targetId));
        player.setPitch(player.getPitch() - 4.0F);
        RevolverItem.spawnHandParticle();
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            holding = false;
            abilityKeyWasDown = false;
            return;
        }
        ItemStack held = player.getMainHandStack();
        boolean holdingGun = BanditRevolverItem.isRevolver(held);
        BanditRevolverState state = holdingGun ? state(held) : BanditRevolverState.EMPTY;

        if (holding) {
            if (!holdingGun || client.currentScreen != null || !client.options.useKey.isPressed()
                    || state.rounds().isEmpty() || BanditRevolverRules.holdTicks(state.barrelPart()) <= 0
                    || player.getItemCooldownManager().isCoolingDown(held.getItem())) {
                holding = false;
            } else if (++holdTicks >= BanditRevolverRules.holdTicks(state.barrelPart())) {
                holding = false;
                fire(player, state);
            }
        }

        KeyBinding ability = NoellesrolesClient.abilityBind;
        boolean abilityDown = ability != null && ability.isPressed();
        if (abilityDown && !abilityKeyWasDown && holdingGun && client.currentScreen == null) {
            ClientPlayNetworking.send(new BanditRevolverActionC2SPayload(BanditRevolverService.Action.LOAD_ALL, 0));
        }
        abilityKeyWasDown = abilityDown;

        if (!holdingGun) {
            // Paused, like the server: progress continues when the gun is drawn again. / 与服务端一致地暂停，重新持枪后继续。
            return;
        }
        int pending = state.pending().size();
        int chambered = state.rounds().size();
        if (pending == 0 || pending != lastPending || chambered != lastChambered) {
            loadProgress = 0;
        } else {
            loadProgress = Math.min(loadProgress + 1, BanditRevolverRules.loadTicks(state.cylinderPart()));
        }
        lastPending = pending;
        lastChambered = chambered;
    }

    /** Crosshair helper: the player the gun would hit now, or null. / 准星用：此刻会命中的玩家，没有则为 null。 */
    public static EntityHitResult findTarget(PlayerEntity user, ItemStack stack) {
        BanditRevolverState state = state(stack);
        if (state.rounds().isEmpty()) {
            return null;
        }
        return BanditRevolverItem.gunTarget(user, BanditRevolverRules.rangeBlocks(state.barrelPart()))
                instanceof EntityHitResult hit ? hit : null;
    }

    /** 0..1 progress of the long barrel's held trigger, 0 when idle. / 长枪管按住扳机的进度（0..1），空闲时为 0。 */
    public static float holdProgress(float tickDelta) {
        if (!holding) {
            return 0.0F;
        }
        return Math.min(1.0F, (holdTicks + tickDelta) / BanditRevolverRules.LONG_BARREL_HOLD_TICKS);
    }

    /**
     * Cylinder under the crosshair while the revolver is held: one pip per chamber in firing order (PS12 brass, PS12B
     * blue), pending rounds as outlines with the seating one filling up, and the held-trigger bar of the long barrel.
     * 手持左轮时准星下方的弹巢：每个弹膛一个小方块，按击发顺序排列（PS12 黄铜色、PS12B 蓝色）；待到位的子弹为空心框，
     * 正在到位的一发逐渐填满；长枪管另有按住扳机的进度条。
     */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || client.currentScreen != null || player.isSpectator()
                || !client.options.getPerspective().isFirstPerson()) {
            return;
        }
        ItemStack held = player.getMainHandStack();
        if (!BanditRevolverItem.isRevolver(held)) {
            return;
        }
        BanditRevolverState state = state(held);
        int capacity = state.capacity();
        int width = capacity * PIP + (capacity - 1) * PIP_GAP;
        int left = (context.getScaledWindowWidth() - width) / 2;
        int top = context.getScaledWindowHeight() / 2 + 10;
        int chambered = state.rounds().size();
        for (int index = 0; index < capacity; index++) {
            int x = left + index * (PIP + PIP_GAP);
            BanditAmmo ammo = state.roundAt(index);
            context.fill(x, top, x + PIP, top + PIP, COLOR_EMPTY);
            if (ammo == null) {
                continue;
            }
            int color = ammo == BanditAmmo.PS12B ? COLOR_PS12B : COLOR_PS12;
            if (index < chambered) {
                context.fill(x, top, x + PIP, top + PIP, color);
            } else {
                int dim = (color & 0x00FFFFFF) | 0x66000000;
                context.fill(x, top, x + PIP, top + 1, dim);
                context.fill(x, top + PIP - 1, x + PIP, top + PIP, dim);
                context.fill(x, top + 1, x + 1, top + PIP - 1, dim);
                context.fill(x + PIP - 1, top + 1, x + PIP, top + PIP - 1, dim);
                if (index == chambered) {
                    int filled = Math.round((float) PIP * loadProgress
                            / BanditRevolverRules.loadTicks(state.cylinderPart()));
                    context.fill(x, top + PIP - filled, x + PIP, top + PIP, color);
                }
            }
        }
        if (chambered > 0) {
            // The round the next shot fires. / 下一枪击发的那一发。
            context.fill(left, top + PIP + 1, left + PIP, top + PIP + 2, COLOR_FRAME);
        }
        float hold = holdProgress(tickCounter.getTickDelta(false));
        if (hold > 0.0F) {
            int barLeft = context.getScaledWindowWidth() / 2 - 8;
            int barTop = context.getScaledWindowHeight() / 2 + 5;
            context.fill(barLeft, barTop, barLeft + 16, barTop + 2, COLOR_EMPTY);
            context.fill(barLeft, barTop, barLeft + Math.round(16 * hold), barTop + 2, COLOR_FRAME);
        }
    }
}
