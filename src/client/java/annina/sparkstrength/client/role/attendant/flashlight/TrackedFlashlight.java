package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.role.attendant.FlashlightBeamRules;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
    /**
     * Lights farther than this from the camera recast at most every {@link #FAR_RECAST_TICKS} ticks: their pattern
     * covers few pixels, and a moving holder would otherwise cost a full grid every tick.
     * 距相机超过此距离的光源最多每 FAR_RECAST_TICKS tick 重投射一次：其光斑只占少量像素，否则移动中的持有者每 tick 都要
     * 投射整张网格。
     */
    static final double FAR_LIGHT_DISTANCE = 32.0;
    static final int FAR_RECAST_TICKS = 3;
    /** A block change within this distance of the cast origin forces a recast. / 投射原点此距离内的方块变化会强制重投射。 */
    private static final double BLOCK_CHANGE_REACH_SQ = (FlashlightBeamRules.RANGE_BLOCKS + 1.0)
            * (FlashlightBeamRules.RANGE_BLOCKS + 1.0);
    /** Block entities are sampled this far outside their own shape, on the viewer's side. / 方块实体在观看者一侧、自身形状外此距离处采样。 */
    private static final double BLOCK_ENTITY_SAMPLE_MARGIN = 0.05;
    private static final byte UNKNOWN = -1;

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
    private boolean forceRecast;
    private double pendingDirX;
    private double pendingDirY;
    private double pendingDirZ;

    // Where the beam starts at the latest tick, and the exact line-of-sight answers computed from it this tick.
    // 最近一个 tick 的光束起点，以及本 tick 据此计算的精确视线结果。
    private boolean hasTickOrigin;
    private double tickOriginX;
    private double tickOriginY;
    private double tickOriginZ;
    private long reachTick = Long.MIN_VALUE;
    private final Long2ByteOpenHashMap entityReach = new Long2ByteOpenHashMap();
    private final Long2ByteOpenHashMap blockEntityReach = new Long2ByteOpenHashMap();

    TrackedFlashlight(AbstractClientPlayerEntity player) {
        this.player = player;
        entityReach.defaultReturnValue(UNKNOWN);
        blockEntityReach.defaultReturnValue(UNKNOWN);
    }

    /**
     * Client tick, first pass: refreshes where the beam starts and returns whether the ray map wants a recast from
     * this pose (FlashlightLights decides which wanted recasts fit this tick's budget).
     * 客户端 tick 第一步：刷新光束起点，并返回射线图是否需要按此姿态重投射（由 FlashlightLights 决定本 tick 预算内执行哪些）。
     */
    boolean updatePose(FlashlightRayCaster caster, ClientWorld world, boolean cameraAnchored, long tick,
                       double cameraDistanceSq) {
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
        hasTickOrigin = true;
        tickOriginX = ox;
        tickOriginY = oy;
        tickOriginZ = oz;
        pendingDirX = direction.x;
        pendingDirY = direction.y;
        pendingDirZ = direction.z;
        int minInterval = cameraDistanceSq > FAR_LIGHT_DISTANCE * FAR_LIGHT_DISTANCE ? FAR_RECAST_TICKS : 1;
        return needsRecast(ox, oy, oz, direction, tick, minInterval);
    }

    /** Second pass: recasts the ray map from the pose of {@link #updatePose}. / 第二步：按 updatePose 的姿态重投射。 */
    void recast(FlashlightRayCaster caster, ClientWorld world, long tick) {
        rayMap.recast(caster, world, tickOriginX, tickOriginY, tickOriginZ, pendingDirX, pendingDirY, pendingDirZ);
        forceRecast = false;
        castTick = tick;
        castX = tickOriginX;
        castY = tickOriginY;
        castZ = tickOriginZ;
        castDirX = pendingDirX;
        castDirY = pendingDirY;
        castDirZ = pendingDirZ;
    }

    /** Tick of the last recast (oldest first gets the budget); MIN_VALUE before the first. / 上次重投射的 tick。 */
    long castTick() {
        return rayMap.hasCast() ? castTick : Long.MIN_VALUE;
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

    /**
     * Exact line of sight from this tick's beam origin to one height sample of the entity (see
     * {@link FlashlightLights#ENTITY_SAMPLE_HEIGHTS}) on its bounding-box axis, cached per sample for the tick.
     * 从本 tick 光束起点到实体碰撞箱中轴上某个高度采样点的精确视线，按采样点在本 tick 内缓存。
     */
    boolean reachesEntity(FlashlightRayCaster caster, ClientWorld world, Entity entity, int sample, double heightFraction,
                          long tick) {
        if (!hasTickOrigin) {
            return false;
        }
        syncReachTick(tick);
        long key = ((long) entity.getId() << 2) | sample;
        byte cached = entityReach.get(key);
        if (cached != UNKNOWN) {
            return cached != 0;
        }
        Box box = entity.getBoundingBox();
        boolean reaches = !caster.isSegmentBlocked(world, tickOriginX, tickOriginY, tickOriginZ,
                (box.minX + box.maxX) * 0.5, box.minY + (box.maxY - box.minY) * heightFraction,
                (box.minZ + box.maxZ) * 0.5);
        entityReach.put(key, reaches ? (byte) 1 : (byte) 0);
        return reaches;
    }

    /**
     * Exact line of sight to a block entity, sampled just outside its own collision shape on the side facing the
     * viewer: its renderer uses one light for every face, so a closed door must look dark from the unlit side.
     * 到方块实体的精确视线，在其自身碰撞箱外、朝向观看者的一侧采样：其渲染器所有面共用一个光照，
     * 因此关闭的门从背光一侧看必须是暗的。
     */
    boolean reachesBlockEntity(FlashlightRayCaster caster, ClientWorld world, BlockPos pos, Vec3d viewer, long tick) {
        if (!hasTickOrigin) {
            return false;
        }
        syncReachTick(tick);
        long key = pos.asLong();
        byte cached = blockEntityReach.get(key);
        if (cached != UNKNOWN) {
            return cached != 0;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double vx = viewer.x - cx;
        double vy = viewer.y - cy;
        double vz = viewer.z - cz;
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length > 1.0e-3) {
            vx /= length;
            vy /= length;
            vz /= length;
            double pull = Math.min(length, caster.ownShapeExit(world, pos, vx, vy, vz) + BLOCK_ENTITY_SAMPLE_MARGIN);
            cx += vx * pull;
            cy += vy * pull;
            cz += vz * pull;
        }
        boolean reaches = !caster.isSegmentBlocked(world, tickOriginX, tickOriginY, tickOriginZ, cx, cy, cz);
        blockEntityReach.put(key, reaches ? (byte) 1 : (byte) 0);
        return reaches;
    }

    /** A block changed: recast next tick if it can matter to this beam. / 方块变化：若可能影响本光束则下一 tick 重投射。 */
    void onBlockChanged(BlockPos pos) {
        if (!rayMap.hasCast()) {
            return;
        }
        double dx = pos.getX() + 0.5 - castX;
        double dy = pos.getY() + 0.5 - castY;
        double dz = pos.getZ() + 0.5 - castZ;
        if (dx * dx + dy * dy + dz * dz <= BLOCK_CHANGE_REACH_SQ) {
            forceRecast = true;
            reachTick = Long.MIN_VALUE;
        }
    }

    private void syncReachTick(long tick) {
        if (reachTick != tick) {
            reachTick = tick;
            entityReach.clear();
            blockEntityReach.clear();
        }
    }

    private boolean needsRecast(double ox, double oy, double oz, Vec3d direction, long tick, int minInterval) {
        if (!rayMap.hasCast() || forceRecast || tick - castTick >= REFRESH_TICKS) {
            return true;
        }
        if (tick - castTick < minInterval) {
            return false;
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
