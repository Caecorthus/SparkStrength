package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Side-neutral hit geometry for Partygoers, the twin of {@link DroneHitGeometry} (whose public ray, sight and block
 * helpers it reuses): nearest-wins rays, piercing sweeps, gun aim validation and blast checks. The client uses it to
 * pick a Partygoer target; the server uses it to validate client picks and to resolve its own rays. Bomber drones on the
 * same ray are measured too, so the nearest of either takes the hit.
 * 派对客命中几何（两端通用），对应 {@link DroneHitGeometry}（复用其公开的射线、视线与方块辅助方法）：“最近者命中”射线、穿透扫掠、
 * 枪械瞄准校验与爆炸判定。客户端用它选择派对客目标，服务端用它校验客户端选择并结算自身射线。同一射线上的炸弹客无人机也会被测量，
 * 由两者中最近者承受命中。
 */
public final class PartygoerHitGeometry {
    private static final double SEARCH_PADDING = 1.0;
    private static final double SAMPLE_INSET = 0.01;

    private PartygoerHitGeometry() {
    }

    /** A Partygoer met by a segment: entry point and squared distance from the start. / 线段命中的派对客。 */
    public record PartygoerHit(PartygoerEntity partygoer, Vec3d point, double distanceSquared) {
    }

    /**
     * Alive, visible and not inside a Taotie (the swallowed state is synced, and it is invisible then); the server also
     * asks its brain ({@link PartygoerService#isKillable}). Works on both sides.
     * 存活、可见且不在饕餮肚里（吞下状态会同步，且此时隐身）；服务端还会询问其大脑。两端可用。
     */
    public static boolean isTargetable(@Nullable Entity entity) {
        if (!(entity instanceof PartygoerEntity partygoer) || !partygoer.isAlive() || partygoer.isRemoved()
                || partygoer.isInvisible() || partygoer.partyState() == PartygoerState.SWALLOWED) {
            return false;
        }
        return partygoer.getWorld().isClient() || PartygoerService.isKillable(partygoer);
    }

    /**
     * Targetable by this attacker: someone carried by a Partygoer can't hit one (null attacker: a thrown or placed
     * weapon whose owner is gone). / 该攻击者可命中：被派对客扛着的人不能打派对客（null 表示主人已不在的投掷或放置武器）。
     */
    public static boolean targetableBy(@Nullable Entity attacker, @Nullable Entity entity) {
        return isTargetable(entity) && !PartygoerCarry.isCarried(attacker);
    }

    /** Box grown by the shared targeting margin. / 按统一瞄准余量扩大的箱体。 */
    public static Box targetBox(PartygoerEntity partygoer) {
        return partygoer.getBoundingBox().expand(PartygoerWeaponRules.TARGET_MARGIN);
    }

    /**
     * Nearest Partygoer the attacker may hit whose grown box meets {@code start → end} strictly nearer than
     * {@code beatDistanceSquared} (the caller clips the segment to blocks).
     * 攻击者可命中、扩大箱体与线段相交且严格近于 beatDistanceSquared 的最近派对客（调用方负责按方块截断）。
     */
    @Nullable
    public static PartygoerHit nearest(World world, Vec3d start, Vec3d end, double beatDistanceSquared,
                                       @Nullable Entity attacker) {
        PartygoerEntity selected = null;
        double closest = beatDistanceSquared;
        for (PartygoerEntity partygoer : candidates(world, start, end, attacker)) {
            double distance = DroneHitGeometry.entryDistanceSquared(start, end, targetBox(partygoer));
            if (distance >= 0.0 && distance < closest) {
                closest = distance;
                selected = partygoer;
            }
        }
        if (selected == null) {
            return null;
        }
        Vec3d point = DroneHitGeometry.entryPoint(start, end, targetBox(selected)).orElse(start);
        return new PartygoerHit(selected, point, start.squaredDistanceTo(point));
    }

    /**
     * Every Partygoer the segment enters strictly before {@code cutSquared}, nearest first (piercing weapons).
     * 线段在 cutSquared 之前严格先进入的所有派对客，按距离由近到远（穿透武器）。
     */
    public static List<PartygoerHit> allBefore(World world, Vec3d start, Vec3d end, double cutSquared,
                                               @Nullable Entity attacker) {
        List<PartygoerHit> hits = new ArrayList<>();
        for (PartygoerEntity partygoer : candidates(world, start, end, attacker)) {
            Optional<Vec3d> point = DroneHitGeometry.entryPoint(start, end, targetBox(partygoer));
            if (point.isPresent()) {
                double distance = start.squaredDistanceTo(point.get());
                if (PartygoerWeaponRules.beforeCut(distance, cutSquared)) {
                    hits.add(new PartygoerHit(partygoer, point.get(), distance));
                }
            }
        }
        hits.sort(Comparator.comparingDouble(PartygoerHit::distanceSquared));
        return hits;
    }

    /**
     * Squared distance to the nearest live Bomber drone on the segment strictly before {@code beatDistanceSquared},
     * else {@code +∞}. Read only: nothing breaks here.
     * 线段上严格近于 beatDistanceSquared 的最近存活炸弹客无人机的平方距离，没有时为 +∞。只读：这里不会击毁任何东西。
     */
    public static double droneDistanceSquared(World world, Vec3d start, Vec3d end, double beatDistanceSquared) {
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(world, start, end, beatDistanceSquared,
                drone -> true);
        return hit == null ? Double.POSITIVE_INFINITY : hit.distanceSquared();
    }

