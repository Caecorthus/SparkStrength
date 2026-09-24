package annina.sparkstrength.item.m67;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.entity.M67GrenadeEntity;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transient match/lobby identity and projectile ownership; nothing survives reload. / 临时对局或大厅标识和投掷物归属，不跨重载保留。 */
public final class M67RoundService {
    private static final Map<ServerWorld, Round> ROUNDS = new IdentityHashMap<>();
    private static boolean initialized;

    private M67RoundService() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                startRound(serverWorld);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                endRound(serverWorld);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // Reset also runs on death: keep already thrown grenades. / 死亡也会重置玩家，不移除已投出的手雷。
                M67UseService.forgetPlayer(serverPlayer);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(M67RoundService::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncOpening(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> disconnect(handler.player));
        ServerWorldEvents.UNLOAD.register((server, world) -> endRound(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerWorld world : ROUNDS.keySet().toArray(ServerWorld[]::new)) {
                endRound(world);
            }
            M67UseService.clearAll();
        });
    }

    @Nullable
    public static UUID currentRoundId(ServerWorld world) {
        Round round = ROUNDS.get(world);
        // Lobby grenades get a separate lifetime; match initialization must still come from Wathe.
        // 大厅手雷使用独立生命周期；正式对局仍必须由 Wathe 初始化。
        if (round == null && GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.INACTIVE) {
            round = new Round(new M67RoundClock(world.getTime()), false);
            ROUNDS.put(world, round);
        }
        return round != null && matchesStatus(world, round) ? round.clock.id() : null;
    }

    public static boolean isCurrentRound(ServerWorld world, UUID roundId) {
        return roundId != null && roundId.equals(currentRoundId(world));
    }

    public static void registerThrown(M67GrenadeEntity grenade) {
        if (!(grenade.getWorld() instanceof ServerWorld world)
                || !isCurrentRound(world, grenade.getRoundId())) {
            grenade.discard();
            return;
        }
        ROUNDS.get(world).grenades.add(grenade);
    }

    static int openingRemaining(ServerWorld world) {
        Round round = ROUNDS.get(world);
        return round == null || !round.inGame || !active(world) ? 0 : round.clock.openingRemaining(world.getTime());
    }

    private static void startRound(ServerWorld world) {
        endRound(world);
        // This callback still sees STARTING; capture the origin before ACTIVE is set. / 此回调仍为 STARTING，必须先记录起点。
        ROUNDS.put(world, new Round(new M67RoundClock(world.getTime()), true));
        for (ServerPlayerEntity player : world.getPlayers()) {
            M67UseService.preserveCooldown(player, M67Rules.OPENING_TICKS);
        }
    }

    private static void endRound(ServerWorld world) {
        Round round = ROUNDS.remove(world);
        M67UseService.clearWorld(world);
        if (round != null) {
            round.grenades.forEach(M67GrenadeEntity::discard);
            round.grenades.clear();
            if (round.inGame && round.clock.openingRemaining(world.getTime()) > 0) {
                // A canceled match's opening lock must not prevent subsequent lobby use.
                // 提前结束对局时清除开局锁定，避免继续阻止大厅使用。
                for (ServerPlayerEntity player : world.getPlayers()) {
                    player.getItemCooldownManager().remove(SparkStrengthItems.m67());
                }
            }
        }
    }

    private static void tick(ServerWorld world) {
        Round round = ROUNDS.get(world);
        if (round != null && !matchesStatus(world, round)) {
            // Phase changes invalidate both lobby and match grenades, including STARTING/STOPPING.
            // 阶段变化会使大厅和对局手雷失效，包括 STARTING/STOPPING 过渡阶段。
            endRound(world);
            return;
        }
        if (round != null) {
            round.grenades.removeIf(M67GrenadeEntity::isRemoved);
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            syncOpening(player);
            M67UseService.tick(player);
        }
    }

    private static void syncOpening(ServerPlayerEntity player) {
        int remaining = openingRemaining(player.getServerWorld());
        if (remaining > 0) {
            // Native cooldown is sent even without an owned stack, so a first purchase shows it immediately.
            // 即使尚未持有也发送原版冷却，首次购买即可显示，且不限制购买。
            M67UseService.preserveCooldown(player, remaining);
        }
    }

    private static void disconnect(ServerPlayerEntity player) {
        M67UseService.forgetPlayer(player);
        for (Round round : ROUNDS.values()) {
            round.grenades.removeIf(grenade -> {
                if (player.getUuid().equals(grenade.getThrowerUuid())) {
                    grenade.discard();
                    return true;
                }
                return grenade.isRemoved();
            });
        }
    }

    private static boolean active(ServerWorld world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    private static boolean matchesStatus(ServerWorld world, Round round) {
        return GameWorldComponent.KEY.get(world).getGameStatus()
                == (round.inGame ? GameWorldComponent.GameStatus.ACTIVE : GameWorldComponent.GameStatus.INACTIVE);
    }

    private static final class Round {
        private final M67RoundClock clock;
        private final boolean inGame;
        private final Set<M67GrenadeEntity> grenades = new HashSet<>();

        private Round(M67RoundClock clock, boolean inGame) {
            this.clock = clock;
            this.inGame = inGame;
        }
    }
}
