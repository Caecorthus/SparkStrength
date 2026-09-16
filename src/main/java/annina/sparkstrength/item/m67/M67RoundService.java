package annina.sparkstrength.item.m67;

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

/** Transient round identity and projectile ownership; nothing survives reload. / 临时回合标识和投掷物归属，不跨重载保留。 */
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
        return round != null && active(world) ? round.clock.id() : null;
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
        return round == null || !active(world) ? 0 : round.clock.openingRemaining(world.getTime());
    }

    private static void startRound(ServerWorld world) {
        endRound(world);
        // This callback still sees STARTING; capture the origin before ACTIVE is set. / 此回调仍为 STARTING，必须先记录起点。
        ROUNDS.put(world, new Round(new M67RoundClock(world.getTime())));
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
        }
    }

    private static void tick(ServerWorld world) {
        Round round = ROUNDS.get(world);
        if (round != null && !active(world)) {
            // isRunning includes STOPPING, where no M67 may remain armed. / isRunning 包含 STOPPING，不能据此保留手雷。
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

    private static final class Round {
        private final M67RoundClock clock;
        private final Set<M67GrenadeEntity> grenades = new HashSet<>();

        private Round(M67RoundClock clock) {
            this.clock = clock;
        }
    }
}
