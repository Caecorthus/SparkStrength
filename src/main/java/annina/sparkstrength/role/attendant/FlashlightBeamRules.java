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
    /**
     * Distance at which the inverse-square term halves; the range window trims it slightly more.
     * 平方反比项减半的距离；射程窗口会再略微压低。
     */
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
    /**
     * Occlusion stores, per ray, the distance where the ray leaves the first occluder (see
     * {@link #OCCLUDER_DEPTH_CAP_BLOCKS}), so an occluder's lit front faces keep its whole thickness as slack and the
     * receiver shader's normal offset absorbs grid discretisation. The bias is therefore slightly negative up close:
     * receivers lying on the occluder's far faces (the floor and wall seams right behind a closed door) count as
     * shadowed; it turns positive past 20 blocks where cells grow coarse.
     * 遮挡按射线记录离开首个遮挡体的距离（见 OCCLUDER_DEPTH_CAP_BLOCKS），因此遮挡体受光的正面以其整个厚度作为余量，
     * 受光着色器的法线偏移吸收网格离散误差。所以近处的偏移略为负：位于遮挡体背面上的受光点（紧贴关闭的门背后的地面与墙缝）
     * 视为处于阴影中；超过 20 格后单元变粗，偏移转为正值。
     */
    public static final double SHADOW_BIAS_BLOCKS = -0.02;
    public static final double SHADOW_BIAS_PER_BLOCK = 0.001;
    /**
     * Deepest an occluder run may extend past its entry: a ray that leaves one occluding box straight into another
     * (carpet into floor, slab into the block below) keeps going up to this depth, which gives grazing floors a
     * full block of slack, while a ray diving into the ground cannot carry light under a wall.
     * 遮挡区间自进入点起的最大延伸深度：射线离开一个遮挡盒并立即进入另一个（地毯到地面、台阶到下方方块）时继续延伸至此
     * 深度，使掠射地面获得一整格余量，同时钻入地面的射线不会把光带到墙下。
     */
    public static final double OCCLUDER_DEPTH_CAP_BLOCKS = 1.0;
    /** Soft knee before light reaches albedo or entity light levels. / 光照作用于反照率或实体亮度前的软拐点。 */
    public static final double EXPOSURE = 1.6;
    /**
     * Block-light floor for the holder of a lit flashlight (spill off the reflector), so their hand and the flashlight
     * body read dimly instead of as a black cut-out inside the beam; others see the holder faintly lit too.
     * 已开启手电持有者的方块光下限（反光杯的溢光），使其手部与手电本体呈现暗淡亮度，而不是光斑中的黑色剪影；
     * 其他玩家也会看到持有者被微微照亮。
     */
    public static final int HOLDER_BLOCK_LIGHT = 6;

    private static final int DISTANCE_ENCODING_MAX = 0xFFFF;
    // Precomputed: the ray caster and entity lighting call these per ray / per entity.
    // 预先计算：射线投射与实体照明会逐射线、逐实体调用它们。
    private static final double COS_INNER = Math.cos(INNER_HALF_ANGLE_RADIANS);
    private static final double COS_OUTER = Math.cos(OUTER_HALF_ANGLE_RADIANS);
    private static final double COS_SPILL = Math.cos(SPILL_HALF_ANGLE_RADIANS);
    private static final double RAY_GRID_TANGENT_EXTENT = Math.tan(SPILL_HALF_ANGLE_RADIANS + RAY_GRID_MARGIN_RADIANS);

    private FlashlightBeamRules() {
    }

    public static double cosInner() {
        return COS_INNER;
    }

    public static double cosOuter() {
        return COS_OUTER;
    }

    public static double cosSpill() {
        return COS_SPILL;
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

    /**
     * Block-light level whose vanilla lightmap brightness ({@code f / (4 - 3f)}, f = level / 15) matches the exposed
     * light the receiver shader adds to terrain, so a player standing in the spot looks as lit as the wall behind
     * them instead of a linear level that the lightmap curve would darken to about half.
     * 选取原版光照贴图亮度（{@code f / (4 - 3f)}，f = 等级 / 15）与受光着色器加到地形上的曝光亮度一致的方块光等级，
     * 使站在光斑里的玩家与身后墙面同样明亮，而不是用线性等级被光照曲线压暗到约一半。
     */
    public static int entityBlockLight(double intensity) {
        double brightness = exposed(intensity);
        double fraction = 4.0 * brightness / (1.0 + 3.0 * brightness);
        return (int) Math.max(0L, Math.min(15L, Math.round(15.0 * fraction)));
    }

    /** Tangent of the half-angle the ray grid spans on each axis. / 射线网格每个轴向所覆盖半角的正切值。 */
    public static double rayGridTangentExtent() {
        return RAY_GRID_TANGENT_EXTENT;
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

    /**
     * True when a receiver {@code distance} from the cast origin lies beyond the occluder the cell's ray left at
     * {@code occluderExit} (RANGE_BLOCKS when nothing occludes). 距投射原点 distance 的受光点位于该单元射线离开遮挡体的
     * 距离 occluderExit（无遮挡时为射程）之后时为 true。
     */
    public static boolean isShadowed(double distance, double occluderExit) {
        return distance > occluderExit + shadowBias(distance);
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
