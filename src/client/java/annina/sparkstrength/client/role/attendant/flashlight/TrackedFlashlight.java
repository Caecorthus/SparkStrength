package annina.sparkstrength.client.role.attendant.flashlight;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
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

    private final double[] scratch = new double[3];
    /** Not aiming, or the lens sits inside a block: the beam starts at the eye. / 未瞄准或镜片在方块内：光束从眼睛出发。 */
    private boolean fromEye;
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
     * Client tick: refreshes where the beam starts and recasts the ray map from the current (tick) pose when needed.
     * 客户端 tick：刷新光束起点，并在需要时从当前 tick 姿态重投射射线图。
     */
    void tick(FlashlightRayCaster caster, ClientWorld world, boolean cameraAnchored, long tick) {
        Vec3d eye = player.getEyePos();
        Vec3d direction;
        double ox = eye.x;
        double oy = eye.y;
        double oz = eye.z;
        if (cameraAnchored) {
            fromEye = true;
            direction = player.getRotationVec(1.0F);
        } else {
            direction = FlashlightAim.lookDirection(player, 1.0F);
            fromEye = true;
            if (FlashlightAim.canAim(player)) {
                Vec3d lens = FlashlightAim.lensPosition(player, 1.0F, scratch);
                // A lens pushed into a wall or under a counter would cast from inside it; use the eye instead.
                // 镜片伸进墙内或桌下时会从方块内投射，此时改用眼睛位置。
                fromEye = caster.isSegmentBlocked(world, eye.x, eye.y, eye.z, lens.x, lens.y, lens.z);
                if (!fromEye) {
                    ox = lens.x;
                    oy = lens.y;
                    oz = lens.z;
                }
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
     * look; others start at the lens of the aimed third-person model (or the eye) and follow the head look.
     * 渲染线程：逐帧插值的光源。相机锚定的光源与相机完全重合并跟随视线；其余从第三人称瞄准模型的镜片（或眼睛）出发，
     * 跟随头部朝向。
     */
    FlashlightLight frameLight(Camera camera, boolean cameraAnchored, float tickDelta) {
        Vec3d origin;
        Vec3d direction;
        if (cameraAnchored) {
            origin = camera.getPos();
            direction = player.getRotationVector(camera.getPitch(), camera.getYaw());
        } else {
            origin = fromEye || !FlashlightAim.canAim(player)
                    ? player.getCameraPosVec(tickDelta)
                    : FlashlightAim.lensPosition(player, tickDelta, scratch);
            direction = FlashlightAim.lookDirection(player, tickDelta);
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
}
