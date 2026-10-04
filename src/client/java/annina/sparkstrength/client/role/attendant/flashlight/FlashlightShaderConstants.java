package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.role.attendant.FlashlightBeamRules;

/**
 * Uniform values for the flashlight core shaders. The beam-rule entries are derived from {@link FlashlightBeamRules},
 * so the GLSL in {@code shaders/include/flashlight.glsl} never holds a duplicate number; the rest are render-only
 * tuning knobs. No Minecraft types, so the values can be checked outside the game.
 * 手电筒核心着色器的 uniform 值。光束规则相关项均由 {@link FlashlightBeamRules} 推导，GLSL 中不保存重复数值；
 * 其余为仅影响渲染的调节参数。不依赖 Minecraft 类型，可在游戏外校验。
 */
public final class FlashlightShaderConstants {
    /** Warm incandescent tint multiplied into every lit pixel. / 乘到每个受光像素上的暖色白炽光色调。 */
    public static final float LIGHT_RED = 1.0F;
    public static final float LIGHT_GREEN = 0.94F;
    public static final float LIGHT_BLUE = 0.82F;
    /**
     * In-scatter per block of beam density: a faint dusty shaft seen from the side, never a laser.
     * 每格光束密度的散射强度：侧面看是淡淡的尘埃光柱，而非激光。
     */
    public static final float BEAM_STRENGTH = 0.035F;
    /** Peak of the lens glare when looking straight into a nearby light. / 近距离直视光源时镜头眩光的峰值。 */
    public static final float GLARE_STRENGTH = 0.6F;

    private FlashlightShaderConstants() {
    }

    /** ConeCos: cos of the inner, outer and spill half-angles. / 内锥、外锥与溢光半角的余弦。 */
    public static float[] coneCos() {
        return new float[]{
                (float) FlashlightBeamRules.cosInner(),
                (float) FlashlightBeamRules.cosOuter(),
                (float) FlashlightBeamRules.cosSpill()
        };
    }

    public static float spillStrength() {
        return (float) FlashlightBeamRules.SPILL_STRENGTH;
    }

    /** RangeFalloff: range and half-intensity distance in blocks. / 射程与半亮度距离（格）。 */
    public static float[] rangeFalloff() {
        return new float[]{(float) FlashlightBeamRules.RANGE_BLOCKS, (float) FlashlightBeamRules.FALLOFF_BLOCKS};
    }

    public static float peakIntensity() {
        return (float) FlashlightBeamRules.PEAK_INTENSITY;
    }

    public static float exposure() {
        return (float) FlashlightBeamRules.EXPOSURE;
    }

    /** ShadowBias: constant and per-block terms of shadowBias(distance). / shadowBias 的常数项与每格项。 */
    public static float[] shadowBias() {
        return new float[]{
                (float) FlashlightBeamRules.SHADOW_BIAS_BLOCKS,
                (float) FlashlightBeamRules.SHADOW_BIAS_PER_BLOCK
        };
    }

    /**
     * RayGrid: tangent extent of the grid, cells per axis, and blocks per encoded distance unit (decodeDistance(1)).
     * 网格切平面半宽、每轴单元数、每个距离编码单位对应的格数。
     */
    public static float[] rayGrid() {
        return new float[]{
                (float) FlashlightBeamRules.rayGridTangentExtent(),
                FlashlightBeamRules.RAY_GRID_SIZE,
                (float) FlashlightBeamRules.decodeDistance(1)
        };
    }
}
