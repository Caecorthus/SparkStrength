package annina.sparkstrength.entity;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.role.taotie.TaotieHeadRules;
import annina.sparkstrength.role.taotie.TaotieHeadService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * The Taotie's launched head. Server authority: the server alone steers, moves, collides and hits (see
 * {@link TaotieHeadService#tickHead}); the client only advances by the synced velocity, keeps its rotation on the
 * velocity and spawns the trail. Rotation contract (both sides, every tick, prevYaw/prevPitch kept for lerp): the
 * vanilla projectile convention, {@code yaw = atan2(vx, vz)} and {@code pitch = atan2(vy, horizontal)} in degrees
 * (pitch &gt; 0 points up). The origin is the bottom centre of the 0.5 box, so the head centre is 0.25 above it.
 * 饕餮发射的头颅。服务端权威：转向、移动、碰撞与命中只由服务端处理（见 {@link TaotieHeadService#tickHead}）；客户端只按同步的
 * 速度前进、保持朝向与速度一致并生成尾迹。朝向契约（两端每 tick 更新，保留 prevYaw/prevPitch 用于插值）：原版弹射物约定，
 * {@code yaw = atan2(vx, vz)}、{@code pitch = atan2(vy, 水平长度)}，单位为度（pitch &gt; 0 朝上）。实体原点位于 0.5 碰撞箱底面中心，
 * 头颅中心在其上方 0.25 处。
 */
public final class TaotieHeadEntity extends ThrownEntity {
    private static final TrackedData<ItemStack> HEAD_STACK = DataTracker.registerData(
            TaotieHeadEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final DustParticleEffect TRAIL_DUST = new DustParticleEffect(
            new Vector3f(
                    ((TaotieHeadRules.TRAIL_COLOR >> 16) & 0xFF) / 255.0F,
                    ((TaotieHeadRules.TRAIL_COLOR >> 8) & 0xFF) / 255.0F,
                    (TaotieHeadRules.TRAIL_COLOR & 0xFF) / 255.0F),
            TaotieHeadRules.TRAIL_DUST_SCALE);

    // Server-only flight state, never tracked or saved (the type disables saving). / 仅服务端的飞行状态，不同步也不存档。
    private @Nullable UUID roundId;
    /** Shared by every head of one press, for target claims. / 同一次按键的所有头颅共享，用于目标认领。 */
    private @Nullable UUID volleyId;
    private @Nullable UUID shooterUuid;
    private @Nullable UUID targetUuid;
    private @Nullable Vec3d pendingSegmentStart;
    private double travelled;

    public TaotieHeadEntity(EntityType<? extends TaotieHeadEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    /**
     * Server: one head of the volley {@code volleyId}, launched by {@code shooter} wearing {@code skinOwner}'s face,
     * centred on {@code centre} and flying along {@code heading} (unit, its own fan direction). The first tick's sweep
     * starts at {@code sweepStart} (the shooter's eye), so a wall between the eye and the spawn point still stops it.
     * 服务端：齐射 {@code volleyId} 中的一颗头颅，由 {@code shooter} 发射、带 {@code skinOwner} 面孔，中心位于
     * {@code centre}，沿单位向量 {@code heading}（它自己的扇形方向）飞行。首个 tick 的扫掠从 {@code sweepStart}（发射者眼睛）
     * 开始，因此眼睛与生成点之间的墙仍会拦下它。
     */
    public TaotieHeadEntity(ServerWorld world, ServerPlayerEntity shooter, ServerPlayerEntity skinOwner, UUID roundId,
                            UUID volleyId, Vec3d centre, Vec3d heading, Vec3d sweepStart) {
        this(SparkStrengthEntities.taotieHead(), world);
        setOwner(shooter);
        this.shooterUuid = shooter.getUuid();
        this.roundId = roundId;
        this.volleyId = volleyId;
        this.pendingSegmentStart = sweepStart;
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponentTypes.PROFILE, new ProfileComponent(skinOwner.getGameProfile()));
        dataTracker.set(HEAD_STACK, head);
        setPosition(centre.x, centre.y - TaotieHeadRules.HEAD_HALF_SIZE, centre.z);
        setVelocity(heading.multiply(TaotieHeadRules.SPEED));
        alignRotationToVelocity(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(HEAD_STACK, ItemStack.EMPTY);
    }

    /** Synced {@code minecraft:player_head} carrying the skin owner's {@code PROFILE}. / 同步的玩家头颅物品，带皮肤主人的 PROFILE。 */
    public ItemStack getHeadStack() {
        return dataTracker.get(HEAD_STACK);
    }

    public Vec3d getHeadCenter() {
        return getPos().add(0.0D, TaotieHeadRules.HEAD_HALF_SIZE, 0.0D);
    }

    public void setHeadCenter(Vec3d centre) {
        setPosition(centre.x, centre.y - TaotieHeadRules.HEAD_HALF_SIZE, centre.z);
    }

    public @Nullable UUID getRoundId() {
        return roundId;
    }

    public @Nullable UUID getVolleyId() {
        return volleyId;
    }

    public @Nullable UUID getShooterUuid() {
        return shooterUuid;
    }

    public @Nullable UUID getTargetUuid() {
        return targetUuid;
    }

    public void setTargetUuid(@Nullable UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    /** One-shot: the first server tick's sweep start, then null. / 一次性：首个服务端 tick 的扫掠起点，之后为 null。 */
    public @Nullable Vec3d takePendingSegmentStart() {
        Vec3d start = pendingSegmentStart;
        pendingSegmentStart = null;
        return start;
    }

    public double getTravelled() {
        return travelled;
    }

    public void addTravelled(double blocks) {
        travelled += blocks;
    }

    @Override
    public void tick() {
        // Replaces ThrownEntity's gravity/drag/vanilla-collision tick: only the base entity tick runs here.
        // 取代 ThrownEntity 的重力/阻力/原版碰撞逻辑：这里只运行实体基础 tick。
        baseTick();
        if (isRemoved()) {
            return;
        }
        if (getWorld() instanceof ServerWorld) {
            TaotieHeadService.tickHead(this);
            return;
        }
        // Client: advance by the synced velocity (the tracker snaps the position every tick), trail particles.
        // 客户端：按同步速度前进（追踪器每 tick 校正位置），并生成尾迹粒子。
        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        alignRotationToVelocity(false);
        spawnTrail();
    }

    /**
     * Keeps yaw/pitch on the velocity. {@code snapPrevious} also sets prevYaw/prevPitch (spawn); otherwise prevYaw is
     * only unwrapped so the renderer's lerp never takes the long way round.
     * 使 yaw/pitch 与速度一致。{@code snapPrevious} 时同时设置 prevYaw/prevPitch（生成时）；否则只展开 prevYaw，避免渲染插值绕远路。
     */
    public void alignRotationToVelocity(boolean snapPrevious) {
        Vec3d velocity = getVelocity();
        if (velocity.lengthSquared() < 1.0E-12D) {
            return;
        }
        float yaw = (float) (MathHelper.atan2(velocity.x, velocity.z) * MathHelper.DEGREES_PER_RADIAN);
        float pitch = (float) (MathHelper.atan2(velocity.y, velocity.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
        setYaw(yaw);
        setPitch(pitch);
        if (snapPrevious) {
            prevYaw = yaw;
            prevPitch = pitch;
            return;
        }
        while (yaw - prevYaw < -180.0F) {
            prevYaw -= 360.0F;
        }
        while (yaw - prevYaw >= 180.0F) {
            prevYaw += 360.0F;
        }
    }

    @Override
    public void setVelocityClient(double x, double y, double z) {
        // Vanilla would only orient once, from (0, 0) previous angles; keep the head on every velocity update instead.
        // 原版只在旧角度为 (0, 0) 时调整一次朝向；这里每次速度更新都让头颅跟随。
        setVelocity(x, y, z);
        alignRotationToVelocity(prevYaw == 0.0F && prevPitch == 0.0F);
    }

    private void spawnTrail() {
        World world = getWorld();
        Vec3d centre = getHeadCenter();
        for (int i = 0; i < 2; i++) {
            world.addParticle(TRAIL_DUST,
                    centre.x + (random.nextDouble() - 0.5D) * 0.2D,
                    centre.y + (random.nextDouble() - 0.5D) * 0.2D,
                    centre.z + (random.nextDouble() - 0.5D) * 0.2D,
                    0.0D, 0.0D, 0.0D);
        }
        world.addParticle(ParticleTypes.SMOKE,
                centre.x + (random.nextDouble() - 0.5D) * 0.15D,
                centre.y + (random.nextDouble() - 0.5D) * 0.15D,
                centre.z + (random.nextDouble() - 0.5D) * 0.15D,
                0.0D, 0.0D, 0.0D);
    }

    @Override
    protected double getGravity() {
        return 0.0D;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        // Never saved; a loaded copy has no round and fails closed on its first tick. / 从不存档；被读取的副本没有回合，首个 tick 即失效。
        roundId = null;
    }
}
