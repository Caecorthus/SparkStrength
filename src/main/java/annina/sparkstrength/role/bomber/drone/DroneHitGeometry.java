package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.item.grenade.GrenadeBlastRules;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Side-neutral hit geometry for drones (mirrors SparkWitch's Seeker-device rules): nearest-wins rays clipped at the
 * first COLLIDER block, sample-point line of sight, gun aim validation and blast spheres. The client uses it to pick a
 * drone target; the server uses it to validate client picks and to resolve its own rays.
 * 无人机命中几何（两端通用，对应 SparkWitch 搜寻者设备规则）：在第一个 COLLIDER 方块处截断的“最近者命中”射线、
 * 采样点视线、枪械瞄准校验与爆炸球。客户端用它选择无人机目标，服务端用它校验客户端选择并结算自身射线。
 */
public final class DroneHitGeometry {
    private static final double SEARCH_PADDING = 1.0;
    private static final double SAMPLE_INSET = 0.01;
    private static final double LOS_EPSILON = 1.0E-4;

    private DroneHitGeometry() {
    }

    /** A drone met by a segment: entry point and squared distance from the start. / 线段命中的无人机：入射点与平方距离。 */
    public record DroneHit(DroneEntity drone, Vec3d point, double distanceSquared) {
    }

    public static boolean isLive(@Nullable DroneEntity drone) {
        return drone != null && drone.isAlive() && !drone.isRemoved();
    }

    /** Drone box grown by the shared targeting margin. / 按统一瞄准余量扩大的无人机箱体。 */
    public static Box targetBox(DroneEntity drone) {
        return drone.getBoundingBox().expand(DroneWeaponRules.TARGET_MARGIN);
    }

    public static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    public static Optional<Vec3d> entryPoint(Vec3d start, Vec3d end, Box box) {
        return box.contains(start) ? Optional.of(start) : box.raycast(start, end);
    }

    /** Squared distance to where the segment enters {@code box}; -1 on a miss. / 线段进入箱体处的平方距离，未命中为 -1。 */
    public static double entryDistanceSquared(Vec3d start, Vec3d end, Box box) {
        return entryPoint(start, end, box).map(start::squaredDistanceTo).orElse(-1.0);
    }

    /** Ray entry into a target's box, else its closest point. / 射线进入目标箱体处，未相交时取最近点。 */
    public static double targetDistanceSquared(Vec3d start, Vec3d end, Box targetBox) {
        double entry = entryDistanceSquared(start, end, targetBox);
        return entry >= 0.0 ? entry : squaredDistanceToBox(start, targetBox);
    }

    /** Cuts {@code end} at the first COLLIDER block. / 在第一个 COLLIDER 方块处截断终点。 */
    public static Vec3d clipToBlocks(World world, Vec3d start, Vec3d end, @Nullable Entity context) {
        HitResult block = world.raycast(raycastContext(start, end, context));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    /** No COLLIDER block between the two points. / 两点之间没有 COLLIDER 方块。 */
    public static boolean segmentClear(World world, Vec3d from, Vec3d to, @Nullable Entity context) {
        if (from.squaredDistanceTo(to) < LOS_EPSILON * LOS_EPSILON) {
            return true;
        }
        HitResult hit = world.raycast(raycastContext(from, to, context));
        return hit.getType() == HitResult.Type.MISS
                || from.squaredDistanceTo(hit.getPos()) >= from.squaredDistanceTo(to) - LOS_EPSILON;
    }

    /** Some sample point of {@code box} is visible from {@code from}. / 能从 from 看到箱体的任一采样点。 */
    public static boolean hasLineOfSight(World world, Vec3d from, Box box, @Nullable Entity context) {
        for (Vec3d point : samplePoints(box)) {
            if (segmentClear(world, from, point, context)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Server gun validation for a client-picked drone: the look ray meets the grown box with a clear segment to the
     * device itself, or (latency fallback) a sample point lies inside the aim cone and some sample point is visible.
     * 客户端选中无人机时的服务端枪械校验：视线射线与扩大箱体相交且到无人机本体的线段无遮挡；或（延迟兜底）有采样点位于瞄准锥内
     * 且有采样点可见。
     */
    public static boolean gunAimedAndVisible(World world, Vec3d eye, Vec3d look, double maxDistance, DroneEntity drone,
                                             @Nullable Entity context) {
        Box box = drone.getBoundingBox();
        if (isFinite(look) && look.lengthSquared() > 0.0) {
            Vec3d end = eye.add(look.normalize().multiply(maxDistance));
            Optional<Vec3d> point = entryPoint(eye, end, box);
            if (point.isEmpty()) {
                Box in = insetBox(box);
                point = entryPoint(eye, end, targetBox(drone)).map(hit -> new Vec3d(
                        MathHelper.clamp(hit.x, in.minX, in.maxX),
                        MathHelper.clamp(hit.y, in.minY, in.maxY),
                        MathHelper.clamp(hit.z, in.minZ, in.maxZ)));
            }
            if (point.isPresent() && segmentClear(world, eye, point.get(), context)) {
                return true;
            }
        }
        double minCosine = Math.cos(Math.toRadians(DroneWeaponRules.GUN_AIM_CONE_DEGREES)) - 1.0E-9;
        boolean aimed = false;
        for (Vec3d point : samplePoints(box)) {
            Vec3d toPoint = point.subtract(eye);
            double lengths = look.length() * toPoint.length();
            if (lengths > 0.0 && look.dotProduct(toPoint) / lengths >= minCosine) {
                aimed = true;
                break;
            }
        }
        return aimed && hasLineOfSight(world, eye, box, context);
    }

    /**
     * Nearest live eligible drone whose grown box meets {@code start → end} strictly nearer than
     * {@code beatDistanceSquared} (the caller clips the segment to blocks).
     * 扩大箱体与线段相交、且严格近于 beatDistanceSquared 的最近存活合格无人机（调用方负责按方块截断）。
     */
    @Nullable
    public static DroneHit nearestDrone(World world, Vec3d start, Vec3d end, double beatDistanceSquared,
                                        Predicate<DroneEntity> eligible) {
        List<DroneEntity> drones = world.getEntitiesByClass(DroneEntity.class,
                new Box(start, end).expand(SEARCH_PADDING), drone -> isLive(drone) && eligible.test(drone));
        DroneEntity selected = null;
        double closest = beatDistanceSquared;
        for (DroneEntity drone : drones) {
            double distance = entryDistanceSquared(start, end, targetBox(drone));
            if (DroneWeaponRules.droneWins(distance, closest)) {
                closest = distance;
                selected = drone;
            }
        }
        if (selected == null) {
            return null;
        }
        Vec3d point = entryPoint(start, end, targetBox(selected)).orElse(start);
        return new DroneHit(selected, point, start.squaredDistanceTo(point));
    }

    /**
     * Client target wrappers: keep {@code original} unless a drone lies strictly nearer on the shooter's look ray (the
     * ray length is the original pick's distance, so extended ranges such as SparkTraits Marksman are kept; {@code range}
     * is only the fallback). Uses Wathe's own aim ({@code getRotationVec(0)}).
     * 客户端目标包装：除非射手视线上有严格更近的无人机，否则保留原结果（射线长度取原选择的距离，自动保留 SparkTraits 神射手等延长射程，
     * range 仅作兜底）。朝向与 Wathe 相同（getRotationVec(0)）。
     */
    public static HitResult preferNearerDrone(Entity shooter, HitResult original, double range) {
        if (shooter == null || original == null) {
            return original;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(0.0F);
        double limit = referenceDistance(start, look, original, range);
        if (!(limit > 0.0) || !Double.isFinite(limit)) {
            return original;
        }
        Vec3d end = clipToBlocks(shooter.getWorld(), start, start.add(look.multiply(limit)), shooter);
        DroneHit hit = nearestDrone(shooter.getWorld(), start, end, limit * limit, drone -> true);
        return hit == null ? original : new EntityHitResult(hit.drone(), hit.point());
    }

    /** Blast sphere on centres (closed), then line of sight from the centre. / 以中心计算的闭球判定，再检查爆心视线。 */
    public static boolean caughtInBlast(World world, Vec3d center, double radius, DroneEntity drone,
                                       @Nullable Entity context) {
        Vec3d droneCenter = drone.getBoundingBox().getCenter();
        return GrenadeBlastRules.containsSphere(droneCenter.x - center.x, droneCenter.y - center.y,
                droneCenter.z - center.z, radius)
                && hasLineOfSight(world, center, drone.getBoundingBox(), context);
    }

    /**
     * Wathe's EntityHitResult carries the target's feet position, so measure where the look ray enters its box.
     * Wathe 的 EntityHitResult 位置为目标脚下，因此改量视线进入其箱体处的距离。
     */
    private static double referenceDistance(Vec3d start, Vec3d look, HitResult original, double range) {
        if (original instanceof EntityHitResult entityHit) {
            Box box = entityHit.getEntity().getBoundingBox();
            double reach = Math.max(range, Math.sqrt(squaredDistanceToBox(start, box)) + 2.0);
            double entry = entryDistanceSquared(start, start.add(look.multiply(reach)), box);
            return Math.sqrt(entry >= 0.0 ? entry : squaredDistanceToBox(start, box));
        }
        Vec3d position = original.getPos();
        if (position != null) {
            double distance = start.distanceTo(position);
            if (Double.isFinite(distance) && distance > 0.0) {
                return distance;
            }
        }
        return range;
    }

    /** Centre, 6 face centres and 8 corners, inset so none touches a block face. / 中心、6 个面中心与 8 个角，均内缩。 */
    private static List<Vec3d> samplePoints(Box box) {
        Box in = insetBox(box);
        Vec3d c = box.getCenter();
        return List.of(
                c,
                new Vec3d(c.x, in.maxY, c.z),
                new Vec3d(c.x, in.minY, c.z),
                new Vec3d(in.minX, c.y, c.z),
                new Vec3d(in.maxX, c.y, c.z),
                new Vec3d(c.x, c.y, in.minZ),
                new Vec3d(c.x, c.y, in.maxZ),
                new Vec3d(in.minX, in.maxY, in.minZ),
                new Vec3d(in.maxX, in.maxY, in.minZ),
                new Vec3d(in.minX, in.maxY, in.maxZ),
                new Vec3d(in.maxX, in.maxY, in.maxZ),
                new Vec3d(in.minX, in.minY, in.minZ),
                new Vec3d(in.maxX, in.minY, in.minZ),
                new Vec3d(in.minX, in.minY, in.maxZ),
                new Vec3d(in.maxX, in.minY, in.maxZ));
    }

    private static Box insetBox(Box box) {
        double inset = Math.min(SAMPLE_INSET,
                Math.min(box.getLengthX(), Math.min(box.getLengthY(), box.getLengthZ())) / 2.0);
        return box.expand(-inset);
    }

    private static RaycastContext raycastContext(Vec3d from, Vec3d to, @Nullable Entity context) {
        return context == null
                ? new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                ShapeContext.absent())
                : new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                context);
    }

    private static boolean isFinite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
