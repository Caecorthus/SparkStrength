package annina.sparkstrength.entity;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.item.grenade.GrenadeBlastService;
import annina.sparkstrength.item.m67.M67Physics;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.item.m67.M67Rules;
import annina.sparkstrength.role.bomber.drone.DroneCombatService;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class M67GrenadeEntity extends ThrownItemEntity {
    private static final TrackedData<Long> DETONATE_AT = DataTracker.registerData(
            M67GrenadeEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Optional<UUID>> ROUND_ID = DataTracker.registerData(
            M67GrenadeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Optional<UUID>> THROWER_UUID = DataTracker.registerData(
            M67GrenadeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    // m67_throw.ogg's striker clicks end within 0.25s of release; landing clatter must not mask them.
    // m67_throw.ogg 的撞针声在投出后 0.25 秒内结束，落地声不得将其掩盖。
    private static final int THROW_STRIKER_TICKS = 5;

    // Server-only thrower of a drone-dropped M67; never tracked or in the spawn packet. / 无人机投下的 M67 的投掷者，仅存服务器，不进追踪数据与生成包。
    private @Nullable UUID concealedThrowerUuid;
    private boolean hasLanded;
    private boolean landSoundPending;
    private int interpolationTicks;
    private double trackedX;
    private double trackedY;
    private double trackedZ;
    private float trackedYaw;
    private float trackedPitch;

    public M67GrenadeEntity(EntityType<? extends M67GrenadeEntity> type, World world) {
        super(type, world);
    }

    public M67GrenadeEntity(ServerWorld world, ServerPlayerEntity owner, UUID roundId) {
        this(world, owner, roundId, false);
        setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        setRotation(owner.getYaw(), owner.getPitch());
    }

    private M67GrenadeEntity(ServerWorld world, ServerPlayerEntity owner, UUID roundId, boolean concealThrower) {
        this(SparkStrengthEntities.m67(), world);
        if (concealThrower) {
            concealedThrowerUuid = owner.getUuid();
        } else {
            setOwner(owner);
            dataTracker.set(THROWER_UUID, Optional.of(owner.getUuid()));
        }
        dataTracker.set(ROUND_ID, Optional.of(roundId));
        dataTracker.set(DETONATE_AT, world.getTime() + M67Rules.FUSE_TICKS);
    }

    /**
     * Server: an M67 released by a Bomber drone, with the thrower concealed. The thrower is kept only in a server
     * field ({@link #getThrowerUuid} still answers it, so blast attribution, disconnect cleanup and SparkWitch's
     * reflective M67 seam keep working) and the vanilla projectile owner is never set, because 1.21.1's
     * {@code setOwner(null)} cannot clear it and its entity id would ride in the spawn packet. Pose comes from the drone.
     * 服务器：炸弹客无人机投下的 M67，投掷者被隐藏。投掷者只存于服务器字段（{@link #getThrowerUuid} 仍返回它，因此爆炸归属、
     * 断线清理与 SparkWitch 的 M67 反射接缝照常工作），且从不设置原版投射物主人——1.21.1 的 setOwner(null) 无法清除它，
     * 其实体 id 会随生成包下发。位姿取自无人机。
     */
    public static M67GrenadeEntity droppedConcealed(ServerWorld world, ServerPlayerEntity owner, UUID roundId,
                                                    Vec3d pos, float yaw, float pitch) {
        M67GrenadeEntity grenade = new M67GrenadeEntity(world, owner, roundId, true);
        grenade.setPosition(pos);
        grenade.setRotation(yaw, pitch);
        return grenade;
    }

    @Override
    protected Item getDefaultItem() {
        return SparkStrengthItems.m67();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(DETONATE_AT, -1L);
        builder.add(ROUND_ID, Optional.empty());
        builder.add(THROWER_UUID, Optional.empty());
    }

    /**
     * Thrower uuid: the concealed server-only value when set, else the tracked one. Public contract: SparkWitch's
     * {@code SparkStrengthM67Compat} reflects this getter on the server (blast attribution).
     * 投掷者 uuid：设置了隐藏的服务器字段时取它，否则取追踪值。公开契约：SparkWitch 的 SparkStrengthM67Compat 在服务器反射此 getter（爆炸归属）。
     */
    public @Nullable UUID getThrowerUuid() {
        return concealedThrowerUuid != null ? concealedThrowerUuid : dataTracker.get(THROWER_UUID).orElse(null);
    }

    public @Nullable UUID getRoundId() {
        return dataTracker.get(ROUND_ID).orElse(null);
    }

    public long getDetonateAt() {
        return dataTracker.get(DETONATE_AT);
    }

    public int getRemainingFuseTicks() {
        return (int) Math.clamp(getDetonateAt() - getWorld().getTime(), 0L, M67Rules.FUSE_TICKS);
    }

    @Override
    public void tick() {
        // Bypass ThrownEntity.tick: it integrates movement itself. / 绕过父投掷物 tick，避免重复位移。
        baseTick();
        if (isRemoved()) {
            return;
        }
        if (getWorld().isClient()) {
            if (interpolationTicks > 0) {
                lerpPosAndRotation(interpolationTicks, trackedX, trackedY, trackedZ, trackedYaw, trackedPitch);
                interpolationTicks--;
            }
            return;
        }
        ServerWorld world = (ServerWorld) getWorld();
        UUID throwerUuid = getThrowerUuid();
        ServerPlayerEntity owner = throwerUuid == null ? null : world.getServer().getPlayerManager().getPlayer(throwerUuid);
        // Connected dead owners retain attribution; stale rounds never detonate. / 在线死亡投掷者保留归属，旧回合禁止引爆。
        if (owner == null || !validRound(world) || getDetonateAt() < 0) {
            discard();
            return;
        }
        if (world.getTime() >= getDetonateAt()) {
            detonate(world, owner);
            return;
        }
        tickServerMotion(world);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
        trackedX = x;
        trackedY = y;
        trackedZ = z;
        trackedYaw = yaw;
        trackedPitch = pitch;
        interpolationTicks = Math.max(1, steps);
    }

    private void tickServerMotion(ServerWorld world) {
        M67Physics.Vector requested = M67Physics.requestedMovement(vector(getVelocity()));
        Vec3d movement = new Vec3d(requested.x(), requested.y(), requested.z());
        // Swept block shapes, no player-body collisions or material-dependent friction. / 扫掠方块形状，不碰撞玩家身体或采用材质摩擦。
        Vec3d actual = Entity.adjustMovementForCollisions(this, movement, getBoundingBox(), world, List.of());
        setPosition(getPos().add(actual));
        M67Physics.Motion motion = M67Physics.afterCollision(requested,
                M67Physics.collision(requested, vector(actual)), hasLanded);
        hasLanded = motion.hasLanded();
        setOnGround(motion.grounded());
        M67Physics.Vector velocity = motion.velocity();
        setVelocity(velocity.x(), velocity.y(), velocity.z());
        if (motion.firstLanding()) {
            landSoundPending = true;
        }
        // Spawned on release, so age counts server ticks since the throw. / 投出时生成，age 即投出后的服务端刻数。
        if (landSoundPending && age >= THROW_STRIKER_TICKS) {
            landSoundPending = false;
            world.playSound(null, getX(), getY(), getZ(), SparkStrengthSounds.M67_LAND,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static M67Physics.Vector vector(Vec3d value) {
        return new M67Physics.Vector(value.x, value.y, value.z);
    }

    private boolean validRound(ServerWorld world) {
        UUID roundId = getRoundId();
        return roundId != null && M67RoundService.isCurrentRound(world, roundId);
    }

    private void detonate(ServerWorld world, ServerPlayerEntity owner) {
        if (!validRound(world)) {
            discard();
            return;
        }
        // Breaks drones itself below; SparkWitch's onBlast (fired from the discard) must not break them again.
        // 下方自行击毁无人机；SparkWitch 的 onBlast（由 discard 触发）不得再次处理无人机。
        DroneCombatService.runOwnBlast(() -> explode(world, owner));
    }

    private void explode(ServerWorld world, ServerPlayerEntity owner) {
        world.playSound(null, getBlockPos(), SparkStrengthSounds.M67_EXPLODE, SoundCategory.PLAYERS,
                5.0F, 1.0F);
        world.spawnParticles(WatheParticles.BIG_EXPLOSION, getX(), getY() + 0.1, getZ(), 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 100, 0, 0, 0, 0.2);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                getX(), getY() + 0.1, getZ(), 100, 0, 0, 0, 1.0);

        List<ServerPlayerEntity> candidates = List.copyOf(world.getPlayers(GameFunctions::isPlayerAliveAndSurvival));
        for (ServerPlayerEntity victim : GrenadeBlastService.filterVictims(world, this, candidates, M67Rules.BLAST_RADIUS)) {
            if (isRemoved() || !validRound(world)) {
                break;
            }
            // Exactly one ordinary kill attempt; protection/rewards belong to Wathe. / 仅调用一次普通击杀流程，保护与奖励交给 Wathe。
            if (GameFunctions.isPlayerAliveAndSurvival(victim)) {
                GameFunctions.killPlayer(victim, true, owner, GameConstants.DeathReasons.GRENADE);
            }
        }
        if (!isRemoved() && validRound(world)) {
            // Bomber drones in the blast break too (bomb drones chain-detonate). / 爆炸范围内的炸弹客无人机同样被击毁（炸弹无人机连锁引爆）。
            DroneCombatService.breakDronesInBlast(world, this, M67Rules.BLAST_RADIUS, owner);
        }
        discard();
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        // This transient entity must fail closed even if external code reloads its NBT. / 即使外部代码载入 NBT，临时实体也不能复活生效。
        dataTracker.set(ROUND_ID, Optional.empty());
        dataTracker.set(THROWER_UUID, Optional.empty());
        concealedThrowerUuid = null;
        dataTracker.set(DETONATE_AT, -1L);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
    }
}
