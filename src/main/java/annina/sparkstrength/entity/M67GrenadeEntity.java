package annina.sparkstrength.entity;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.item.m67.M67Physics;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.item.m67.M67Rules;
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

    private boolean hasLanded;
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
        this(SparkStrengthEntities.m67(), world);
        setOwner(owner);
        setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        setRotation(owner.getYaw(), owner.getPitch());
        dataTracker.set(THROWER_UUID, Optional.of(owner.getUuid()));
        dataTracker.set(ROUND_ID, Optional.of(roundId));
        dataTracker.set(DETONATE_AT, world.getTime() + M67Rules.FUSE_TICKS);
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

    public @Nullable UUID getThrowerUuid() {
        return dataTracker.get(THROWER_UUID).orElse(null);
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
        world.playSound(null, getBlockPos(), SparkStrengthSounds.M67_EXPLODE, SoundCategory.PLAYERS,
                5.0F, 1.0F);
        world.spawnParticles(WatheParticles.BIG_EXPLOSION, getX(), getY() + 0.1, getZ(), 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 100, 0, 0, 0, 0.2);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()),
                getX(), getY() + 0.1, getZ(), 100, 0, 0, 0, 1.0);

        for (ServerPlayerEntity victim : List.copyOf(world.getPlayers(player -> M67Rules.containsCube(
                player.getX() - getX(), player.getY() - getY(), player.getZ() - getZ(), M67Rules.BLAST_HALF_EXTENT)))) {
            if (isRemoved() || !validRound(world)) {
                break;
            }
            // Exactly one ordinary kill attempt; protection/rewards belong to Wathe. / 仅调用一次普通击杀流程，保护与奖励交给 Wathe。
            if (GameFunctions.isPlayerAliveAndSurvival(victim)) {
                GameFunctions.killPlayer(victim, true, owner, GameConstants.DeathReasons.GRENADE);
            }
        }
        discard();
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        // This transient entity must fail closed even if external code reloads its NBT. / 即使外部代码载入 NBT，临时实体也不能复活生效。
        dataTracker.set(ROUND_ID, Optional.empty());
        dataTracker.set(THROWER_UUID, Optional.empty());
        dataTracker.set(DETONATE_AT, -1L);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
    }
}
