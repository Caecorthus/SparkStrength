package annina.sparkstrength.role.attendant;

/**
 * Pure flashlight beam math shared by the client light tracker, entity lighting and the flashlight shaders.
 * The shaders receive these constants as uniforms; keep every formula here in sync with
 * {@code assets/sparkstrength/shaders/include/flashlight.glsl}.
 * 手电筒光束纯数学规则，由客户端光源追踪、实体照明与手电筒着色器共用。
 * 着色器通过 uniform 接收这些常量；此处公式须与 {@code shaders/include/flashlight.glsl} 保持一致。
 */
public final class FlashlightBeamRules {
    public static final double RANGE_BLOCKS = 28.0;
    /** Distance at which the inverse-square falloff halves the intensity. / 衰减至一半亮度的距离。 */
    public static final double FALLOFF_BLOCKS = 10.0;
    public static final double PEAK_INTENSITY = 1.35;
    public static final double INNER_HALF_ANGLE_RADIANS = Math.toRadians(8.0);
    public static final double OUTER_HALF_ANGLE_RADIANS = Math.toRadians(20.0);
    public static final double SPILL_HALF_ANGLE_RADIANS = Math.toRadians(30.0);
    public static final double SPILL_STRENGTH = 0.14;
    /**
     * The ray grid covers the spill cone plus this margin, so per-frame rotation between ticks stays covered.
     * 射线网格覆盖溢光锥并额外留出此余量，使两 tick 之间的逐帧转动仍被覆盖。
     */
    public static final double RAY_GRID_MARGIN_RADIANS = Math.toRadians(8.0);
    public static final int RAY_GRID_SIZE = 32;
    public static final double SHADOW_BIAS_BLOCKS = 0.35;
    public static final double SHADOW_BIAS_PER_BLOCK = 0.02;
    /** Soft knee before light reaches albedo or entity light levels. / 光照作用于反照率或实体亮度前的软拐点。 */
    public static final double EXPOSURE = 1.6;

    private static final int DISTANCE_ENCODING_MAX = 0xFFFF;

    private FlashlightBeamRules() {
    }

    public static double cosInner() {
        return Math.cos(INNER_HALF_ANGLE_RADIANS);
    }

    public static double cosOuter() {
        return Math.cos(OUTER_HALF_ANGLE_RADIANS);
    }

    public static double cosSpill() {
        return Math.cos(SPILL_HALF_ANGLE_RADIANS);
    }

    /**
     * 1 inside the inner cone, smooth to {@link #SPILL_STRENGTH} at the outer edge, then a faint halo to 0 at the spill edge.
     * 内锥内为 1，平滑过渡到外锥边缘的溢光强度，再以微弱光晕在溢光边缘降为 0。
     */
    public static double coneFactor(double cosAngle) {
        double core = smoothstep(cosOuter(), cosInner(), cosAngle);
        double halo = SPILL_STRENGTH * smoothstep(cosSpill(), cosOuter(), cosAngle);
        return core + (1.0 - core) * halo;
    }

    public static double distanceFactor(double distance) {
        if (distance >= RANGE_BLOCKS) {
            return 0.0;
        }
        double clamped = Math.max(0.0, distance);
        double ratio = clamped / RANGE_BLOCKS;
        double window = 1.0 - ratio * ratio * ratio * ratio;
        double falloff = clamped / FALLOFF_BLOCKS;
        return window * window / (1.0 + falloff * falloff);
    }

    /** Unoccluded, pre-exposure intensity. / 未计遮挡、未经曝光处理的强度。 */
    public static double intensity(double cosAngle, double distance) {
        return PEAK_INTENSITY * coneFactor(cosAngle) * distanceFactor(distance);
    }

    public static double exposed(double intensity) {
        return 1.0 - Math.exp(-EXPOSURE * Math.max(0.0, intensity));
    }

    public static int entityBlockLight(double intensity) {
        return (int) Math.max(0L, Math.min(15L, Math.round(15.0 * exposed(intensity))));
    }

    /** Tangent of the half-angle the ray grid spans on each axis. / 射线网格每个轴向所覆盖半角的正切值。 */
    public static double rayGridTangentExtent() {
        return Math.tan(SPILL_HALF_ANGLE_RADIANS + RAY_GRID_MARGIN_RADIANS);
    }

    /**
     * Tangent-plane coordinate of grid cell {@code index}'s centre; ray = normalize(forward + tu*right + tv*up).
     * 网格单元中心在切平面上的坐标；射线方向为 normalize(forward + tu*right + tv*up)。
     */
    public static double gridCellTangent(int index) {
        return ((index + 0.5) / RAY_GRID_SIZE * 2.0 - 1.0) * rayGridTangentExtent();
    }

    /** Tangent-plane coordinate to texture space [0, 1]; outside means outside the grid. / 切平面坐标转为纹理坐标。 */
    public static double tangentToGridUnit(double tangent) {
        return (tangent / rayGridTangentExtent() + 1.0) * 0.5;
    }

    public static double shadowBias(double distance) {
        return SHADOW_BIAS_BLOCKS + SHADOW_BIAS_PER_BLOCK * Math.max(0.0, distance);
    }

    public static boolean isShadowed(double distance, double hitDistance) {
        return distance > hitDistance + shadowBias(distance);
    }

    /** 16-bit fixed point of {@code distance / RANGE_BLOCKS}. / 距离占射程比例的 16 位定点编码。 */
    public static int encodeDistance(double distance) {
        double ratio = Math.max(0.0, Math.min(1.0, distance / RANGE_BLOCKS));
        return (int) Math.round(ratio * DISTANCE_ENCODING_MAX);
    }

    public static double decodeDistance(int encoded) {
        return (encoded & DISTANCE_ENCODING_MAX) / (double) DISTANCE_ENCODING_MAX * RANGE_BLOCKS;
    }

    static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
