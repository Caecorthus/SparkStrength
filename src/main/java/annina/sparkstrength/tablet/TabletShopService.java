package annina.sparkstrength.tablet;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.role.attendant.AttendantRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.EnumSet;
import java.util.List;

/**
 * Grants the SparkStrength tablet; it is never sold and takes no shop slot (the class name is historical). Every
 * tablet-eligible player ({@link TabletShopRules#isTabletEligible}) gets one free at round start, and a reconciliation
 * pass covers players who become eligible mid-round. At most one grant per player per round.
 * 发放 SparkStrength 平板；平板从不出售，也不占用商店栏位（类名沿用旧称）。每名符合条件的玩家开局免费获得一台，
 * 局中才获得资格的玩家由对账轮次补发。每名玩家每局最多发放一次。
 */
public final class TabletShopService {
    private static boolean registered;

    private TabletShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Corrupt Cop joins SparkFactionAPI PoliceRoles, i.e. the police network (TabletShopRules#isPoliceNetworkRole).
        // 黑警注册进 SparkFactionAPI PoliceRoles，即加入义警平板网络。
        SparkFactionCompat.registerCorruptCopPoliceRole();
    }

    /**
     * Round-start grant from {@code GameEvents.ON_FINISH_INITIALIZE}: runs after every RoleAssigned of Wathe's
     * initializeGame (including SparkTraits Conscience compensation, which may rewrite a role), so it sees final roles
     * and traits, before the round becomes ACTIVE. Must run after the tablet round state is cleared.
     * 开局发放（ON_FINISH_INITIALIZE）：在 Wathe initializeGame 的所有 RoleAssigned（含可能改写身份的 SparkTraits
     * 良心补偿）之后、对局变为 ACTIVE 之前执行，因此看到的是最终身份与天赋。必须在清理平板本局状态之后调用。
     */
    public static void grantStarterTablets(ServerWorld world, GameWorldComponent game) {
        TabletWorldComponent state = TabletWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Status is still STARTING here, so isPlayerPlayingAndAlive would be false for everyone.
            // 此时状态仍为 STARTING，isPlayerPlayingAndAlive 对所有人都为 false。
            if (game.hasAnyRole(player)) {
                settle(player, state, false);
            }
        }
    }

    /**
     * Mid-round reconciliation (END_WORLD_TICK, every {@link TabletShopRules#GRANT_RECONCILE_INTERVAL_TICKS} while
     * ACTIVE) instead of a RoleAssigned-time grant: SparkWitch Grand Witch recruitment fires RoleAssigned and then
     * rewrites every inventory slot from a snapshot (wiping anything given inside the event), and Wraith promotion uses
     * addRole without firing RoleAssigned at all. Each alive, eligible, not-yet-settled player gets a tablet only if
     * they hold none, and is settled either way; a missing tablet never re-triggers a grant, because the Black Raven
     * disguise stashes inventory items and a re-grant would duplicate when the stash returns.
     * 局中对账（ACTIVE 期间于 END_WORLD_TICK 每隔固定 tick 执行），取代 RoleAssigned 时发放：SparkWitch 大魔女招募会先触发
     * RoleAssigned、再按快照重写所有物品栏槽位（事件内发放的物品会被抹掉）；冤魂晋升使用 addRole，根本不触发 RoleAssigned。
     * 每名存活、有资格且尚未结算的玩家只在身上没有平板时获得一台，无论是否发放都记为已结算；平板缺失绝不会再次触发发放，
     * 因为黑羽鸦伪装会暂存物品，重复发放会在暂存归还时产生第二台。
     */
    public static void tick(ServerWorld world) {
        if (world.getTime() % TabletShopRules.GRANT_RECONCILE_INTERVAL_TICKS != 0) {
            return;
        }
        if (GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        TabletWorldComponent state = TabletWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (GameFunctions.isPlayerPlayingAndAlive(player)) {
                settle(player, state, true);
            }
        }
    }

    private static void settle(ServerPlayerEntity player, TabletWorldComponent state, boolean syncAfterGrant) {
        if (state.isTabletGrantSettled(player.getUuid())) {
            return;
        }
        // Same identity facts as channel membership (TabletChannelResolver.identityChannels), plus the real Attendant.
        // 与频道成员资格相同的身份事实（identityChannels），再加真实乘务员身份。
        TabletChannelRules.Facts facts = TabletChannelResolver.facts(player);
        EnumSet<TabletChannel> channels = TabletChannelRules.allowed(facts);
        boolean realAttendant = isRealAttendant(player);
        if (!TabletShopRules.isTabletEligible(facts.hasRole(), !channels.isEmpty(), realAttendant)) {
            // Not settled: a later role/trait/faction change can still make this player eligible this round.
            // 不记为已结算：本局之后的身份/天赋/阵营变化仍可能让该玩家获得资格。
            return;
        }
        if (!hasTabletAnywhere(player)) {
            if (!player.giveItemStack(new ItemStack(SparkStrengthItems.tablet()))) {
                // Full inventory: stay unsettled so the next reconciliation pass retries.
                // 物品栏已满：保持未结算，由下一轮对账重试。
                return;
            }
            List<String> keys = TabletShopRules.grantMessageKeys(channels, facts.undercover(), realAttendant);
            for (String key : keys) {
                player.sendMessage(Text.translatable(key), false);
            }
            if (syncAfterGrant) {
                TabletStateService.syncTo(player);
            }
        }
        state.markTabletGrantSettled(player.getUuid());
    }

    private static boolean isRealAttendant(PlayerEntity player) {
        // Real round role only: a Coroner's Attendant disguise is lent a temporary tablet by CoronerService instead.
        // 只看真实局内身份：验尸官的乘务员伪装改由 CoronerService 借出临时平板。
        return AttendantRules.isAttendant(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    private static boolean hasTabletAnywhere(ServerPlayerEntity player) {
        // Every slot plus the cursor stack (the inventory screen may be open), so a grant never duplicates a tablet.
        // 扫描所有槽位以及鼠标上的物品（物品栏界面可能打开），保证发放绝不重复。
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(SparkStrengthItems.tablet())) {
                return true;
            }
        }
        return player.currentScreenHandler != null
                && player.currentScreenHandler.getCursorStack().isOf(SparkStrengthItems.tablet());
    }
}
