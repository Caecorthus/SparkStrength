package annina.sparkstrength.client.role.bomber;

import annina.sparkstrength.client.mixin.m67.M67KeyBindingAccessor;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.network.drone.DronePilotActionC2SPacket;
import annina.sparkstrength.network.drone.DronePilotCorrectionS2CPacket;
import annina.sparkstrength.network.drone.DronePilotExitC2SPacket;
import annina.sparkstrength.network.drone.DronePilotMoveC2SPacket;
import annina.sparkstrength.network.drone.DronePilotStateS2CPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.bomber.drone.DronePilotEndReason;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import annina.sparkstrength.role.bomber.drone.DroneState;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Pilot mode on the pilot's own client: camera switch, input capture, flight prediction and the session lifecycle.
 * 驾驶者客户端的驾驶模式：镜头切换、输入接管、飞行预测与会话生命周期。
 *
 * <p>Authority: the server owns the session ({@link DronePilotStateS2CPacket}) and validates every move/action; this
 * client only predicts the drone it pilots and may end its own view early (drone gone, body dead, camera taken), in
 * which case it tells the server with {@link DronePilotExitC2SPacket} (never the blockable FIRE action packet). The
 * camera switch is client-only and never touches the server's camera entity.
 * 权威性：会话由服务器掌控（DronePilotStateS2CPacket），每次移动/动作都由服务器校验；本客户端只预测自己驾驶的无人机，
 * 并可在本地提前结束画面（无人机消失、本体死亡、镜头被接管），此时以 DronePilotExitC2SPacket（而非可被拦截的开火动作包）通知服务器。镜头切换只发生在客户端，
 * 不会改动服务器端的镜头实体。</p>
 *
 * <p>Range: the server re-centres the pilot's chunk view on the drone and force-tracks it, so a far drone may
 * reappear as a new entity instance with the same id; the camera is therefore re-bound by id every tick.
 * 范围：服务器把驾驶者的区块视野重新以无人机为中心并强制追踪，远处的无人机可能以同 id 的新实体实例重新出现，因此每刻按 id 重新绑定镜头。</p>
 */
public final class DronePilotClient {
    /** Ticks the drone may be missing before the session is dropped locally. / 无人机缺失多少刻后在本地结束会话。 */
    private static final int MISSING_GRACE_TICKS = 40;
    /** Camera pitch limit while piloting. / 驾驶时镜头俯仰角上限。 */
    private static final float MAX_PITCH = 89.0F;
    /** Share of the gap to the target velocity closed per tick (smooth start/stop, never above top speed). / 每刻向目标速度靠近的比例（平滑起停，不超过最高速度）。 */
    private static final double ACCELERATION = 0.5;
    private static final double STOP_EPSILON_SQUARED = 1.0E-6;
    /** Hull probe below the drone for "landed" (horizontal input needs lift-off first). / 机身下方“着陆”探测（需先起飞才能水平移动）。 */
    private static final double GROUND_PROBE = 0.05;
    /** Altitude readout / drop marker search depth. / 高度读数与投弹点的向下探测深度。 */
    private static final double GROUND_SCAN_DEPTH = 96.0;

    // Session (main thread; droneId is also read by DroneEntity.localPilotCheck). / 会话（主线程；droneId 也被 localPilotCheck 读取）。
    private static volatile int droneId = -1;
    private static @Nullable DroneKind sessionKind;
    private static @Nullable DroneEntity drone;
    private static boolean everBound;
    private static int missingTicks;
    private static @Nullable ClientPlayerEntity sessionPlayer;
    private static @Nullable ClientWorld sessionWorld;
    private static boolean savedChunkCulling = true;

    // Flight prediction. / 飞行预测。
    private static Vec3d flightVelocity = Vec3d.ZERO;
    private static boolean flightMoving;
    private static boolean flightLanded;
    private static boolean fireQueued;
    private static boolean exitQueued;

    // HUD telemetry (per tick). / HUD 遥测（每刻更新）。
    private static double groundY = Double.NaN;
    private static boolean lastPayload;
    private static long bootStartedMs;
    private static long feedbackUntilMs;
    private static @Nullable Text feedbackText;
    private static int feedbackColor;

