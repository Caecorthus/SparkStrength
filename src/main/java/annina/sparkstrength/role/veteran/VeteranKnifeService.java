package annina.sparkstrength.role.veteran;

import annina.sparkstrength.component.veteran.VeteranKnifeComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerVeteranComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/**
 * 老兵匕首的服务端规则。
 *
 * <p>这个服务由 mixin 在 {@link KnifeStabPayload.Receiver#receive} 开头调用。
 * 如果发包者是老兵，就由这里完整接管刺杀并取消 Wathe 原逻辑，从而同时做到：
 * 无刺杀音效、刺杀后无刀 CD、支持多把商店匕首累计次数，以及避免原版 0/1/2 次组件吞掉新买的刀。</p>
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
     * @return true 表示本次 payload 已由老兵逻辑处理，原 Wathe 刀人逻辑必须取消。
     */
    public static boolean handleKnifeStab(KnifeStabPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        if (!VeteranRules.isVeteran(role)) {
            return false;
        }

        // 只要是老兵的刀包，就不再放回原逻辑；无效目标直接整刀无效，避免原版播放声音/设置 CD。
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
        player.swingHand(usedHand);
        // 老兵加强要求刺杀后没有刀 CD；这里显式清掉，防止其他逻辑在同 tick 写入冷却。
        player.getItemCooldownManager().remove(WatheItems.KNIFE);
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
