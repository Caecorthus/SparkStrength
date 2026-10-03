package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.item.FlashlightItem;
import annina.sparkstrength.role.attendant.FlashlightBeamGeometry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side state of one player's lit flashlight: the tick pose its ray map was cast from and the per-frame pose.
 * 单个玩家已开启手电筒的客户端状态：射线图所用的 tick 姿态，以及逐帧姿态。
 */
final class TrackedFlashlight implements AutoCloseable {
    /** Recast when the tick pose moves or turns beyond these. / tick 姿态移动或转动超过此阈值时重投射。 */
    private static final double POSITION_EPSILON_SQ = 0.02 * 0.02;
    private static final double DIRECTION_EPSILON_COS = Math.cos(Math.toRadians(0.25));
    /** Doors and blocks change without the holder moving, so recast at least this often. / 门与方块可能自行变化，至少按此间隔重投射。 */
    static final int REFRESH_TICKS = 10;

    final AbstractClientPlayerEntity player;
    final FlashlightRayMap rayMap = new FlashlightRayMap();
    boolean seen;

    private final double[] handOffset = new double[3];
    private boolean rightHand = true;
    private boolean handBlocked;
    private long castTick;
    private double castX;
    private double castY;
    private double castZ;
    private double castDirX;
    private double castDirY;
    private double castDirZ;

    TrackedFlashlight(AbstractClientPlayerEntity player) {
        this.player = player;
    }

    /**
     * Client tick: refreshes the hand side and recasts the ray map from the current (tick) pose when needed.
     * 客户端 tick：刷新持手侧，并在需要时从当前 tick 姿态重投射射线图。
     */
    void tick(FlashlightRayCaster caster, ClientWorld world, boolean cameraAnchored, long tick) {
        rightHand = holdsInRightHand(player);
        Vec3d eye = player.getEyePos();
        Vec3d direction = player.getRotationVec(1.0F);
        double ox = eye.x;
        double oy = eye.y;
        double oz = eye.z;
        if (cameraAnchored) {
            handBlocked = false;
        } else {
            FlashlightBeamGeometry.handOffset(player.bodyYaw, rightHand, handOffset);
            double hx = ox + handOffset[0];
            double hy = oy + handOffset[1];
            double hz = oz + handOffset[2];
            // A hand pushed into a wall would cast from inside it; fall back to the eye instead.
            // 手伸进墙内时会从墙内投射，此时改用眼睛位置。
            handBlocked = caster.isSegmentBlocked(world, ox, oy, oz, hx, hy, hz);
            if (!handBlocked) {
                ox = hx;
                oy = hy;
                oz = hz;
            }
        }
        if (!needsRecast(ox, oy, oz, direction, tick)) {
            return;
        }
        rayMap.recast(caster, world, ox, oy, oz, direction.x, direction.y, direction.z);
        castTick = tick;
        castX = ox;
        castY = oy;
        castZ = oz;
        castDirX = direction.x;
        castDirY = direction.y;
        castDirZ = direction.z;
    }

    /**
     * Render thread: frame-interpolated light. A camera-anchored light sits exactly on the camera and follows its
     * look; others start at the interpolated eye plus the hand offset and follow the interpolated head look.
     * 渲染线程：逐帧插值的光源。相机锚定的光源与相机完全重合并跟随视线；其余从插值眼睛位置加手部偏移出发，跟随插值头部朝向。
     */
    FlashlightLight frameLight(Camera camera, boolean cameraAnchored, float tickDelta) {
        Vec3d origin;
        Vec3d direction;
        if (cameraAnchored) {
            origin = camera.getPos();
            direction = player.getRotationVector(camera.getPitch(), camera.getYaw());
        } else {
            Vec3d eye = player.getCameraPosVec(tickDelta);
            if (handBlocked) {
                origin = eye;
            } else {
                float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
                FlashlightBeamGeometry.handOffset(bodyYaw, rightHand, handOffset);
                origin = new Vec3d(eye.x + handOffset[0], eye.y + handOffset[1], eye.z + handOffset[2]);
            }
            direction = player.getRotationVec(tickDelta);
        }
        return new FlashlightLight(player.getId(), origin, direction, cameraAnchored,
                rayMap.hasCast() ? rayMap : null);
    }

    @Override
    public void close() {
        rayMap.close();
    }

    private boolean needsRecast(double ox, double oy, double oz, Vec3d direction, long tick) {
        if (!rayMap.hasCast() || tick - castTick >= REFRESH_TICKS) {
            return true;
        }
        double dx = ox - castX;
        double dy = oy - castY;
        double dz = oz - castZ;
        if (dx * dx + dy * dy + dz * dz > POSITION_EPSILON_SQ) {
            return true;
        }
        return direction.x * castDirX + direction.y * castDirY + direction.z * castDirZ < DIRECTION_EPSILON_COS;
    }

    /** The lit flashlight's hand: main hand first, as {@link FlashlightItem#isHeldOn} checks it first. / 已开启手电所在手。 */
    private static boolean holdsInRightHand(AbstractClientPlayerEntity player) {
        Arm mainArm = player.getMainArm();
        Arm arm = FlashlightItem.isOn(player.getMainHandStack()) ? mainArm : mainArm.getOpposite();
        return arm == Arm.RIGHT;
    }
}
