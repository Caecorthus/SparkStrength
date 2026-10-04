package annina.sparkstrength.entity;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.role.bomber.drone.DroneCombatService;
import annina.sparkstrength.role.bomber.drone.DroneFlight;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.bomber.drone.DronePilotEndReason;
import annina.sparkstrength.role.bomber.drone.DronePilotService;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import annina.sparkstrength.role.bomber.drone.DroneService;
import annina.sparkstrength.role.bomber.drone.DroneState;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A placed Bomber drone (both kinds share this class; the kind comes from the entity type).
 * 已放置的炸弹客无人机（两种型号共用此类，型号由实体类型决定）。
 *
 * <p>Authority: the server owns charge, state, payload, ownership and destruction. Only the pilot's client moves the
 * drone it pilots (client-predicted, server-validated, like a vanilla boat); every other client interpolates tracker
 * updates.
 * 权威性：电量、状态、挂载、归属与损毁均由服务器决定。只有驾驶者客户端会移动其驾驶的无人机（客户端预测、服务器校验，同原版船）；
 * 其他客户端只插值追踪更新。</p>
 *
 * <p>Privacy: owner, pilot and round ids are server-only fields and never enter the DataTracker, so clients cannot read
 * who the Bomber is from the entity. The owner learns its drones' entity ids through its own tablet snapshot.
 * 隐私：主人、驾驶者与回合 id 仅存于服务器字段，绝不写入 DataTracker，客户端无法从实体读出谁是炸弹客。
 * 主人通过自己的平板快照得知其无人机的实体 id。</p>
 *
 * <p>Flight: powered drones ignore gravity (an unpiloted airborne drone holds its altitude and hovers); only a FALLING
 * drone (battery flat or owner lost) is pulled down, and it breaks when it lands. Movement collides with blocks only
 * (closed doors included) and is clamped to Wathe's play area and the world border, identically on the server and the
 * pilot's client.
 * 飞行：通电时无人机不受重力（无人操控的空中无人机保持高度悬停）；只有 FALLING（没电或主人失联）才会下坠，并在落地时损毁。
 * 移动只与方块碰撞（含关闭的门），并限制在 Wathe 游戏区域与世界边界内，服务器与驾驶客户端算法一致。</p>
 */
public class DroneEntity extends Entity {
    private static final TrackedData<Byte> STATE = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> CHARGE = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> PAYLOAD = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Thin slab under the hull that counts as "resting on something". / 机身下方视为“有支撑”的薄层厚度。 */
    private static final double GROUND_PROBE = 0.05;
    private static final double MIN_MOVE_SQUARED = 1.0E-12;
    /** Remote clients render drones up to this far, independent of the small hitbox. / 远端客户端渲染距离，不受小碰撞箱影响。 */
    private static final double RENDER_DISTANCE = 64.0;
    // Client rotor animation in radians per tick; purely cosmetic. / 客户端旋翼动画（弧度/刻），仅外观。
    private static final float ROTOR_MAX_SPEED = 1.6F;
    private static final float ROTOR_SPIN_UP = 0.2F;
    private static final float ROTOR_SPIN_DOWN = 0.9F;
    private static final float FULL_TURN = (float) (Math.PI * 2.0);
    private static final float BOB_AMPLITUDE = 0.04F;
    private static final float BOB_FREQUENCY = 0.2F;
    private static final float HOVER_BLEND_STEP = 0.1F;

    /**
     * Client bridge set by the client entrypoint: true when the local player pilots this drone, so tracker position
     * updates are ignored for it (the pilot's client is the movement authority). Always false on a dedicated server.
     * Implementations compare entity ids, never cached instances (re-tracking creates a new instance with the same id).
     * 由客户端入口设置的桥接：本地玩家正在驾驶此无人机时返回 true，从而忽略其追踪位置更新（驾驶者客户端是移动权威）。专用服务器上恒为 false。
     * 实现须比较实体 id，不能缓存实例（重新追踪会以相同 id 创建新实例）。
     */
    public static volatile Predicate<DroneEntity> localPilotCheck = drone -> false;

