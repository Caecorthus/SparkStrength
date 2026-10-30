package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.attendant.DoorLogService;
import annina.sparkstrength.role.bomber.drone.DroneService;
import annina.sparkstrength.role.partyanimal.nav.NavBlocks;
import annina.sparkstrength.role.partyanimal.nav.TrainNavGrid;
import annina.sparkstrength.role.partyanimal.nav.TrainPathfinder;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Server-side behaviour of one Partygoer: hunt the locked prey, carry it to the destination room, eat it; rage once at
 * whoever kills the prey in its hands; give up when the prey is lost for good.
 * 单只派对客的服务端行为：追锁定的猎物、扛到终点房间、吃掉；手中猎物被别人杀死时对击杀者狂怒一次；猎物彻底丢失时放弃。
 *
 * <p>Movement is kinematic along cells of its own {@link TrainNavGrid}: the path is replanned every half second (or when
 * the target changes cell; after a search that could not reach it, only every two seconds) and walked at the phase's
 * speed; closed doors two to three cells ahead are pushed open. Ladders are climbed at a player's ladder speed.
 * 移动是沿自有 TrainNavGrid 格子的运动学位移：每半秒（或目标换格时；到不了目标的搜索之后则每两秒）重新规划，按当前阶段速度行走；
 * 前方两三格内关着的门会被推开。梯子按玩家的爬梯速度爬。</p>
 */
public final class PartygoerBrain {
    private static final int REPLAN_TICKS = 10;
    private static final long STALL_TICKS = 100;
    /** After a search that can't reach its goal, wait longer before searching again. / 搜索到不了目标后，等久一点再搜。 */
    private static final int REPLAN_FAILED_TICKS = 40;
    /** Charge straight at a target only this close (never through walls from afar). / 只在这么近时直线冲向目标（远处绝不穿墙）。 */
    private static final double STRAIGHT_RANGE = 2.5;
    private static final double STRAIGHT_VERTICAL = 1.5;
    /** Carrying with no route at all for this long: eat where it stands. / 扛人时完全无路达到这么久：就地开吃。 */
    private static final int CARRY_STUCK_TICKS = 60;
    private static final int DOOR_LOOKAHEAD = 3;
    /** Keep a door we walk through open at least this long. / 我们正在穿过的门至少保持打开这么久。 */
    private static final int DOOR_HOLD_TICKS = 40;
    private static final double FOOTSTEP_EVERY = 0.75;
    private static final float TURN_DEGREES_PER_TICK = 30.0F;
    private static final int STATUS_SYNC_TICKS = 10;

    private final PartygoerEntity body;
    private final ServerWorld world;
    private final UUID ownerUuid;
    private final UUID roundId;
    private final TrainNavGrid grid;
    private final PartyRoomIndex.PartyRoom destination;

    private PartygoerState state = PartygoerState.HUNT;
    private @Nullable UUID preyUuid;
    private @Nullable UUID rageTargetUuid;
    private @Nullable UUID swallowerUuid;
    private boolean rageUsed;
    private boolean finished;
    /** Set while our own bite runs killPlayer, so KillPlayer.AFTER ignores it. / 我们自己咬人调用 killPlayer 期间置位，让 KillPlayer.AFTER 忽略。 */
    private boolean biting;
    private int stateTicks;
    private int eatTotal;
    private int eatElapsed;

    private List<Long> path = List.of();
    private int pathIndex;
    private long pathGoal = Long.MIN_VALUE;
    private int replanIn;
    /** The last search ran but could not reach its goal. / 上次搜索执行了但到不了目标。 */
    private boolean searchFailed;
    private int carryStuckTicks;
    private long lastTickTime;
    private double footstepDistance;