    /**
     * Server gun validation for a client-picked Partygoer (same rule as a drone): the look ray meets the grown box with a
     * clear segment to the body, or (latency fallback) a sample point lies inside the aim cone and is visible.
     * 客户端选中派对客时的服务端枪械校验（与无人机规则相同）：视线射线与扩大箱体相交且到本体的线段无遮挡；或（延迟兜底）有采样点位于
     * 瞄准锥内且可见。
     */
    public static boolean gunAimedAndVisible(World world, Vec3d eye, Vec3d look, double maxDistance,
                                             PartygoerEntity partygoer, @Nullable Entity context) {
        Box box = partygoer.getBoundingBox();
        if (isFinite(look) && look.lengthSquared() > 0.0) {
            Vec3d end = eye.add(look.normalize().multiply(maxDistance));
            Optional<Vec3d> point = DroneHitGeometry.entryPoint(eye, end, box);
            if (point.isEmpty()) {
                Box in = insetBox(box);
                point = DroneHitGeometry.entryPoint(eye, end, targetBox(partygoer)).map(hit -> new Vec3d(
                        MathHelper.clamp(hit.x, in.minX, in.maxX),
                        MathHelper.clamp(hit.y, in.minY, in.maxY),
                        MathHelper.clamp(hit.z, in.minZ, in.maxZ)));
            }
            if (point.isPresent() && DroneHitGeometry.segmentClear(world, eye, point.get(), context)) {
                return true;
            }
        }
        double minCosine = Math.cos(Math.toRadians(PartygoerWeaponRules.GUN_AIM_CONE_DEGREES)) - 1.0E-9;
        for (Vec3d point : samplePoints(box)) {
            Vec3d toPoint = point.subtract(eye);
            double lengths = look.length() * toPoint.length();
            if (lengths > 0.0 && look.dotProduct(toPoint) / lengths >= minCosine) {
                return DroneHitGeometry.hasLineOfSight(world, eye, box, context);
            }
        }
        return false;
    }

    /** Melee: box within {@code reach} of the eyes and visible. / 近战：箱体距眼睛不超过 reach 且可见。 */
    public static boolean inMeleeReach(Entity attacker, PartygoerEntity partygoer, double reach) {
        Vec3d eye = attacker.getEyePos();
        return PartygoerWeaponRules.withinReach(
                DroneHitGeometry.squaredDistanceToBox(eye, targetBox(partygoer)), reach)
                && DroneHitGeometry.hasLineOfSight(attacker.getWorld(), eye, partygoer.getBoundingBox(), attacker);
    }

    /**
     * Blast: feet or body centre inside the sphere, and some sample point visible from the centre.
     * 爆炸：脚下或身体中心位于球内，且爆心能看到任一采样点。
     */
    public static boolean caughtInBlast(World world, Vec3d center, double radius, PartygoerEntity partygoer,
                                        @Nullable Entity context) {
        Vec3d feet = partygoer.getPos();
        Vec3d body = partygoer.getBoundingBox().getCenter();
        return PartygoerWeaponRules.inBlastSphere(feet.x - center.x, feet.y - center.y, feet.z - center.z,
                body.x - center.x, body.y - center.y, body.z - center.z, radius)
                && DroneHitGeometry.hasLineOfSight(world, center, partygoer.getBoundingBox(), context);
    }

    /**
     * Client target wrapper (apply after the drone wrapper): keep {@code original} unless a Partygoer lies strictly
     * nearer on the shooter's look ray. The ray length is the original pick's distance, so extended ranges (SparkTraits
     * Marksman) are kept; {@code range} is only the fallback. Uses Wathe's own aim ({@code getRotationVec(0)}).
     * 客户端目标包装（在无人机包装之后套用）：除非射手视线上有严格更近的派对客，否则保留原结果。射线长度取原选择的距离（保留 SparkTraits
     * 神射手等延长射程），range 仅作兜底。朝向与 Wathe 相同（getRotationVec(0)）。
     */
    public static HitResult preferNearer(@Nullable Entity shooter, @Nullable HitResult original, double range) {
        if (shooter == null || original == null) {
            return original;
        }
        Vec3d start = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(0.0F);
        double limit = referenceDistance(start, look, original, range);
        if (!(limit > 0.0) || !Double.isFinite(limit)) {
            return original;
        }
        Vec3d end = DroneHitGeometry.clipToBlocks(shooter.getWorld(), start, start.add(look.multiply(limit)), shooter);
        PartygoerHit hit = nearest(shooter.getWorld(), start, end, limit * limit, shooter);
        return hit == null ? original : new EntityHitResult(hit.partygoer(), hit.point());
    }

    private static List<PartygoerEntity> candidates(World world, Vec3d start, Vec3d end, @Nullable Entity attacker) {
        return world.getEntitiesByClass(PartygoerEntity.class, new Box(start, end).expand(SEARCH_PADDING),
                partygoer -> targetableBy(attacker, partygoer));
    }

    /**
     * Wathe's EntityHitResult carries the target's feet position, so measure where the look ray enters its box.
     * Wathe 的 EntityHitResult 位置为目标脚下，因此改量视线进入其箱体处的距离。
     */
    private static double referenceDistance(Vec3d start, Vec3d look, HitResult original, double range) {
        if (original instanceof EntityHitResult entityHit) {
            Box box = entityHit.getEntity().getBoundingBox();
            double reach = Math.max(range, Math.sqrt(DroneHitGeometry.squaredDistanceToBox(start, box)) + 2.0);
            double entry = DroneHitGeometry.entryDistanceSquared(start, start.add(look.multiply(reach)), box);
            return Math.sqrt(entry >= 0.0 ? entry : DroneHitGeometry.squaredDistanceToBox(start, box));
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

    private static boolean isFinite(Vec3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
