package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.attendant.DoorLogService;
import annina.sparkstrength.role.bomber.drone.DroneService;
import annina.sparkstrength.role.partyanimal.nav.NavBlocks;
import annina.sparkstrength.role.partyanimal.nav.TrainNavGrid;
import annina.sparkstrength.role.partyanimal.nav.TrainPathfinder;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
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
 * the target changes cell) and walked at the phase's speed; closed doors two to three cells ahead are pushed open.
 * 移动是沿自有 TrainNavGrid 格子的运动学位移：每半秒（或目标换格时）重新规划，按当前阶段速度行走；前方两三格内关着的门会被推开。</p>
 */
public final class PartygoerBrain {
    private static final int REPLAN_TICKS = 10;
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

    /** Alive in the world and not inside a Taotie: the only time weapons can end it. / 在场且不在饕餮肚里：只有此时武器能打死它。 */
    public boolean isKillable() {
        return !finished && state != PartygoerState.SWALLOWED && !body.isRemoved();
    }

    // ---- Tick / 每刻 ----

    public void tick() {
        if (finished) {
            return;
        }
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
        if (walkTo(destination.anchorCell(), PartyAnimalRules.CARRY_SPEED_LEVEL)) {
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
        biting = true;
        try {
            GameFunctions.killPlayer(prey, false, PartygoerService.killCredit(this, prey),
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
     * Someone died (KillPlayer.AFTER). Prey killed in its hands by someone alive triggers the one rage; otherwise it gives
     * up. The rage target dying any other way also ends it. A prey that dies before being caught is just replaced.
     * 有人死亡（KillPlayer.AFTER）。手中猎物被活着的人杀死时触发唯一一次狂怒，否则放弃。狂怒目标以其他方式死亡也会结束。
     * 尚未抓到的猎物死亡则只是换目标。
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
                        killer != null && killer.getUuid().equals(victimUuid))) {
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
        body.setPartyState(next);
        resetPath();
        PartygoerService.publishStatus(this);
    }

    private boolean grab(ServerPlayerEntity target, PartygoerState next) {
        if (target.getVehicle() != body) {
            if (target.isSleeping()) {
                target.wakeUp();
            }
            // Off any couch first: a seated player sinks into the floor. / 先离开座位：坐着的玩家位置在地板里。
            target.stopRiding();
            if (!target.startRiding(body, true)) {
                return false;
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
        double speed = PartyAnimalRules.blocksPerTick(speedLevel);
        if (goal == Long.MIN_VALUE || walkTo(goal, speedLevel)) {
            // Last stretch, or the target stands where the grid can't follow: straight at it.
            // 最后一段，或目标站在网格跟不到的位置：直线冲过去。
            stepToward(target.getPos(), speed);
        }
    }

    /** Walks the planned path towards {@code goal}; true once there. / 沿规划路径走向 goal；到达时返回 true。 */
    private boolean walkTo(long goal, int speedLevel) {
        if (goal != pathGoal || --replanIn <= 0) {
            replan(goal);
        }
        if (path.isEmpty()) {
            return true;
        }
        openDoorsAhead();
        double remaining = PartyAnimalRules.blocksPerTick(speedLevel);
        while (remaining > 1.0E-6 && pathIndex < path.size()) {
            Vec3d target = PartyRoomIndex.cellCentre(grid, path.get(pathIndex));
            Vec3d delta = target.subtract(body.getPos());
            double distance = delta.length();
            if (distance <= remaining) {
                moveTo(target, delta);
                remaining -= distance;
                pathIndex++;
            } else {
                Vec3d step = delta.multiply(remaining / distance);
                moveTo(body.getPos().add(step), step);
                remaining = 0.0;
            }
        }
        return pathIndex >= path.size();
    }

    private void replan(long goal) {
        replanIn = REPLAN_TICKS;
        pathGoal = goal;
        long start = grid.snap(body.getX(), body.getY(), body.getZ());
        path = TrainPathfinder.path(grid, start, goal, TrainPathfinder.DEFAULT_BUDGET);
        // Skip the cell we stand in. / 跳过脚下所在格。
        pathIndex = path.size() > 1 ? 1 : path.size();
    }

    private void resetPath() {
        path = List.of();
        pathIndex = 0;
        pathGoal = Long.MIN_VALUE;
        replanIn = 0;
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
        double moved = pos.distanceTo(body.getPos());
        body.setPosition(pos.x, pos.y, pos.z);
        face(heading);
        footstepDistance += moved;
        if (footstepDistance >= FOOTSTEP_EVERY) {
            footstepDistance = 0.0;
            world.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ENTITY_ZOMBIE_STEP,
                    SoundCategory.HOSTILE, 0.5F, 1.0F);
        }
    }

    private void face(Vec3d heading) {
        if (heading.x * heading.x + heading.z * heading.z < 1.0E-6) {
            return;
        }
        float target = (float) (MathHelper.atan2(heading.z, heading.x) * (180.0 / Math.PI)) - 90.0F;
        float yaw = MathHelper.stepUnwrappedAngleTowards(body.getYaw(), target, TURN_DEGREES_PER_TICK);
        body.setYaw(yaw);
        body.setBodyYaw(yaw);
        body.setHeadYaw(yaw);
    }

    /**
     * Opens closed room doors in the next few cells (key, lock and jam ignored), keeps open ones from closing on it, and
     * logs a forced opening on the door monitor.
     * 推开前方几格内关着的房门（无视钥匙、上锁与卡门），让开着的门别在身上关上，并在门禁记录里记一条强行打开。
     */
    private void openDoorsAhead() {
        int end = Math.min(path.size(), pathIndex + DOOR_LOOKAHEAD);
        for (int i = Math.max(0, pathIndex - 1); i < end; i++) {
            long cell = path.get(i);
            int x = TrainNavGrid.x(cell);
            int y = TrainNavGrid.y(cell);
            int z = TrainNavGrid.z(cell);
            for (int dy = 0; dy <= 1; dy++) {
                if ((grid.flags(x, y + dy, z) & NavBlocks.FLAG_ROOM_DOOR) != 0) {
                    openDoor(new BlockPos(x, y + dy, z));
                }
            }
        }
    }

    private void openDoor(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SmallDoorBlock && !(state.getBlock() instanceof TrainDoorBlock)) {
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
        return dx * dx + dz * dz <= PartyAnimalRules.GRAB_DISTANCE * PartyAnimalRules.GRAB_DISTANCE
                && Math.abs(target.getY() - body.getY()) <= PartyAnimalRules.GRAB_VERTICAL;
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