    /**
     * Optional client bridge: true when the local player owns this drone (known only from its own tablet snapshot, by
     * entity id). It only makes the owner's right-click on the drone swing locally instead of also using the held item
     * (e.g. opening the tablet); the server alone decides whether the recovery happens. Default false is safe.
     * 可选客户端桥接：本地玩家拥有此无人机时返回 true（仅能从自己的平板快照按实体 id 得知）。它只让主人右键无人机时在本地挥手，
     * 不再同时使用手持物品（如打开平板）；是否回收完全由服务器决定。默认 false 是安全的。
     */
    public static volatile Predicate<DroneEntity> localOwnerCheck = drone -> false;

    // Server-only state. / 仅服务器状态。
    private @Nullable UUID ownerUuid;
    private @Nullable UUID roundId;
    private @Nullable UUID pilotUuid;
    // World time of the last real (collided) displacement, any axis / horizontal only. / 最近一次真实（碰撞后）位移的世界时间：任意轴 / 仅水平。
    private long lastDisplacedTime = Long.MIN_VALUE / 2;
    private long lastHorizontalDisplacedTime = Long.MIN_VALUE / 2;
    private double horizontalBudget;
    private double verticalBudget;
    private long moveBudgetTime = -1L;
    private boolean ownerLost;
    private int fallTicks;

    // Client-only interpolation and animation. / 仅客户端插值与动画。
    private int interpolationTicks;
    private double trackedX;
    private double trackedY;
    private double trackedZ;
    private float trackedYaw;
    private float trackedPitch;
    private float rotorAngle;
    private float prevRotorAngle;
    private float rotorSpeed;
    private float hoverBlend;
    private float prevHoverBlend;

    public DroneEntity(EntityType<? extends DroneEntity> type, World world) {
        super(type, world);
    }