    public PartygoerBrain(PartygoerEntity body, ServerWorld world, UUID ownerUuid, UUID roundId, TrainNavGrid grid,
                          PartyRoomIndex.PartyRoom destination, UUID preyUuid) {
        this.body = body;
        this.world = world;
        this.ownerUuid = ownerUuid;
        this.roundId = roundId;
        this.grid = grid;
        this.destination = destination;
        this.preyUuid = preyUuid;
        this.lastTickTime = world.getTime();
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public PartygoerState state() {
        return state;
    }

    public @Nullable UUID preyUuid() {
        return preyUuid;
    }

    public @Nullable UUID swallowerUuid() {
        return swallowerUuid;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Its round is still the running one. / 其所属对局仍在进行。 */
    boolean isCurrentRound() {
        return roundId.equals(DroneService.currentRoundId(world));
    }

    /** Not ticked for 5 s while still alive: its chunk left the simulation distance. / 存活却 5 秒未 tick：所在区块离开了模拟距离。 */
    boolean isStalled() {
        return !finished && world.getTime() - lastTickTime > STALL_TICKS;
    }

    /** Alive in the world and not inside a Taotie: the only time weapons can end it. / 在场且不在饕餮肚里：只有此时武器能打死它。 */
    public boolean isKillable() {
        return !finished && state != PartygoerState.SWALLOWED && !body.isRemoved();
    }

    // ---- Tick / 每刻 ----

    public void tick() {
        if (finished) {
            return;
        }
        lastTickTime = world.getTime();
        if (!roundId.equals(DroneService.currentRoundId(world))) {
            PartygoerService.finish(this, PartygoerService.Ending.QUIET);
            return;
        }
        stateTicks++;
        switch (state) {
            case HUNT -> tickHunt();
            case CARRY -> tickCarry();
            case EAT, RAGE_EAT -> tickEat();
            case RAGE_STUN -> tickRageStun();
            case RAGE_CHASE -> tickRageChase();
            case SWALLOWED -> tickSwallowed();
        }
        if (!finished && (stateTicks % STATUS_SYNC_TICKS == 0 || stateTicks == 1)) {
            PartygoerService.publishStatus(this);
        }
    }

    private void tickHunt() {
        ServerPlayerEntity prey = player(preyUuid);
        if (prey != null && prey.getVehicle() == body) {
            // Already in our arms (counts as "carried", which isValidPrey rejects for other Partygoers).
            // 已在我们手中（isValidPrey 会把“被扛着”当成其他派对客的猎物而拒绝）。
            grab(prey, PartygoerState.CARRY);
            return;
        }
        if (prey == null || !PartygoerTargeting.isValidPrey(prey, ownerUuid)) {
            long here = grid.snap(body.getX(), body.getY(), body.getZ());
            prey = here == Long.MIN_VALUE ? null : PartygoerTargeting.nearestPrey(world, grid, here, ownerUuid);
            if (prey == null) {
                PartygoerService.finish(this, PartygoerService.Ending.GAVE_UP);
                return;
            }
            preyUuid = prey.getUuid();
            resetPath();
        }
        if (stateTicks > PartyAnimalRules.HUNT_TIMEOUT_TICKS) {
            PartygoerService.finish(this, PartygoerService.Ending.GAVE_UP);
            return;
        }
        if (inGrabReach(prey)) {
            grab(prey, PartygoerState.CARRY);
            return;
        }
        chase(prey, PartyAnimalRules.HUNT_SPEED_LEVEL);
    }

    private void tickCarry() {
        ServerPlayerEntity prey = player(preyUuid);
        if (prey == null || prey.getVehicle() != body) {
            // Escaped the arms alive (swallowed by a Taotie, disconnected...): hunt again. Deaths come via onPlayerKilled.
            // 活着离开了手臂（被饕餮吞、掉线……）：重新开始追。死亡由 onPlayerKilled 处理。
            preyUuid = null;
            enter(PartygoerState.HUNT);
            return;
        }
        // The end of a partial path is the reachable spot nearest the room, which is where it eats.
        // 部分路径的终点是离房间最近的可达位置，就在那里吃。
        if (walkTo(destination.anchorCell(), PartyAnimalRules.CARRY_SPEED_LEVEL)) {
            startEating(prey, PartygoerState.EAT);
        } else if (path.isEmpty() && ++carryStuckTicks >= CARRY_STUCK_TICKS) {
            startEating(prey, PartygoerState.EAT);
        }
    }

    private void tickEat() {
        ServerPlayerEntity prey = player(state == PartygoerState.RAGE_EAT ? rageTargetUuid : preyUuid);
        if (prey == null || prey.getVehicle() != body) {
            if (state == PartygoerState.RAGE_EAT) {
                PartygoerService.finish(this, PartygoerService.Ending.GAVE_UP);
            } else {
                preyUuid = null;
                enter(PartygoerState.HUNT);
            }
            return;
        }
        eatElapsed++;
        if (eatElapsed % PartyAnimalRules.EAT_SOUND_INTERVAL == 0) {
            eatEffects(prey);
        }
        if (eatElapsed % PartyAnimalRules.EAT_TICKS_PER_LAYER != 0) {
            return;
        }
        boolean finalBite = eatElapsed >= eatTotal;
        // A shield bite has no killer: credited to the summoner, SparkTraits' Second Strike would retry it at once and
        // skip that shield's 5 s. Only the final, forced bite is the summoner's kill.
        // 啃盾的一口不记击杀者：若记给召唤者，SparkTraits 的二次打击会立刻重试，跳过这层盾的 5 秒。只有最后强制的一口才算召唤者击杀。
        biting = true;
        try {
            GameFunctions.killPlayer(prey, false, finalBite ? PartygoerService.killCredit(this, prey) : null,
                    PartyAnimalRules.DEATH_REASON_EATEN, finalBite);
        } finally {
            biting = false;
        }
        if (finalBite || !GameFunctions.isPlayerPlayingAndAlive(prey)) {
            PartygoerService.finish(this, PartygoerService.Ending.ATE);
        }
    }

    private void tickRageStun() {
        ServerPlayerEntity target = player(rageTargetUuid);
        if (target != null) {
            face(target.getPos().subtract(body.getPos()));
        }
        if (stateTicks >= PartyAnimalRules.RAGE_STUN_TICKS) {
            enter(PartygoerState.RAGE_CHASE);
        }
    }

    private void tickRageChase() {
        ServerPlayerEntity target = player(rageTargetUuid);
        if (!PartygoerTargeting.isReachable(target) || stateTicks > PartyAnimalRules.RAGE_CHASE_TIMEOUT_TICKS) {
            PartygoerService.finish(this, PartygoerService.Ending.GAVE_UP);
            return;
        }
        if (inGrabReach(target)) {
            if (grab(target, PartygoerState.RAGE_EAT)) {
                startEating(target, PartygoerState.RAGE_EAT);
            }
            return;
        }
        chase(target, PartyAnimalRules.RAGE_SPEED_LEVEL);
    }

    private void tickSwallowed() {
        ServerPlayerEntity taotie = player(swallowerUuid);
        if (taotie == null || !GameFunctions.isPlayerPlayingAndAlive(taotie)) {
            // The Taotie left without the release hook firing (disconnect): let it out where it is.
            // 饕餮未经释放钩子就离开（掉线）：就地放出。
            releasedAt(body.getPos());
            return;
        }
        body.setPosition(taotie.getX(), taotie.getY(), taotie.getZ());
    }

    // ---- Events from PartygoerService / 来自 PartygoerService 的事件 ----

    /**
     * Someone died (KillPlayer.AFTER). Prey killed in its hands by someone alive and not on the summoner's team triggers
     * the one rage; otherwise it gives up. The rage target dying any other way also ends it. A prey that dies before
     * being caught is just replaced.
     * 有人死亡（KillPlayer.AFTER）。手中猎物被活着且不属于召唤者一方的人杀死时触发唯一一次狂怒，否则放弃。狂怒目标以其他方式
     * 死亡也会结束。尚未抓到的猎物死亡则只是换目标。
     */
    public void onPlayerKilled(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer) {
        if (finished || biting) {
            return;
        }
        UUID victimUuid = victim.getUuid();
        if (victimUuid.equals(preyUuid)) {
            if (state.preyInHand()) {
                boolean killerAlive = PartygoerTargeting.isReachable(killer);
                if (PartyAnimalRules.triggersRage(true, rageUsed, killer != null, killerAlive,
                        killer != null && killer.getUuid().equals(victimUuid),
                        killer != null && PartygoerTargeting.isSummonerTeam(killer, ownerUuid, player(ownerUuid)))) {
                    startRage(killer);
                } else {
                    PartygoerService.finish(this, PartygoerService.Ending.GAVE_UP);
                }
            } else if (state == PartygoerState.HUNT) {
                preyUuid = null;
                resetPath();
            }
            return;
        }
        if (victimUuid.equals(rageTargetUuid) && state.raging()) {
            PartygoerService.finish(this, PartygoerService.Ending.GAVE_UP);
        }
    }

    /** Swallowed by a Taotie: drop whoever it holds and ride inside, hidden. / 被饕餮吞下：放下手中的人，隐身待在肚子里。 */
    public void swallowedBy(ServerPlayerEntity taotie) {
        PartygoerService.releasePassenger(this);
        swallowerUuid = taotie.getUuid();
        body.setInvisible(true);
        enter(PartygoerState.SWALLOWED);
    }

    /** Let out of the Taotie at {@code pos}: back on the hunt. / 在 pos 处从饕餮肚里出来：重新开始追。 */
    public void releasedAt(Vec3d pos) {
        if (finished || state != PartygoerState.SWALLOWED) {
            return;
        }
        swallowerUuid = null;
        body.setInvisible(false);
        long cell = grid.snap(pos.x, pos.y, pos.z);
        Vec3d at = cell == Long.MIN_VALUE ? pos : PartyRoomIndex.cellCentre(grid, cell);
        body.setPosition(at.x, at.y, at.z);
        preyUuid = null;
        rageTargetUuid = null;
        enter(PartygoerState.HUNT);
    }

    void markFinished() {
        finished = true;
    }

    PartygoerEntity body() {
        return body;
    }

    ServerWorld world() {
        return world;
    }

    TrainNavGrid grid() {
        return grid;
    }

    PartyRoomIndex.PartyRoom destination() {
        return destination;
    }

    /** Whom the HUD should name: the prey, or the rage target. / HUD 显示的对象：猎物或狂怒目标。 */
    @Nullable UUID focusUuid() {
        return state.raging() ? rageTargetUuid : preyUuid;
    }

    /** Seconds left in the current phase for the HUD, or 0. / HUD 显示的当前阶段剩余秒数，或 0。 */
    int phaseSecondsLeft() {
        return switch (state) {
            case HUNT -> Math.ceilDiv(Math.max(0, PartyAnimalRules.HUNT_TIMEOUT_TICKS - stateTicks), 20);
            case RAGE_CHASE -> Math.ceilDiv(Math.max(0, PartyAnimalRules.RAGE_CHASE_TIMEOUT_TICKS - stateTicks), 20);
            case EAT, RAGE_EAT -> Math.ceilDiv(Math.max(0, eatTotal - eatElapsed), 20);
            default -> 0;
        };
    }

    // ---- Phases / 阶段切换 ----

    private void enter(PartygoerState next) {
        state = next;
        stateTicks = 0;
        carryStuckTicks = 0;
        body.setPartyState(next);
        resetPath();
        PartygoerService.publishStatus(this);
    }

    private boolean grab(ServerPlayerEntity target, PartygoerState next) {
        if (target.getVehicle() != body) {
            if (target.isSleeping()) {
                target.wakeUp();
            }
            // Drop a use in progress without finishing it (a charged throwing axe must not fly from its arms).
            // 中断进行中的使用且不触发结束效果（蓄力的飞斧不能从它怀里飞出去）。
            target.clearActiveItem();
            // Off any couch first: a seated player sinks into the floor. / 先离开座位：坐着的玩家位置在地板里。
            target.stopRiding();
            if (!target.startRiding(body, true)) {
                return false;
            }
            long here = grid.snap(body.getX(), body.getY(), body.getZ());
            long ladder = here == Long.MIN_VALUE ? Long.MIN_VALUE : ladderAt(here);
            if (ladder != Long.MIN_VALUE) {
                faceLadder(ladder);
            }
        }
        PartygoerService.playClip(this, SparkStrengthSounds.PARTYGOER_GRAB);
        PartygoerService.recordGrab(this, target);
        if (next == PartygoerState.CARRY) {
            enter(PartygoerState.CARRY);
        }
        return true;
    }

    private void startEating(ServerPlayerEntity target, PartygoerState eatState) {
        eatTotal = PartyAnimalRules.eatTicks(PartygoerShieldLayers.count(target));
        eatElapsed = 0;
        enter(eatState);
    }

    private void startRage(ServerPlayerEntity killer) {
        rageUsed = true;
        rageTargetUuid = killer.getUuid();
        preyUuid = null;
        PartygoerService.releasePassenger(this);
        PartygoerService.playClip(this, SparkStrengthSounds.PARTYGOER_RAGE);
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, body.getX(), body.getEyeY() + 0.4, body.getZ(),
                6, 0.35, 0.2, 0.35, 0.0);
        PartygoerService.recordRage(this, killer);
        enter(PartygoerState.RAGE_STUN);
    }

    // ---- Movement / 移动 ----

    private void chase(ServerPlayerEntity target, int speedLevel) {
        long goal = grid.snap(target.getX(), target.getY(), target.getZ());
        boolean pathDone = goal != Long.MIN_VALUE && walkTo(goal, speedLevel);
        if ((pathDone || goal == Long.MIN_VALUE) && closeEnough(target.getPos())) {
            // Last stretch, or the target stands where the grid can't follow (a counter): straight at it, only when
            // already close. / 最后一段，或目标站在网格跟不到的地方（柜台上）：仅在已经很近时直线冲过去。
            stepToward(target.getPos(), PartyAnimalRules.blocksPerTick(speedLevel));
        }
    }

    private boolean closeEnough(Vec3d target) {
        double dx = target.x - body.getX();
        double dz = target.z - body.getZ();
        return dx * dx + dz * dz <= STRAIGHT_RANGE * STRAIGHT_RANGE
                && Math.abs(target.y - body.getY()) <= STRAIGHT_VERTICAL;
    }

    /**
     * Walks the planned path towards {@code goal}; true once at its end (the goal, or the reachable cell nearest it).
     * False while walking, and while there is no route at all (empty path: it stands still). Ladder stretches go at
     * {@link PartyAnimalRules#CLIMB_BLOCKS_PER_TICK}.
     * 沿规划路径走向 goal；走到路径终点（目标或离目标最近的可达格）时返回 true。行走途中以及完全无路（空路径：原地不动）时返回 false。
     * 梯子路段按 CLIMB_BLOCKS_PER_TICK 的速度。
     */
    private boolean walkTo(long goal, int speedLevel) {
        // A failed search waits out its back-off even when the goal changes cell: it would explore the same area again.
        // 失败的搜索即使目标换格也要等完退避：再搜一遍探索的还是同一片区域。
        if (--replanIn <= 0 || (goal != pathGoal && !searchFailed)) {
            replan(goal);
        }
        if (path.isEmpty()) {
            return false;
        }
        openDoorsAhead();
        double walkSpeed = PartyAnimalRules.blocksPerTick(speedLevel);
        // Share of this tick still to spend. / 本刻剩余可用的时间比例。
        double time = 1.0;
        while (time > 1.0E-6 && pathIndex < path.size()) {
            long from = path.get(pathIndex - 1);
            long to = path.get(pathIndex);
            // Straight up or down is climbing, with the rung the lower of the two cells. / 直上直下即为爬梯，横档是两格中较低者。
            boolean climb = TrainNavGrid.x(from) == TrainNavGrid.x(to) && TrainNavGrid.z(from) == TrainNavGrid.z(to);
            long rung = !climb ? Long.MIN_VALUE : TrainNavGrid.y(from) < TrainNavGrid.y(to) ? from : to;
            double speed = climb ? PartyAnimalRules.CLIMB_BLOCKS_PER_TICK : walkSpeed;
            Vec3d target = PartyRoomIndex.cellCentre(grid, to);
            Vec3d delta = target.subtract(body.getPos());
            double distance = delta.length();
            double reach = time * speed;
            if (distance <= reach) {
                moveTo(target, delta, rung);
                time -= distance / speed;
                pathIndex++;
            } else {
                Vec3d step = delta.multiply(reach / distance);
                moveTo(body.getPos().add(step), step, rung);
                time = 0.0;
            }
            // Holding someone, it also keeps its back to the ladder while stepping on or off it.
            // 抱着人时，踏上或离开梯子的这一步也背对梯子。
            long ladder = rung;
            if (!climb && body.hasPassengers()) {
                ladder = ladderAt(to);
                if (ladder == Long.MIN_VALUE) {
                    ladder = ladderAt(from);
                }
            }
            if (ladder != Long.MIN_VALUE) {
                faceLadder(ladder);
            }
        }
        return pathIndex >= path.size();
    }

    private void replan(long goal) {
        pathGoal = goal;
        long start = grid.snap(body.getX(), body.getY(), body.getZ());
        path = TrainPathfinder.path(grid, start, goal, TrainPathfinder.DEFAULT_BUDGET);
        replanIn = !path.isEmpty() && path.getLast() == goal ? REPLAN_TICKS : REPLAN_FAILED_TICKS;
        // An empty path means start or goal is off the grid: no search ran, so a new goal may try again at once.
        // 空路径表示起点或终点不在网格上：并未搜索，换了目标可以立即再试。
        searchFailed = !path.isEmpty() && path.getLast() != goal;
        // Skip the cell we stand in. / 跳过脚下所在格。
        pathIndex = path.size() > 1 ? 1 : path.size();
    }

    private void resetPath() {
        path = List.of();
        pathIndex = 0;
        pathGoal = Long.MIN_VALUE;
        replanIn = 0;
        searchFailed = false;
    }

    private void stepToward(Vec3d target, double speed) {
        Vec3d delta = target.subtract(body.getPos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.05) {
            return;
        }
        double travel = Math.min(speed, Math.max(0.0, horizontal - 0.4));
        Vec3d step = new Vec3d(delta.x / horizontal * travel, 0.0, delta.z / horizontal * travel);
        moveTo(body.getPos().add(step), delta);
    }

    private void moveTo(Vec3d pos, Vec3d heading) {
        moveTo(pos, heading, Long.MIN_VALUE);
    }

    /** {@code rung}: the ladder cell it is climbing on, for the ladder's own step sound. / 正在爬的梯子格，用梯子自己的脚步声。 */
    private void moveTo(Vec3d pos, Vec3d heading, long rung) {
        double moved = pos.distanceTo(body.getPos());
        body.setPosition(pos.x, pos.y, pos.z);
        face(heading);
        footstepDistance += moved;
        if (footstepDistance >= FOOTSTEP_EVERY) {
            footstepDistance = 0.0;
            SoundEvent step = rung == Long.MIN_VALUE ? SoundEvents.ENTITY_ZOMBIE_STEP
                    : world.getBlockState(cellPos(rung)).getSoundGroup().getStepSound();
            world.playSound(null, body.getX(), body.getY(), body.getZ(), step, SoundCategory.HOSTILE, 0.5F, 1.0F);
        }
    }

    private void face(Vec3d heading) {
        if (heading.x * heading.x + heading.z * heading.z < 1.0E-6) {
            return;
        }
        turn((float) (MathHelper.atan2(heading.z, heading.x) * (180.0 / Math.PI)) - 90.0F, TURN_DEGREES_PER_TICK);
    }

    private void turn(float target, float maxDegrees) {
        float yaw = MathHelper.stepUnwrappedAngleTowards(body.getYaw(), target, maxDegrees);
        body.setYaw(yaw);
        body.setBodyYaw(yaw);
        body.setHeadYaw(yaw);
    }

    /**
     * On a ladder it faces the rungs like a player. Holding someone it turns its back to them at once instead, or the
     * held player (in front of its chest) would be pressed into the wall the ladder hangs on.
     * 在梯子上像玩家一样面朝横档。抱着人时则立刻背对梯子，否则被抱的人（在它胸前）会被挤进挂梯子的墙里。
     */
    private void faceLadder(long cell) {
        BlockState state = world.getBlockState(cellPos(cell));
        if (!(state.getBlock() instanceof LadderBlock)) {
            return;
        }
        // A ladder faces away from its wall. / 梯子朝向背离所挂的墙。
        Direction out = state.get(LadderBlock.FACING);
        if (body.hasPassengers()) {
            turn(out.asRotation(), 180.0F);
        } else {
            turn(out.getOpposite().asRotation(), TURN_DEGREES_PER_TICK);
        }
    }

    /** The cell if it is a rung, or the rung under it (the top of the ladder). / 该格本身是横档，或其下方的横档（梯子顶端）。 */
    private long ladderAt(long cell) {
        if (grid.isLadder(cell)) {
            return cell;
        }
        long below = TrainNavGrid.key(TrainNavGrid.x(cell), TrainNavGrid.y(cell) - 1, TrainNavGrid.z(cell));
        return grid.isLadder(below) ? below : Long.MIN_VALUE;
    }

    private static BlockPos cellPos(long cell) {
        return new BlockPos(TrainNavGrid.x(cell), TrainNavGrid.y(cell), TrainNavGrid.z(cell));
    }

    /**
     * Opens every closed door in the next few cells, train doors included (key, lock and jam ignored), keeps open ones
     * from closing on it, and logs a forced opening of a room door on the door monitor.
     * 推开前方几格内所有关着的门，列车外门也一样（无视钥匙、上锁与卡门），让开着的门别在身上关上，并在门禁记录里给房门记一条强行打开。
     */
    private void openDoorsAhead() {
        int end = Math.min(path.size(), pathIndex + DOOR_LOOKAHEAD);
        for (int i = Math.max(0, pathIndex - 1); i < end; i++) {
            long cell = path.get(i);
            int x = TrainNavGrid.x(cell);
            int y = TrainNavGrid.y(cell);
            int z = TrainNavGrid.z(cell);
            for (int dy = 0; dy <= 1; dy++) {
                if ((grid.flags(x, y + dy, z) & (NavBlocks.FLAG_ROOM_DOOR | NavBlocks.FLAG_TRAIN_DOOR)) != 0) {
                    openDoor(new BlockPos(x, y + dy, z));
                }
            }
        }
    }

    private void openDoor(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SmallDoorBlock) {
            BlockPos lower = state.get(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
            BlockState lowerState = world.getBlockState(lower);
            if (!(world.getBlockEntity(lower) instanceof SmallDoorBlockEntity door)) {
                return;
            }
            if (!door.isOpen() && !door.isBlasted()) {
                SmallDoorBlock.toggleDoor(lowerState, world, door, lower);
                DoorLogService.recordForced(world, lower);
            }
            if (door.isOpen() && door.getCloseCountdown() < DOOR_HOLD_TICKS) {
                door.setCloseCountdown(DOOR_HOLD_TICKS);
            }
        } else if (state.getBlock() instanceof DoorBlock vanillaDoor && !state.get(DoorBlock.OPEN)) {
            vanillaDoor.setOpen(body, world, state, pos, true);
        }
    }

    private boolean inGrabReach(ServerPlayerEntity target) {
        double dx = target.getX() - body.getX();
        double dz = target.getZ() - body.getZ();
        // Line of sight too: never through a cabin wall or a closed door (open doors don't collide).
        // 还需视线：绝不隔着客房墙或关着的门抓人（打开的门没有碰撞）。
        return dx * dx + dz * dz <= PartyAnimalRules.GRAB_DISTANCE * PartyAnimalRules.GRAB_DISTANCE
                && Math.abs(target.getY() - body.getY()) <= PartyAnimalRules.GRAB_VERTICAL
                && body.canSee(target);
    }

    private void eatEffects(ServerPlayerEntity prey) {
        Vec3d mouth = body.getEyePos().add(body.getRotationVector().multiply(0.35));
        world.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.HOSTILE,
                0.9F, 0.8F + world.random.nextFloat() * 0.4F);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COOKED_BEEF)),
                mouth.x, mouth.y - 0.15, mouth.z, 5, 0.12, 0.08, 0.12, 0.06);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                mouth.x, mouth.y - 0.25, mouth.z, 3, 0.15, 0.1, 0.15, 0.0);
    }

    private @Nullable ServerPlayerEntity player(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return world.getPlayerByUuid(uuid) instanceof ServerPlayerEntity player ? player : null;
    }
}
