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

/**
 * Transient per-phase identity and projectile ownership; nothing survives reload. ACTIVE uses the match round Wathe
 * initialises; INACTIVE, STARTING and STOPPING each get a presentation-only round on demand. Any phase change ends the
 * current round and discards its grenades.
 * 按阶段划分的临时标识和投掷物归属，不跨重载保留。ACTIVE 使用 Wathe 初始化的对局回合；INACTIVE、STARTING、STOPPING
 * 各自按需创建仅作表现的回合。任何阶段变化都会结束当前回合并移除其手雷。
 */
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

    /**
     * Shared lobby/match identity for Perfumer vials and Bomber drones; still null during STARTING/STOPPING.
     * 调香师药瓶与炸弹客无人机共用的大厅/对局标识；STARTING/STOPPING 期间仍为 null。
     */
    @Nullable
    public static UUID currentRoundId(ServerWorld world) {
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(world).getGameStatus();
        return status == GameWorldComponent.GameStatus.STARTING || status == GameWorldComponent.GameStatus.STOPPING
                ? null : throwRoundId(world);
    }

    /**
     * M67 throw identity in every phase. Outside ACTIVE a presentation-only round is minted on demand; ACTIVE never
     * mints one, so match initialization must still come from Wathe.
     * 各阶段的 M67 投掷标识。非 ACTIVE 阶段按需创建仅作表现的回合；ACTIVE 从不自行创建，对局仍必须由 Wathe 初始化。
     */
    @Nullable
    static UUID throwRoundId(ServerWorld world) {
        Round round = ROUNDS.get(world);
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(world).getGameStatus();
        if (round == null && status != GameWorldComponent.GameStatus.ACTIVE) {
            round = new Round(new M67RoundClock(world.getTime()), status);
            ROUNDS.put(world, round);
        }
        return round != null && round.phase == status ? round.clock.id() : null;
    }

    /** Ids of earlier phases never match: each phase's round has a fresh id. / 每个阶段的回合 id 都是新的，旧阶段 id 永不匹配。 */
    public static boolean isCurrentRound(ServerWorld world, UUID roundId) {
        return roundId != null && roundId.equals(throwRoundId(world));
    }

    public static void registerThrown(M67GrenadeEntity grenade) {
        if (!(grenade.getWorld() instanceof ServerWorld world)
                || !isCurrentRound(world, grenade.getRoundId())) {
            grenade.discard();
            return;
        }
        ROUNDS.get(world).grenades.add(grenade);
    }

    /**
     * Remaining match opening lock in ticks. M67 match throws read it through
     * {@link #openingRemaining(ServerPlayerEntity)}, Bomber drones through {@code DroneService.openingRemaining(player,
     * kind)}; an admin cooldown clear can lift either per player (and drone kind).
     * 剩余开局锁刻数。M67 对局投掷经由 {@link #openingRemaining(ServerPlayerEntity)} 读取；炸弹客无人机经由
     * DroneService.openingRemaining(player, kind) 读取。管理员清除冷却可按玩家（及无人机型号）解除。
     */
    public static int openingRemaining(ServerWorld world) {
        Round round = ROUNDS.get(world);
        return round == null || !round.inGame() || !active(world) ? 0 : round.clock.openingRemaining(world.getTime());
    }

    /**
     * Opening lock this player's M67 match throws still obey: the world lock, or 0 once an admin cleared their M67 this
     * round. Throwing and the per-tick cooldown sync read this.
     * 该玩家 M67 对局投掷仍受的开局锁：即世界开局锁；本回合管理员清除过其 M67 后为 0。投掷与每刻冷却同步读取此值。
     */
    public static int openingRemaining(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        int remaining = openingRemaining(world);
        return remaining > 0 && ROUNDS.get(world).openingReleased.contains(player.getUuid()) ? 0 : remaining;
    }

    /**
     * Admin path only: SparkFactionAPI {@code /sparkfactionapi:clearCooldown} just removed this player's M67 cooldown
     * (called through {@code SparkFactionAdminClear}). During the opening that remove alone does not stick (the lock is
     * re-synced every tick and throws read the round clock), so for the rest of this match round the player's M67
     * throws ignore the opening lock and count as normal match throws; other players stay locked. No-op outside an
     * ACTIVE match; role mechanics that remove cooldowns never call this.
     * 仅管理员路径：SparkFactionAPI /sparkfactionapi:clearCooldown 刚移除了该玩家的 M67 冷却（经由 SparkFactionAdminClear
     * 调用）。开局锁期间仅移除原版冷却无效（开局锁每刻重新同步，投掷读取回合时钟），因此本对局回合剩余时间内该玩家的 M67 投掷
     * 不再受开局锁约束，按正常对局投掷结算；其他玩家仍被锁定。对局未处于 ACTIVE 时不做任何事；移除冷却的职业机制不会调用此方法。
     */
    public static void onAdminCooldownCleared(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Round round = ROUNDS.get(world);
        if (round != null && round.inGame() && active(world)) {
            round.openingReleased.add(player.getUuid());
        }
    }

    private static void startRound(ServerWorld world) {
        endRound(world);
        // This callback still sees STARTING; capture the origin before ACTIVE is set. / 此回调仍为 STARTING，必须先记录起点。
        Round round = new Round(new M67RoundClock(world.getTime()), GameWorldComponent.GameStatus.ACTIVE);
        ROUNDS.put(world, round);
        for (ServerPlayerEntity player : world.getPlayers()) {
            lockOpening(world, round, player, M67Rules.OPENING_TICKS);
        }
    }

    /**
     * Roles are already assigned when the match initializes, so only participants get the opening lock; lobby players
     * keep presentation throws. Recipients are remembered so a cancelled match lifts only their lock.
     * 对局初始化时已分配角色，因此只有参赛玩家获得开局锁，大厅玩家仍可表现投掷。记录接收者，提前结束时只解除他们的锁。
     */
    private static void lockOpening(ServerWorld world, Round round, ServerPlayerEntity player, int ticks) {
        if (GameWorldComponent.KEY.get(world).hasAnyRole(player)) {
            round.openingLocked.add(player.getUuid());
            M67UseService.preserveCooldown(player, ticks);
        }
    }

    private static void endRound(ServerWorld world) {
        Round round = ROUNDS.remove(world);
        M67UseService.clearWorld(world);
        if (round != null) {
            round.grenades.forEach(M67GrenadeEntity::discard);
            round.grenades.clear();
            if (round.inGame() && round.clock.openingRemaining(world.getTime()) > 0) {
                // A canceled match's opening lock must not prevent subsequent lobby use; other players' cooldowns stay.
                // 提前结束对局时清除开局锁定，避免继续阻止大厅使用；其他玩家的冷却保持不变。
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (round.openingLocked.contains(player.getUuid())) {
                        player.getItemCooldownManager().remove(SparkStrengthItems.m67());
                    }
                }
            }
        }
    }

    private static void tick(ServerWorld world) {
        Round round = ROUNDS.get(world);
        if (round != null && !matchesStatus(world, round)) {
            // Phase changes invalidate lobby, STARTING/STOPPING presentation and match grenades alike.
            // 阶段变化会使大厅、STARTING/STOPPING 表现手雷与对局手雷一同失效。
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
        ServerWorld world = player.getServerWorld();
        int remaining = openingRemaining(player);
        if (remaining > 0) {
            // Native cooldown is sent even without an owned stack, so a first purchase shows it immediately; a player
            // an admin released is skipped, so the cleared cooldown stays cleared.
            // 即使尚未持有也发送原版冷却，首次购买即可显示，且不限制购买；管理员已解除的玩家跳过，清除的冷却不会被重新写回。
            lockOpening(world, ROUNDS.get(world), player, remaining);
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
        return GameWorldComponent.KEY.get(world).getGameStatus() == round.phase;
    }

    private static final class Round {
        private final M67RoundClock clock;
        /** The only phase this round is valid in; ACTIVE rounds come from Wathe only. / 本回合唯一有效的阶段；ACTIVE 回合只来自 Wathe。 */
        private final GameWorldComponent.GameStatus phase;
        private final Set<M67GrenadeEntity> grenades = new HashSet<>();
        private final Set<UUID> openingLocked = new HashSet<>();
        /** Players an admin cooldown clear released from the opening lock. / 管理员清除冷却后解除开局锁的玩家。 */
        private final Set<UUID> openingReleased = new HashSet<>();

        private Round(M67RoundClock clock, GameWorldComponent.GameStatus phase) {
            this.clock = clock;
            this.phase = phase;
        }

        private boolean inGame() {
            return phase == GameWorldComponent.GameStatus.ACTIVE;
        }
    }
}