    private DronePilotClient() {
    }

    public static void register() {
        // Tracker moves are ignored only for the drone this client pilots (by id: a re-tracked drone is a new instance).
        // 只有本客户端正在驾驶的无人机忽略追踪位移（按 id 比较：重新追踪的无人机是新实例）。
        DroneEntity.localPilotCheck = d -> droneId >= 0 && d.getId() == droneId;
        // Owner's own drones are known only from its tablet snapshot rows (by id); used for a local right-click swing.
        // 主人自己的无人机只能从平板快照行按 id 得知；用于本地右键挥手。
        DroneEntity.localOwnerCheck = DronePilotClient::ownedByLocalPlayer;

        ClientPlayNetworking.registerGlobalReceiver(DronePilotStateS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> onState(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(DronePilotCorrectionS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> onCorrection(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(DronePilotClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset(client));
        WorldRenderEvents.END.register(DronePilotHud::captureProjection);
    }

    private static boolean ownedByLocalPlayer(DroneEntity candidate) {
        for (TabletSnapshot.DroneRow row : TabletClientState.snapshot().drones()) {
            if (row.entityId() == candidate.getId()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** A pilot session is active (also while connecting / signal lost). / 驾驶会话进行中（含连接中与信号丢失）。 */
    public static boolean isPiloting() {
        return droneId >= 0;
    }

    /** The camera currently looks through a drone. / 镜头当前正通过无人机观看。 */
    public static boolean isViewingDrone() {
        return droneId >= 0 && drone != null && MinecraftClient.getInstance().getCameraEntity() == drone;
    }

    static @Nullable DroneEntity drone() {
        return droneId >= 0 ? drone : null;
    }

    static @Nullable DroneKind kind() {
        DroneEntity bound = drone;
        return bound != null ? bound.kind() : sessionKind;
    }

    static boolean connecting() {
        return droneId >= 0 && !everBound;
    }

    static boolean signalLost() {
        return droneId >= 0 && everBound && missingTicks > 0;
    }

    static boolean flightMoving() {
        return flightMoving;
    }

    static boolean landed() {
        return flightLanded;
    }

    static double groundY() {
        return groundY;
    }

    static long bootStartedMs() {
        return bootStartedMs;
    }

    static @Nullable Text feedbackText(long now) {
        return now < feedbackUntilMs ? feedbackText : null;
    }

    static float feedbackAlpha(long now) {
        long left = feedbackUntilMs - now;
        return left <= 0 ? 0.0F : Math.min(1.0F, left / 300.0F);
    }

    static int feedbackColor() {
        return feedbackColor;
    }

    /** HUD flight state (local prediction, so it reacts before the tracked state arrives). / HUD 飞行状态（本地预测，先于追踪状态响应）。 */
    static DroneState displayState(DroneEntity bound) {
        if (bound.state() == DroneState.FALLING || bound.charge() <= 0) {
            return DroneState.FALLING;
        }
        if (flightMoving) {
            return DroneState.FLYING;
        }
        return flightLanded ? DroneState.GROUNDED : DroneState.HOVERING;
    }

    // ------------------------------------------------------------------------------------------------ mixin hooks

    /**
     * Mouse look while piloting turns the drone, never the body. Returns true when the look delta was consumed.
     * 驾驶时鼠标视角转动无人机而非本体。返回 true 表示视角增量已被接管。
     */
    public static boolean steer(double cursorDeltaX, double cursorDeltaY) {
        if (droneId < 0) {
            return false;
        }
        DroneEntity bound = drone;
        if (bound != null) {
            bound.changeLookDirection(cursorDeltaX, cursorDeltaY);
            bound.setPitch(MathHelper.clamp(bound.getPitch(), -MAX_PITCH, MAX_PITCH));
            bound.prevPitch = MathHelper.clamp(bound.prevPitch, -MAX_PITCH, MAX_PITCH);
        }
        return true;
    }

    /**
     * Runs at the head of {@code handleInputEvents}: turns queued attack/use clicks into fire/exit and swallows every
     * body action (hotbar keys, pick, drop, swap, inventory) before vanilla can act on them.
     * 在 handleInputEvents 开头执行：把排队的攻击/使用点击转为开火/退出，并在原版处理前吞掉所有本体操作（快捷栏、选取、丢弃、换手、背包）。
     */
    public static void captureInput(MinecraftClient client) {
        if (droneId < 0) {
            return;
        }
        GameOptions options = client.options;
        while (options.attackKey.wasPressed()) {
            fireQueued = true;
        }
        while (options.useKey.wasPressed()) {
            exitQueued = true;
        }
        drain(options.pickItemKey);
        drain(options.dropKey);
        drain(options.swapHandsKey);
        drain(options.inventoryKey);
        for (KeyBinding hotbarKey : options.hotbarKeys) {
            drain(hotbarKey);
        }
    }

    /** Body actions (attack/use/pick/block breaking/hotbar scroll) are blocked while piloting. / 驾驶时屏蔽本体动作。 */
    public static boolean blocksBodyActions() {
        return droneId >= 0;
    }

    /**
     * Right after the body's {@code Input.tick}: zero all movement so the body stands still (no walk, jump, crouch or
     * sprint). The pilot's WASD/space/sneak are read raw for the drone instead. Not being the camera, the body also
     * skips {@code tickNewAi}, so its stale walk speeds are cleared here too.
     * 紧接本体的 Input.tick：清零全部移动，让本体静止（不走、不跳、不蹲、不疾跑）。WASD/空格/潜行改为原始读取供无人机使用。
     * 本体不是镜头实体时不会执行 tickNewAi，因此这里同时清除残留的行走速度。
     */
    public static void holdBodyStill(ClientPlayerEntity player) {
        if (droneId < 0 || player != MinecraftClient.getInstance().player) {
            return;
        }
        if (player.input != null) {
            player.input.movementForward = 0.0F;
            player.input.movementSideways = 0.0F;
            player.input.pressingForward = false;
            player.input.pressingBack = false;
            player.input.pressingLeft = false;
            player.input.pressingRight = false;
            player.input.jumping = false;
            player.input.sneaking = false;
        }
        player.forwardSpeed = 0.0F;
        player.sidewaysSpeed = 0.0F;
        player.upwardSpeed = 0.0F;
        player.setJumping(false);
    }

    /** Wathe's killer instinct key reads as released while piloting. / 驾驶时 Wathe 杀手本能键视为未按下。 */
    public static boolean blocksInstinctKey(KeyBinding key) {
        return droneId >= 0 && key == WatheClient.instinctKeybind;
    }

    /**
     * Crosshair target while piloting: always a miss, so nothing near the drone can be attacked, used, outlined or
     * targeted by add-on abilities from the drone's point of view.
     * 驾驶时准星目标恒为未命中，避免以无人机视角攻击、使用、描边或被附属模组技能选中附近目标。
     */
    public static void clearCrosshairTarget(MinecraftClient client) {
        if (droneId < 0) {
            return;
        }
        Entity camera = client.getCameraEntity();
        Vec3d at = camera != null ? camera.getEyePos() : Vec3d.ZERO;
        client.crosshairTarget = BlockHitResult.createMissed(at, Direction.UP, BlockPos.ofFloored(at));
        client.targetedEntity = null;
    }

    /**
     * Render-grid centre axis for {@code WorldRenderer.setupTerrain}: the camera while it looks through the drone,
     * otherwise vanilla's player position. Axis 0/1/2 = x/y/z.
     * WorldRenderer.setupTerrain 的渲染网格中心坐标：镜头通过无人机观看时取镜头位置，否则保持原版的玩家位置。axis 0/1/2 = x/y/z。
     */
    public static double renderGridCenter(double original, Camera camera, int axis) {
        if (!isViewingDrone()) {
            return original;
        }
        Vec3d pos = camera.getPos();
        return axis == 0 ? pos.x : axis == 1 ? pos.y : pos.z;
    }

    /**
     * A vanilla SetCameraEntity packet is about to move the camera (another mod or the server took it): leave pilot
     * mode without restoring the camera, and tell the server.
     * 原版 SetCameraEntity 包即将移动镜头（其他模组或服务器接管了镜头）：退出驾驶模式但不恢复镜头，并通知服务器。
     */
    public static void onServerCameraOverride(MinecraftClient client) {
        if (droneId >= 0) {
            end(client, null, true, false);
        }
    }

    // ------------------------------------------------------------------------------------------------ packets

    private static void onState(MinecraftClient client, DronePilotStateS2CPacket payload) {
        if (payload.active()) {
            begin(client, payload.entityId(), payload.kindWire());
            return;
        }
        DronePilotEndReason reason = DronePilotEndReason.fromWire(payload.reason());
        // Refusals (no active session) still show their reason. / 拒绝（无会话）时也显示原因。
        Text message = reason == DronePilotEndReason.EXIT ? null
                : Text.translatable(reason.translationKey()).formatted(Formatting.RED);
        if (droneId >= 0) {
            end(client, message, false, true);
        } else if (message != null) {
            client.inGameHud.setOverlayMessage(message, false);
        }
    }

    private static void onCorrection(DronePilotCorrectionS2CPacket payload) {
        DroneEntity bound = drone;
        if (droneId < 0 || bound == null || bound.getId() != payload.entityId() || bound.isRemoved()) {
            return;
        }
        // Snap; the next prediction step interpolates from here. / 直接对齐；下一步预测从此处插值。
        bound.setPosition(payload.x(), payload.y(), payload.z());
        flightVelocity = Vec3d.ZERO;
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    private static void begin(MinecraftClient client, int entityId, byte kindWire) {
        if (client.player == null || client.world == null) {
            return;
        }
        boolean fresh = droneId < 0;
        if (fresh) {
            savedChunkCulling = client.chunkCullingEnabled;
        }
        if (fresh || droneId != entityId) {
            drone = null;
            everBound = false;
        }
        droneId = entityId;
        sessionKind = DroneKind.fromWire(kindWire);
        sessionPlayer = client.player;
        sessionWorld = client.world;
        missingTicks = 0;
        flightVelocity = Vec3d.ZERO;
        flightMoving = false;
        fireQueued = false;
        exitQueued = false;
        feedbackUntilMs = 0L;
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        holdBodyStill(client.player);
        bindCamera(client);
    }

    /**
     * End the local view. {@code notifyServer} sends the exit packet (the client dropped the session on its own);
     * {@code restoreCamera} is false only when a vanilla camera packet is about to set the camera itself.
     * 结束本地画面。notifyServer 表示客户端自行结束并发送退出包；仅当原版镜头包即将自行设置镜头时 restoreCamera 为 false。
     */
    private static void end(MinecraftClient client, @Nullable Text message, boolean notifyServer, boolean restoreCamera) {
        int id = droneId;
        if (id < 0) {
            return;
        }
        if (notifyServer && ClientPlayNetworking.canSend(DronePilotExitC2SPacket.ID)) {
            ClientPlayNetworking.send(new DronePilotExitC2SPacket(id));
        }
        clearSession();
        if (restoreCamera && client.player != null) {
            Entity camera = client.getCameraEntity();
            if (camera == null || camera instanceof DroneEntity) {
                client.setCameraEntity(client.player);
            }
        }
        client.gameRenderer.setRenderHand(true);
        client.chunkCullingEnabled = savedChunkCulling;
        // Keys held for the drone (sneak, space, right click...) must not leak into the body; toggle-sneak/sprint
        // latched while flying would otherwise stay on. / 为无人机按住的键（潜行、空格、右键……）不能漏给本体；
        // 飞行时锁定的切换式潜行/疾跑也要解除，否则会一直保持。
        KeyBinding.unpressAll();
        KeyBinding.untoggleStickyKeys();
        if (message != null) {
            client.inGameHud.setOverlayMessage(message, false);
        }
    }

    /** Disconnect / join: drop everything without touching a world that is being torn down. / 断开/加入：清空状态，不触碰正在销毁的世界。 */
    private static void reset(MinecraftClient client) {
        if (droneId < 0) {
            return;
        }
        clearSession();
        client.gameRenderer.setRenderHand(true);
        client.chunkCullingEnabled = savedChunkCulling;
    }

    private static void clearSession() {
        droneId = -1;
        drone = null;
        sessionKind = null;
        everBound = false;
        missingTicks = 0;
        sessionPlayer = null;
        sessionWorld = null;
        flightVelocity = Vec3d.ZERO;
        flightMoving = false;
        flightLanded = false;
        fireQueued = false;
        exitQueued = false;
        groundY = Double.NaN;
        feedbackUntilMs = 0L;
    }

    private static void tick(MinecraftClient client) {
        if (droneId < 0) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || player != sessionPlayer || client.world != sessionWorld) {
            // Respawn / dimension change: the server already knows. / 重生或换维度：服务器已知晓。
            end(client, null, false, true);
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            end(client, null, false, true);
            return;
        }
        if (exitQueued) {
            end(client, null, true, true);
            return;
        }
        if (!bindCamera(client)) {
            // Stale clicks must not fire after a reconnect. / 重连后不能补发旧的点击。
            fireQueued = false;
            if (++missingTicks > MISSING_GRACE_TICKS) {
                end(client, Text.translatable("hud.sparkstrength.drone.link_lost").formatted(Formatting.RED), true, true);
            }
            return;
        }
        missingTicks = 0;
        DroneEntity bound = drone;
        if (bound == null) {
            return;
        }
        fly(client, bound);
        if (fireQueued) {
            fireQueued = false;
            fire(bound);
        }
        if (lastPayload && !bound.hasPayload()) {
            feedback(Text.translatable("hud.sparkstrength.drone.feedback.released"), DronePilotHud.accent(bound.kind()));
        }
        lastPayload = bound.hasPayload();
    }

    /**
     * Find the drone by id and look through it. Returns false while it is missing (the camera then keeps the last
     * instance, frozen, until the grace period runs out).
     * 按 id 查找无人机并通过它观看。缺失时返回 false（镜头保留最后一个实例画面，直到宽限期结束）。
     */
    private static boolean bindCamera(MinecraftClient client) {
        ClientWorld world = client.world;
        Entity found = world != null ? world.getEntityById(droneId) : null;
        if (!(found instanceof DroneEntity current) || current.isRemoved()) {
            return false;
        }
        DroneEntity previous = drone;
        if (previous != current) {
            if (previous != null) {
                // Re-tracked instance: keep the pilot's look. / 重新追踪的新实例：保留驾驶者的朝向。
                current.setYaw(previous.getYaw());
                current.setPitch(previous.getPitch());
                current.prevYaw = previous.getYaw();
                current.prevPitch = previous.getPitch();
            } else {
                current.setPitch(MathHelper.clamp(current.getPitch(), -MAX_PITCH, MAX_PITCH));
                lastPayload = current.hasPayload();
                bootStartedMs = Util.getMeasuringTimeMs();
            }
            drone = current;
            everBound = true;
        }
        if (client.getCameraEntity() != current) {
            client.setCameraEntity(current);
        }
        client.gameRenderer.setRenderHand(false);
        client.chunkCullingEnabled = false;
        return true;
    }

    // ------------------------------------------------------------------------------------------------ flight

    private static void fly(MinecraftClient client, DroneEntity bound) {
        boolean input = client.currentScreen == null && client.getOverlay() == null;
        GameOptions options = client.options;
        double forward = 0.0;
        double strafe = 0.0;
        double vertical = 0.0;
        if (input) {
            forward = axis(client, options.forwardKey, options.backKey);
            strafe = axis(client, options.leftKey, options.rightKey);
            vertical = axis(client, options.jumpKey, options.sneakKey);
        }
        boolean powered = bound.state() != DroneState.FALLING && bound.charge() > 0;
        if (!powered) {
            forward = strafe = vertical = 0.0;
        }
        boolean supported = isSupported(bound);
        if (supported && vertical <= 0.0) {
            // Landed: lift off before flying sideways (no sliding along the floor). / 已着陆：需先升空才能水平飞行（不贴地滑行）。
            forward = strafe = 0.0;
        }
        DroneKind kind = bound.kind();
        Vec3d target = Vec3d.ZERO;
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 0.0 || vertical != 0.0) {
            double speed = DroneRules.horizontalSpeed(kind);
            double f = length > 0.0 ? forward / length * speed : 0.0;
            double s = length > 0.0 ? strafe / length * speed : 0.0;
            float yawRadians = bound.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            double sin = MathHelper.sin(yawRadians);
            double cos = MathHelper.cos(yawRadians);
            // Same basis as vanilla movementInputToVelocity. / 与原版 movementInputToVelocity 相同的坐标变换。
            target = new Vec3d(s * cos - f * sin, vertical * DroneRules.VERTICAL_SPEED, f * cos + s * sin);
        }
        Vec3d step = flightVelocity.add(target.subtract(flightVelocity).multiply(ACCELERATION));
        if (target.lengthSquared() == 0.0 && step.lengthSquared() < STOP_EPSILON_SQUARED) {
            step = Vec3d.ZERO;
        }
        Vec3d before = bound.getPos();
        bound.clientPilotMove(step, bound.getYaw(), bound.getPitch());
        flightVelocity = bound.getPos().subtract(before);
        flightMoving = powered && (target.lengthSquared() > 0.0 || flightVelocity.lengthSquared() > STOP_EPSILON_SQUARED);
        flightLanded = isSupported(bound);
        groundY = scanGround(bound);
        if (ClientPlayNetworking.canSend(DronePilotMoveC2SPacket.ID)) {
            ClientPlayNetworking.send(new DronePilotMoveC2SPacket(bound.getId(), bound.getX(), bound.getY(), bound.getZ(),
                    MathHelper.wrapDegrees(bound.getYaw()), bound.getPitch(), flightMoving));
        }
    }

    private static void fire(DroneEntity bound) {
        if (bound.kind() == DroneKind.GRENADE && !bound.hasPayload()) {
            feedback(Text.translatable("hud.sparkstrength.drone.feedback.no_payload"), DronePilotHud.WARNING);
            return;
        }
        sendFire(bound.getId());
    }

    private static boolean isSupported(DroneEntity bound) {
        Box hull = bound.getBoundingBox();
        Box below = new Box(hull.minX, hull.minY - GROUND_PROBE, hull.minZ, hull.maxX, hull.minY, hull.maxZ);
        return !bound.getWorld().isBlockSpaceEmpty(bound, below);
    }

    private static double scanGround(DroneEntity bound) {
        Vec3d start = bound.getPos();
        Vec3d end = start.subtract(0.0, GROUND_SCAN_DEPTH, 0.0);
        HitResult hit = bound.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, bound));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getPos().y;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static double axis(MinecraftClient client, KeyBinding positive, KeyBinding negative) {
        return (rawDown(client, positive) ? 1.0 : 0.0) - (rawDown(client, negative) ? 1.0 : 0.0);
    }

    /**
     * Physical key state of a binding, bypassing {@code isPressed()} return-value filters (Wathe suppresses jump in a
     * round, which is the drone's "up").
     * 按键绑定的物理按下状态，绕过 isPressed() 返回值过滤（Wathe 在对局中屏蔽跳跃键，而它是无人机的“上升”）。
     */
    static boolean rawDown(MinecraftClient client, KeyBinding binding) {
        InputUtil.Key key = ((M67KeyBindingAccessor) binding).sparkstrength$m67BoundKey();
        long handle = client.getWindow().getHandle();
        return switch (key.getCategory()) {
            case KEYSYM -> key.getCode() != InputUtil.UNKNOWN_KEY.getCode() && InputUtil.isKeyPressed(handle, key.getCode());
            case MOUSE -> GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
            case SCANCODE -> binding.isPressed();
        };
    }

    private static void drain(KeyBinding binding) {
        while (binding.wasPressed()) {
            // Swallowed: the body is locked while piloting. / 吞掉：驾驶时本体被锁定。
        }
    }

    private static void sendFire(int entityId) {
        if (ClientPlayNetworking.canSend(DronePilotActionC2SPacket.ID)) {
            ClientPlayNetworking.send(new DronePilotActionC2SPacket(entityId, DronePilotActionC2SPacket.FIRE));
        }
    }

    private static void feedback(Text text, int color) {
        feedbackText = text;
        feedbackColor = color;
        feedbackUntilMs = Util.getMeasuringTimeMs() + 1600L;
    }
}
