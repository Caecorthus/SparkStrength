package annina.sparkstrength.role.economy;

import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.economy.KillerTeamEconomyWorldComponent;
import annina.sparkstrength.network.economy.SyncKillerTeamEconomyS2CPacket;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * All earning, spending and visibility decisions are made on the server from current membership.
 * 收入、支付及可见性均由服务端按当前真实阵营决定。
 */
public final class KillerTeamEconomyService {
    private static final Map<ServerPlayNetworkHandler, Snapshot> VISIBLE_SNAPSHOTS = new HashMap<>();
    private static boolean registered;

    private KillerTeamEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                initializeRound(serverWorld, game);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                KillerTeamEconomyWorldComponent.KEY.get(serverWorld).clearRoundState();
                syncWorld(serverWorld);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(KillerTeamEconomyService::syncWorld);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            VISIBLE_SNAPSHOTS.remove(handler);
            syncPlayer(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VISIBLE_SNAPSHOTS.remove(handler));
        ServerWorldEvents.UNLOAD.register((server, world) -> VISIBLE_SNAPSHOTS.entrySet().removeIf(entry -> {
            if (entry.getValue().world() != world) {
                return false;
            }
            ServerPlayNetworking.send(entry.getKey().player, new SyncKillerTeamEconomyS2CPacket(false, 0));
            return true;
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VISIBLE_SNAPSHOTS.clear());
    }

    private static void initializeRound(ServerWorld world, GameWorldComponent game) {
        KillerTeamEconomyWorldComponent purse = KillerTeamEconomyWorldComponent.KEY.get(world);
        purse.clearRoundState();
        int openingMembers = 0;
        // Traits are assigned synchronously before this event, but Wathe has not set ACTIVE yet.
        // 天赋在此事件前已同步分配，但 Wathe 尚未进入 ACTIVE，开局计数不能套用存活运行门禁。
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player) && isGenuineMember(player, game)) {
                openingMembers++;
            }
        }
        if (SparkTraitsCompat.isTeamEconomyAvailable()) {
            purse.initializeRound(openingMembers);
        }
        syncWorld(world);
    }

    /** Public optional capability probe; does not promise that a solo round enables the purse.
     *  公开可选能力探针；不表示单人杀手局也开启钱包。 */
    public static boolean supportsTraitContributionBonus() {
        return true;
    }

    public static void recordIncome(PlayerEntity player, long actualIncome) {
        if (actualIncome <= 0 || !(player instanceof ServerPlayerEntity serverPlayer) || !canAccess(serverPlayer)) {
            return;
        }
        ServerWorld world = serverPlayer.getServerWorld();
        if (KillerTeamEconomyWorldComponent.KEY.get(world).recordIncome(
                actualIncome, SparkTraitsCompat.hasTeamFirst(serverPlayer))) {
            syncWorld(world);
        }
    }

    public static int availableForPurchase(PlayerEntity player, int personalBalance) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !canAccess(serverPlayer)) {
            return personalBalance;
        }
        return KillerTeamEconomyRules.availableBalance(
                personalBalance, KillerTeamEconomyWorldComponent.KEY.get(serverPlayer.getServerWorld()).getBalance()
        );
    }

    // Called only at the existing successful purchase debit; the personal wallet never contains team funds.
    // 仅在原有购买成功扣款点调用，个人钱包从不临时写入团队余额。
    public static int personalBalanceAfterPurchase(PlayerEntity player, int personalBalance, int price) {
        int shortfall = KillerTeamEconomyRules.sharedShortfall(personalBalance, price);
        if (shortfall == 0 || !(player instanceof ServerPlayerEntity serverPlayer) || !canAccess(serverPlayer)) {
            return personalBalance - price;
        }
        ServerWorld world = serverPlayer.getServerWorld();
        if (!KillerTeamEconomyWorldComponent.KEY.get(world).spend(shortfall)) {
            return personalBalance - price;
        }
        syncWorld(world);
        return 0;
    }

    private static boolean canAccess(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        return KillerTeamEconomyWorldComponent.KEY.get(world).isEnabled()
                && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && game.hasAnyRole(player) && !game.isPlayerDead(player.getUuid())
                && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && isGenuineMember(player, game);
    }

    private static boolean isGenuineMember(ServerPlayerEntity player, GameWorldComponent game) {
        Role role = game.getRole(player);
        return SparkTraitsCompat.isGenuineKillerTeamMember(player, role != null && role.canUseKiller());
    }

    private static void syncWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            syncPlayer(player);
        }
    }

    private static void syncPlayer(ServerPlayerEntity player) {
        ServerPlayNetworkHandler connection = player.networkHandler;
        Snapshot previous = VISIBLE_SNAPSHOTS.get(connection);
        if (!canAccess(player)) {
            if (previous != null) {
                ServerPlayNetworking.send(player, new SyncKillerTeamEconomyS2CPacket(false, 0));
                VISIBLE_SNAPSHOTS.remove(connection);
            }
            return;
        }
        ServerWorld world = player.getServerWorld();
        Snapshot current = new Snapshot(world, KillerTeamEconomyWorldComponent.KEY.get(world).getBalance());
        if (!current.equals(previous)) {
            // Connection-scoped snapshots reveal neither opening N nor any member roster.
            // 按连接定向同步，仅公开可见性与余额，不发送开局 N 或成员名单。
            ServerPlayNetworking.send(player, new SyncKillerTeamEconomyS2CPacket(true, current.balance()));
            VISIBLE_SNAPSHOTS.put(connection, current);
        }
    }

    private record Snapshot(ServerWorld world, int balance) {
    }
}