    public DroneKind kind() {
        return getType() == SparkStrengthEntities.bombDrone() ? DroneKind.BOMB : DroneKind.GRENADE;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE, (byte) DroneState.GROUNDED.wire());
        builder.add(CHARGE, DroneRules.CHARGE_MAX);
        builder.add(PAYLOAD, false);
    }

    public DroneState state() {
        return DroneState.fromWire(dataTracker.get(STATE));
    }

    protected void setState(DroneState state) {
        dataTracker.set(STATE, (byte) state.wire());
    }

    /** Battery in basis points. / 电量（万分比）。 */
    public int charge() {
        return dataTracker.get(CHARGE);
    }

    protected void setCharge(int charge) {
        dataTracker.set(CHARGE, DroneRules.clampCharge(charge));
    }

    /** Grenade drone carries a bound M67 (rendered under the hull). / 投弹无人机挂载了 M67（渲染在机腹下）。 */
    public boolean hasPayload() {
        return dataTracker.get(PAYLOAD);
    }

    public void setPayload(boolean payload) {
        dataTracker.set(PAYLOAD, payload);
    }

    public @Nullable UUID ownerUuid() {
        return ownerUuid;
    }

    public @Nullable UUID roundId() {
        return roundId;
    }

    public @Nullable UUID pilotUuid() {
        return pilotUuid;
    }

    /** Only DronePilotService may change the pilot. / 只有 DronePilotService 可以修改驾驶者。 */
    public void setPilotUuid(@Nullable UUID pilotUuid) {
        this.pilotUuid = pilotUuid;
    }

    /**
     * Server: bind a freshly created drone to its owner and round before spawning.
     * 服务器：生成前把新建的无人机绑定到主人与回合。
     */
    public void initPlaced(UUID ownerUuid, UUID roundId, int charge, boolean payload) {
        this.ownerUuid = ownerUuid;
        this.roundId = roundId;
        setCharge(charge);
        setPayload(payload && kind() == DroneKind.GRENADE);
        setState(DroneState.GROUNDED);
        setVelocity(Vec3d.ZERO);
    }

    @Override
    public void tick() {
        baseTick();
        if (isRemoved()) {
            return;
        }
        if (getWorld().isClient()) {
            clientTick();
            return;
        }
        serverTick((ServerWorld) getWorld());
    }

    private void serverTick(ServerWorld world) {
        if (ownerUuid == null || roundId == null || !roundId.equals(DroneService.currentRoundId(world))) {
            // Stale round, NBT reload or never initialised: fail closed. / 旧回合、NBT 重载或未初始化：失效移除。
            DronePilotService.endFor(this, DronePilotEndReason.ROUND_OVER);
            discard();
            return;
        }
        if (!ownerLost && !DroneService.keepsDrones(world.getServer().getPlayerManager().getPlayer(ownerUuid))) {
            // Owner died, left, or stopped being the real Bomber: the drone loses control for good. A Bomber merely out
            // of survival (NoellesRoles Taotie swallow) keeps it; the drone just holds its current state meanwhile.
            // 主人死亡、离线或不再是真实炸弹客：无人机永久失控。仅暂时脱离生存模式（NoellesRoles 饕餮吞噬）的炸弹客保留控制权，
            // 期间无人机维持当前状态。
            ownerLost = true;
            if (kind() == DroneKind.BOMB) {
                handOff(() -> DroneCombatService.fizzle(this), DronePilotEndReason.PILOT_DOWN);
                return;
            }
            startFalling(DronePilotEndReason.PILOT_DOWN);
        }
        if (state() == DroneState.FALLING) {
            tickFalling(world);
            return;
        }
        long now = world.getTime();
        if (!DroneFlight.recentlyDisplaced(now - lastDisplacedTime)) {
            // Hovering or resting: a dropped M67 must not inherit stale flight speed. / 悬停或停放：投下的 M67 不能继承过期速度。
            setVelocity(Vec3d.ZERO);
        }
        DroneState state = refreshPoweredState(now);
        int charge = DroneFlight.drain(kind(), state, charge());
        setCharge(charge);
        if (charge <= 0 && state.rotorsOn()) {
            if (kind() == DroneKind.BOMB) {
                handOff(() -> DroneCombatService.deplete(this), DronePilotEndReason.DEPLETED);
            } else {
                startFalling(DronePilotEndReason.DEPLETED);
            }
        }
    }

    private void startFalling(DronePilotEndReason reason) {
        if (state() == DroneState.FALLING) {
            return;
        }
        setState(DroneState.FALLING);
        fallTicks = 0;
        DronePilotService.endFor(this, reason);
    }

    private void tickFalling(ServerWorld world) {
        Vec3d requested = clampToBounds(getVelocity().add(0.0, -DroneRules.FALL_GRAVITY, 0.0));
        Vec3d actual = collide(requested);
        setPosition(getPos().add(actual));
        boolean landed = requested.y < 0.0 && actual.y > requested.y + 1.0E-7;
        setVelocity(new Vec3d(
                blocked(requested.x, actual.x) ? 0.0 : actual.x,
                blocked(requested.y, actual.y) ? 0.0 : actual.y,
                blocked(requested.z, actual.z) ? 0.0 : actual.z).multiply(DroneRules.FALL_DRAG));
        fallTicks++;
        if (landed || fallTicks >= DroneRules.MAX_FALL_TICKS) {
            // Landing (or a very long fall) counts as destroyed with no attacker. / 落地（或下坠过久）视为无攻击者的损毁。
            handOff(() -> DroneCombatService.destroy(this, null), DronePilotEndReason.DESTROYED);
        }
    }

    private static boolean blocked(double requested, double actual) {
        return Math.abs(requested - actual) > 1.0E-7;
    }

    /**
     * Hand removal to DroneCombatService; if it left the drone alive anyway, end the pilot session and discard it so a
     * lost drone never lingers.
     * 把移除交给 DroneCombatService；若其仍未移除无人机，则结束驾驶会话并移除，保证失控无人机不会残留。
     */
    private void handOff(Runnable combatCall, DronePilotEndReason fallbackReason) {
        combatCall.run();
        if (!isRemoved()) {
            DronePilotService.endFor(this, fallbackReason);
            discard();
        }
    }

    /**
     * Server: apply one pilot-reported pose. The horizontal and vertical parts of the step are capped separately
     * ({@code DroneRules.maxHorizontalStep}/{@code maxVerticalStep}, each also limited by a per-tick budget against
     * packet flooding), clamped to the play area and world border and re-collided with blocks. State and drain come from the resulting real displacement ({@link DroneFlight#poweredState}); the client's
     * {@code moving} flag is an unused hint kept for the wire contract, so it can neither lower nor raise the drain.
     * Returns false when the result differs from the request by more than {@code DroneRules.CORRECTION_EPSILON}, so the
     * caller sends a correction to the pilot. No movement while FALLING or with a flat battery.
     * 服务器：应用驾驶者上报的一次位姿。单步的水平与垂直分量分别限制（maxHorizontalStep / maxVerticalStep，且各自受按刻补充的预算约束，
     * 防止刷包加速），限制在游戏区域与世界边界内，并重新计算方块碰撞。状态与耗电只取决于由此得到的真实位移；
     * 客户端的 moving 标志仅为保留线协议的未用提示，既不能降低也不能提高耗电。结果与请求相差超过 CORRECTION_EPSILON 时返回 false，
     * 由调用方向驾驶者发送纠正。下坠中或没电时不移动。
     */
    public boolean applyPilotStep(Vec3d target, float yaw, float pitch, boolean moving) {
        if (getWorld().isClient() || isRemoved()) {
            return true;
        }
        if (!isFinite(target) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return false;
        }
        Vec3d start = getPos();
        double epsilonSquared = DroneRules.CORRECTION_EPSILON * DroneRules.CORRECTION_EPSILON;
        if (state() == DroneState.FALLING || charge() <= 0) {
            return start.squaredDistanceTo(target) <= epsilonSquared;
        }
        long now = getWorld().getTime();
        double maxHorizontal = DroneRules.maxHorizontalStep(kind());
        double maxVertical = DroneRules.maxVerticalStep();
        if (moveBudgetTime < 0L) {
            horizontalBudget = maxHorizontal * DroneFlight.MOVE_BUDGET_STEPS;
            verticalBudget = maxVertical * DroneFlight.MOVE_BUDGET_STEPS;
        } else {
            horizontalBudget = DroneFlight.refillMoveBudget(horizontalBudget, maxHorizontal, now - moveBudgetTime);
            verticalBudget = DroneFlight.refillMoveBudget(verticalBudget, maxVertical, now - moveBudgetTime);
        }
        moveBudgetTime = now;

        Vec3d requested = target.subtract(start);
        double scale = DroneFlight.horizontalScale(requested.x, requested.z,
                DroneFlight.allowedStep(maxHorizontal, horizontalBudget));
        double vertical = DroneFlight.clampVertical(requested.y, DroneFlight.allowedStep(maxVertical, verticalBudget));
        requested = new Vec3d(requested.x * scale, vertical, requested.z * scale);
        horizontalBudget = Math.max(0.0, horizontalBudget - requested.horizontalLength());
        verticalBudget = Math.max(0.0, verticalBudget - Math.abs(requested.y));
        Vec3d actual = collide(clampToBounds(requested));
        setPosition(start.add(actual));
        setYaw(yaw);
        setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        // Real displacement per tick, inherited by a dropped M67. / 每刻真实位移，投下的 M67 继承此速度。
        setVelocity(actual);
        if (DroneFlight.isDisplacement(actual.length())) {
            lastDisplacedTime = now;
        }
        if (DroneFlight.isDisplacement(actual.horizontalLength())) {
            lastHorizontalDisplacedTime = now;
        }
        refreshPoweredState(now);
        return getPos().squaredDistanceTo(target) <= epsilonSquared;
    }

    /** Server: set and return the powered state from the real displacement history. / 服务器：按真实位移历史设置并返回通电状态。 */
    private DroneState refreshPoweredState(long now) {
        DroneState state = DroneFlight.poweredState(isSupported(), DroneFlight.recentlyDisplaced(now - lastDisplacedTime),
                DroneFlight.recentlyDisplaced(now - lastHorizontalDisplacedTime));
        setState(state);
        return state;
    }

    /**
     * Pilot's client only: predict one tick of controlled flight ({@code delta} already speed-capped by the caller),
     * with the same bounds clamp and block collisions as the server, and set the look. The pilot client then sends the
     * resulting pose to the server. Call once per client tick after the world tick (e.g. END_CLIENT_TICK): it sets the
     * previous/last-render pose to the pre-move pose so rendering and the camera interpolate smoothly.
     * 仅驾驶者客户端：预测一刻受控飞行（delta 已由调用方限速），使用与服务器相同的边界限制与方块碰撞，并设置朝向。之后驾驶客户端把结果位姿
     * 发给服务器。请在每个客户端 tick 的世界 tick 之后调用一次（如 END_CLIENT_TICK）：它把上一帧/渲染起点设为移动前位姿，使渲染与镜头平滑插值。
     */
    public void clientPilotMove(Vec3d delta, float yaw, float pitch) {
        if (!getWorld().isClient() || isRemoved() || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        Vec3d start = getPos();
        Vec3d actual = Vec3d.ZERO;
        if (state() != DroneState.FALLING && charge() > 0 && isFinite(delta) && delta.lengthSquared() > MIN_MOVE_SQUARED) {
            actual = collide(clampToBounds(delta));
        }
        prevX = start.x;
        prevY = start.y;
        prevZ = start.z;
        lastRenderX = start.x;
        lastRenderY = start.y;
        lastRenderZ = start.z;
        prevYaw = getYaw();
        prevPitch = getPitch();
        setPosition(start.add(actual));
        setYaw(yaw);
        setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        setVelocity(actual);
        interpolationTicks = 0;
    }

    /** Swept block collisions only (no entities). / 仅扫掠方块碰撞（不含实体）。 */
    private Vec3d collide(Vec3d movement) {
        if (movement.lengthSquared() <= MIN_MOVE_SQUARED) {
            return Vec3d.ZERO;
        }
        return Entity.adjustMovementForCollisions(this, movement, getBoundingBox(), getWorld(), List.of());
    }

    /**
     * Shorten a step so the hull never crosses Wathe's play area or the world border. An axis already outside is never
     * pushed back (no teleport), only kept from going further out. Runs before collisions, which only shorten steps.
     * 缩短位移，使机身不越过 Wathe 游戏区域与世界边界。已在边界外的轴不会被拉回（不瞬移），只是不能继续外移。在碰撞之前执行（碰撞只会缩短位移）。
     */
    private Vec3d clampToBounds(Vec3d movement) {
        Box area = MapVariablesWorldComponent.KEY.get(getWorld()).getPlayArea();
        WorldBorder border = getWorld().getWorldBorder();
        Box hull = getBoundingBox();
        double x = clampAxis(movement.x, hull.minX, hull.maxX,
                Math.max(area.minX, border.getBoundWest()), Math.min(area.maxX, border.getBoundEast()));
        double y = clampAxis(movement.y, hull.minY, hull.maxY, area.minY, area.maxY);
        double z = clampAxis(movement.z, hull.minZ, hull.maxZ,
                Math.max(area.minZ, border.getBoundNorth()), Math.min(area.maxZ, border.getBoundSouth()));
        return new Vec3d(x, y, z);
    }

    private static double clampAxis(double delta, double hullMin, double hullMax, double boundMin, double boundMax) {
        if (delta > 0.0) {
            return Math.max(0.0, Math.min(delta, boundMax - hullMax));
        }
        if (delta < 0.0) {
            return Math.min(0.0, Math.max(delta, boundMin - hullMin));
        }
        return 0.0;
    }

    /** Something solid directly under the hull. / 机身正下方有实体方块。 */
    private boolean isSupported() {
        Box hull = getBoundingBox();
        Box below = new Box(hull.minX, hull.minY - GROUND_PROBE, hull.minZ, hull.maxX, hull.minY, hull.maxZ);
        return !getWorld().isBlockSpaceEmpty(this, below);
    }

    private static boolean isFinite(Vec3d value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public boolean isLocallyPiloted() {
        return getWorld().isClient() && localPilotCheck.test(this);
    }

    @Override
    public boolean isLogicalSideForUpdatingMovement() {
        // The pilot's client owns this drone's motion; ignore tracker moves there. / 驾驶者客户端掌控其运动，忽略追踪位移。
        return isLocallyPiloted() || super.isLogicalSideForUpdatingMovement();
    }

    private void clientTick() {
        if (isLocallyPiloted()) {
            // The pilot's client moves this drone itself. / 驾驶者客户端自行移动此无人机。
            interpolationTicks = 0;
        } else if (interpolationTicks > 0) {
            lerpPosAndRotation(interpolationTicks, trackedX, trackedY, trackedZ, trackedYaw, trackedPitch);
            interpolationTicks--;
        }
        prevRotorAngle = rotorAngle;
        rotorSpeed = state().rotorsOn() ? Math.min(ROTOR_MAX_SPEED, rotorSpeed + ROTOR_SPIN_UP) : rotorSpeed * ROTOR_SPIN_DOWN;
        if (rotorSpeed < 0.001F) {
            rotorSpeed = 0.0F;
        }
        rotorAngle += rotorSpeed;
        if (rotorAngle > FULL_TURN * 64.0F) {
            // Wrap both ends by whole turns so the lerp never jumps. / 两端同时减去整圈，插值不会跳变。
            float wrap = FULL_TURN * 64.0F;
            rotorAngle -= wrap;
            prevRotorAngle -= wrap;
        }
        prevHoverBlend = hoverBlend;
        float targetBlend = state() == DroneState.HOVERING ? 1.0F : 0.0F;
        hoverBlend += MathHelper.clamp(targetBlend - hoverBlend, -HOVER_BLEND_STEP, HOVER_BLEND_STEP);
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

    /**
     * Client renderer helper: rotor angle in radians. Spins up to full speed while {@link DroneState#rotorsOn()} and
     * coasts down otherwise.
     * 客户端渲染辅助：旋翼角度（弧度）。旋翼开启时加速到全速，关闭后逐渐减速停转。
     */
    public float rotorAngle(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevRotorAngle, rotorAngle);
    }

    /**
     * Client renderer helper: small vertical bob in blocks, faded in while HOVERING and out otherwise. Cosmetic only;
     * the hitbox does not move.
     * 客户端渲染辅助：悬停时淡入、其他状态淡出的轻微上下浮动（格）。仅外观，碰撞箱不动。
     */
    public float bobOffset(float tickDelta) {
        float blend = MathHelper.lerp(tickDelta, prevHoverBlend, hoverBlend);
        return blend * BOB_AMPLITUDE * MathHelper.sin((age + tickDelta) * BOB_FREQUENCY);
    }

    /**
     * Owner's right-click on a placed grenade drone picks it back up into the hotbar (server decides). Bomb drones and
     * other players pass, so their held item (e.g. a knife's stab) still works.
     * 主人右键已放置的投弹无人机可将其收回快捷栏（由服务器判定）。炸弹无人机与其他玩家直接放行，使其手持物品（如刀的刺击）照常生效。
     */
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (isRemoved() || kind() != DroneKind.GRENADE || state() == DroneState.FALLING) {
            return ActionResult.PASS;
        }
        if (getWorld().isClient()) {
            return localOwnerCheck.test(this) ? ActionResult.SUCCESS : ActionResult.PASS;
        }
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !serverPlayer.getUuid().equals(ownerUuid)) {
            return ActionResult.PASS;
        }
        return DroneService.recoverGrenadeDrone(serverPlayer, this) ? ActionResult.SUCCESS : ActionResult.CONSUME;
    }

    /**
     * Server: any damage is handed to DroneCombatService, which decides whether it breaks the drone.
     * 服务器：所有伤害交给 DroneCombatService，由其决定是否击毁无人机。
     */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient() || isRemoved() || isInvulnerableTo(source)) {
            return false;
        }
        return DroneCombatService.onDamaged(this, source, amount);
    }

    /** Targetable by crosshair, weapons and projectiles. / 可被准星、武器与投射物选中。 */
    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    /** Never pushed by players. / 不会被玩家推动。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** Players walk through it (not a solid body like a boat). / 玩家可穿过（不像船那样是实体障碍）。 */
    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        double range = RENDER_DISTANCE * getRenderDistanceMultiplier();
        return distance < range * range;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Transient round entity: a reloaded drone must fail closed (no owner/round => discarded on first tick).
        // 临时回合实体：重新载入的无人机必须失效（无主人/回合 => 首个 tick 即被移除）。
        ownerUuid = null;
        roundId = null;
        pilotUuid = null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
