package annina.sparkstrength.role.veteran;

import annina.sparkstrength.component.veteran.VeteranKnifeComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerVeteranComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/**
 * Server-authoritative Veteran knife rules.
 * 老兵匕首的服务端权威规则。
 *
 * <p>A mixin calls this service at the start of {@link KnifeStabPayload.Receiver#receive}.
 * Veterans keep Wathe's normal wind-up, prepare sound, and stab-impact sound, while this service
 * owns cumulative multi-knife uses and the fixed Veteran cooldown.</p>
 * <p>Mixin 在 {@link KnifeStabPayload.Receiver#receive} 开头调用本服务。老兵保留 Wathe 原有的蓄力、
 * 举刀准备声与刺杀命中声；累计多把匕首次数和老兵固定冷却仍由本服务负责。</p>
 */
public final class VeteranKnifeService {
    private VeteranKnifeService() {
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        VeteranKnifeComponent knife = VeteranKnifeComponent.KEY.get(player);
        if (VeteranRules.isVeteran(role)) {
            knife.initializeStartingKnife();
            syncWatheVeteranGuard(player, knife);
        } else {
            knife.reset();
            PlayerVeteranComponent.KEY.get(player).reset();
        }
    }

    public static void reset(ServerPlayerEntity player) {
        VeteranKnifeComponent.KEY.get(player).reset();
        PlayerVeteranComponent.KEY.get(player).reset();
    }

    /**
     * @return {@code true} when the Veteran path handled this payload and Wathe's receiver must stop;
     *         {@code true} 表示本次 payload 已由老兵逻辑处理，原 Wathe 刀人逻辑必须取消。
     */
    public static boolean handleKnifeStab(KnifeStabPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        if (!VeteranRules.isVeteran(role)) {
            return false;
        }

        if (player.getItemCooldownManager().isCoolingDown(WatheItems.KNIFE)) {
            return true;
        }

        // Veteran payloads stay on the custom path so cumulative uses remain authoritative.
        // 老兵刀包继续由自定义路径处理，确保累计次数组件保持服务端权威。
        if (player.isSpectator()) {
            return true;
        }
        Entity targetEntity = player.getServerWorld().getEntityById(payload.target());
        if (!(targetEntity instanceof ServerPlayerEntity target)
                || target.isSpectator()
                || target.distanceTo(player) > 3.0D) {
            return true;
        }
        if (!isHoldingKnife(player)) {
            return true;
        }

        VeteranKnifeComponent knife = VeteranKnifeComponent.KEY.get(player);
        adoptExistingWatheUsesIfNeeded(player, knife);
        if (!knife.hasStabUsesLeft()) {
            syncWatheVeteranGuard(player, knife);
            return true;
        }

        Hand usedHand = heldKnifeHand(player);
        knife.useStab();
        if (VeteranRules.shouldRemoveKnifeAfterUse(knife.getStabUsesLeft())) {
            removeOneHeldOrInventoryKnife(player);
        }
        syncWatheVeteranGuard(player, knife);

        // 保留 Wathe 的回放记录：之后复盘仍能看到老兵使用了刀。
        GameRecordManager.recordItemUse(
                player,
                Registries.ITEM.getId(WatheItems.KNIFE),
                target,
                null
        );

        GameFunctions.killPlayer(target, true, player, GameConstants.DeathReasons.KNIFE);
        target.playSound(WatheSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        player.swingHand(usedHand);
        player.getItemCooldownManager().set(WatheItems.KNIFE, VeteranRules.KNIFE_COOLDOWN_TICKS);
        return true;
    }

    public static void addPurchasedKnife(ServerPlayerEntity player) {
        VeteranKnifeComponent knife = VeteranKnifeComponent.KEY.get(player);
        knife.addKnife();
        syncWatheVeteranGuard(player, knife);
    }

    private static void adoptExistingWatheUsesIfNeeded(ServerPlayerEntity player, VeteranKnifeComponent knife) {
        if (knife.hasStabUsesLeft()) {
            return;
        }
        PlayerVeteranComponent watheVeteran = PlayerVeteranComponent.KEY.get(player);
        if (watheVeteran.hasStabUsesLeft()) {
            // 兼容中途加载/旧局状态：如果 Wathe 原组件还有开局刀次数，而我们还没记录，就接管过来。
            knife.addStabUses(watheVeteran.getStabUsesLeft());
        }
    }

    private static void syncWatheVeteranGuard(ServerPlayerEntity player, VeteranKnifeComponent knife) {
        PlayerVeteranComponent watheVeteran = PlayerVeteranComponent.KEY.get(player);
        if (knife.hasStabUsesLeft()) {
            // 原组件最多只能记 2 次，这里只把它当作“老兵仍有可用刀，不能捡枪”的布尔标记。
            watheVeteran.initialize();
        } else {
            watheVeteran.reset();
        }
    }

    private static boolean isHoldingKnife(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(WatheItems.KNIFE)
                || player.getOffHandStack().isOf(WatheItems.KNIFE);
    }

    private static Hand heldKnifeHand(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(WatheItems.KNIFE) ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    private static void removeOneHeldOrInventoryKnife(ServerPlayerEntity player) {
        if (removeOneKnifeFromStack(player.getMainHandStack())) {
            return;
        }
        if (removeOneKnifeFromStack(player.getOffHandStack())) {
            return;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(WatheItems.KNIFE)) {
                player.getInventory().removeStack(slot, 1);
                return;
            }
        }
    }

    private static boolean removeOneKnifeFromStack(ItemStack stack) {
        if (!stack.isOf(WatheItems.KNIFE)) {
            return false;
        }
        stack.decrement(1);
        return true;
    }
}
