package annina.sparkstrength.role.perfumer;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure tuning and curves for the SparkStrength Perfumer kit (Cooling Oil, Aroma Orb, Zephyr Perfume).
 * The Perfumer role belongs to SparkWitch, which is optional: it is matched only by its stable id, so
 * these items never reach any shop when SparkWitch is absent.
 * SparkStrength 调香师道具（风油精、香薰、晨风香水）的纯数值与曲线。调香师属于可选模组 SparkWitch，
 * 这里只按稳定 id 匹配；未安装 SparkWitch 时这些道具不会进入任何商店。
 */
public final class PerfumerRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "perfumer");

    public static final String COOLING_OIL_ENTRY_ID = "sparkstrength_cooling_oil";
    public static final String AROMA_ORB_ENTRY_ID = "sparkstrength_aroma_orb";
    public static final String ZEPHYR_PERFUME_ENTRY_ID = "sparkstrength_zephyr_perfume";

    public static final int COOLING_OIL_PRICE = 50;
    public static final int AROMA_ORB_PRICE = 75;
    public static final int ZEPHYR_PERFUME_PRICE = 150;
    public static final int ZEPHYR_PERFUME_STOCK = 1;
    public static final int THROWABLE_MAX_STACK = 8;

    public static final float THROW_SPEED = 1.5F;
    public static final float THROW_DIVERGENCE = 1.0F;

    /** 5×5 splash: sphere radius 2.5 judged by the shared grenade cover algorithm. / 5×5 溅射：半径 2.5 的球，沿用手雷遮挡判定。 */
    public static final double COOLING_OIL_RADIUS = 2.5D;
    /** Furthest the splash settles straight down onto a floor after hitting a wall or ceiling. / 撞墙或天花板后溅射向下落到地面的最大距离。 */
    public static final double COOLING_OIL_MAX_SETTLE_DROP = 4.0D;
    public static final int COOLING_OIL_DURATION_TICKS = 100;
    public static final int AROMA_DURATION_TICKS = 60;

    public static final int ZEPHYR_SPEED_AMPLIFIER = 1;
    public static final int ZEPHYR_REFRESH_INTERVAL_TICKS = 20;
    public static final float ZEPHYR_MOOD_DRAIN_MULTIPLIER = 0.5F;

    public static final Identifier COOLING_OIL_ACTION_ID = Identifier.of("sparkstrength", "perfumer_cooling_oil");
    public static final Identifier AROMA_ACTION_ID = Identifier.of("sparkstrength", "perfumer_aroma");

    // Aroma aim curve. / 香薰准心曲线。
    public static final double AROMA_MIN_SENSITIVITY = 0.3D;
    public static final double AROMA_MAX_SENSITIVITY = 2.5D;
    public static final int AROMA_SENSITIVITY_MIN_SEGMENT_TICKS = 8;
    public static final int AROMA_SENSITIVITY_MAX_SEGMENT_TICKS = 14;
    public static final double AROMA_MAX_YAW_DRIFT_DEG_PER_SEC = 45.0D;
    public static final double AROMA_MAX_PITCH_DRIFT_DEG_PER_SEC = 20.0D;
    public static final int AROMA_DRIFT_MIN_SEGMENT_TICKS = 10;
    public static final int AROMA_DRIFT_MAX_SEGMENT_TICKS = 16;
    public static final int AROMA_FADE_IN_TICKS = 3;
    public static final int AROMA_FADE_OUT_TICKS = 10;

    // Cooling Oil screen envelopes. / 风油精画面包络。
    public static final float COOLING_OIL_MAX_BLUR_RADIUS = 10.0F;
    public static final int COOLING_OIL_BLUR_FADE_IN_TICKS = 4;
    public static final int COOLING_OIL_FADE_OUT_TICKS = 20;
    public static final int COOLING_OIL_OVERLAY_FADE_IN_TICKS = 3;
    /** Blur radius is tuned for a 1080-pixel-tall framebuffer. / 模糊半径按 1080 像素高的帧缓冲调校。 */
    public static final int COOLING_OIL_BLUR_REFERENCE_HEIGHT = 1080;
    public static final float COOLING_OIL_BLUR_MAX_RESOLUTION_SCALE = 3.0F;

    // Cooling Oil squint: eyelid coverage as a fraction of half the screen height. / 风油精眯眼：眼睑覆盖半屏高度的比例。
    public static final float EYELID_BASE_COVERAGE = 0.16F;
    public static final float EYELID_BREATH_AMPLITUDE = 0.05F;
    public static final float EYELID_BREATH_RADIANS_PER_TICK = 0.35F;
    public static final int EYELID_BLINK_PERIOD_TICKS = 26;
    public static final int EYELID_BLINK_LENGTH_TICKS = 5;
    public static final float EYELID_BLINK_AMPLITUDE = 0.28F;

    private static final long SALT_SENSITIVITY_LENGTH = 0x5EED_0001L;
    private static final long SALT_SENSITIVITY_VALUE = 0x5EED_0002L;
    private static final long SALT_DRIFT_LENGTH = 0x5EED_0003L;
    private static final long SALT_YAW = 0x5EED_0004L;
    private static final long SALT_PITCH = 0x5EED_0005L;

    private PerfumerRules() {
    }

    public static boolean isPerfumer(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    public static float zephyrMoodDrain(float baseDrain, boolean zephyrActive) {
        return zephyrActive ? baseDrain * ZEPHYR_MOOD_DRAIN_MULTIPLIER : baseDrain;
    }

    /**
     * Linear 0..1 envelope: ramps up over {@code fadeIn} ticks after the start and down over the last
     * {@code fadeOut} ticks. Both arguments may be fractional (partial ticks).
     * 线性 0..1 包络：开始后 fadeIn tick 内升满，最后 fadeOut tick 内降到 0；参数可为小数（含 partial tick）。
     */
    public static float envelope(float elapsedTicks, float remainingTicks, int fadeIn, int fadeOut) {
        if (elapsedTicks < 0.0F || remainingTicks <= 0.0F) {
            return 0.0F;
        }
        float in = fadeIn <= 0 ? 1.0F : Math.min(1.0F, elapsedTicks / fadeIn);
        float out = fadeOut <= 0 ? 1.0F : Math.min(1.0F, remainingTicks / fadeOut);
        return Math.max(0.0F, Math.min(in, out));
    }

    public static float coolingOilBlurRadius(float elapsedTicks, float remainingTicks) {
        return COOLING_OIL_MAX_BLUR_RADIUS * envelope(
                elapsedTicks, remainingTicks, COOLING_OIL_BLUR_FADE_IN_TICKS, COOLING_OIL_FADE_OUT_TICKS);
    }

    public static float coolingOilOverlayAlpha(float elapsedTicks, float remainingTicks) {
        return envelope(elapsedTicks, remainingTicks, COOLING_OIL_OVERLAY_FADE_IN_TICKS, COOLING_OIL_FADE_OUT_TICKS);
    }

    public static float aromaStrength(float elapsedTicks, float remainingTicks) {
        return envelope(elapsedTicks, remainingTicks, AROMA_FADE_IN_TICKS, AROMA_FADE_OUT_TICKS);
    }

    /**
     * Scales the blur radius with framebuffer height so HiDPI screens blur as much as a 1080p screen;
     * never weaker than the reference.
     * 按帧缓冲高度放大模糊半径，使高分屏与 1080p 的模糊程度一致；不会弱于基准。
     */
    public static float coolingOilBlurResolutionScale(int framebufferHeight) {
        float scale = framebufferHeight / (float) COOLING_OIL_BLUR_REFERENCE_HEIGHT;
        return Math.max(1.0F, Math.min(COOLING_OIL_BLUR_MAX_RESOLUTION_SCALE, scale));
    }

    /**
     * Squinting eyelid coverage (fraction of half the screen height, 0..1) at {@code elapsedTicks}: a slow
     * breathing squint plus a 5-tick triangular blink every 26 ticks, the first one right at the splash.
     * 眯眼眼睑覆盖率（占半屏高度的比例，0..1）：缓慢起伏的眯眼，加上每 26 tick 一次、持续 5 tick 的三角形眨眼，
     * 第一次眨眼发生在溅到的瞬间。
     */
    public static float coolingOilEyelidCoverage(float elapsedTicks) {
        float t = Math.max(0.0F, elapsedTicks);
        float breath = EYELID_BREATH_AMPLITUDE * (float) Math.sin(t * EYELID_BREATH_RADIANS_PER_TICK);
        float phase = t % EYELID_BLINK_PERIOD_TICKS;
        float blink = 0.0F;
        if (phase < EYELID_BLINK_LENGTH_TICKS) {
            float half = EYELID_BLINK_LENGTH_TICKS / 2.0F;
            blink = EYELID_BLINK_AMPLITUDE * (1.0F - Math.abs(phase - half) / half);
        }
        return Math.max(0.0F, Math.min(1.0F, EYELID_BASE_COVERAGE + breath + blink));
    }

    /**
     * Mouse sensitivity multiplier for the Aroma victim, in [MIN, MAX] at full strength and blended toward
     * 1.0 by {@code strength}. Deterministic in (seed, elapsedTicks) so client and tests agree.
     * 香薰受害者的鼠标灵敏度倍率：满强度时位于 [MIN, MAX]，按 strength 向 1.0 混合；
     * 只由 (seed, elapsedTicks) 决定，客户端与测试结果一致。
     */
    public static double aromaSensitivity(long seed, float elapsedTicks, float strength) {
        double raw = smoothKeyframes(seed, elapsedTicks, SALT_SENSITIVITY_LENGTH, SALT_SENSITIVITY_VALUE,
                AROMA_SENSITIVITY_MIN_SEGMENT_TICKS, AROMA_SENSITIVITY_MAX_SEGMENT_TICKS);
        double logMin = Math.log(AROMA_MIN_SENSITIVITY);
        double logMax = Math.log(AROMA_MAX_SENSITIVITY);
        double multiplier = Math.exp(logMin + raw * (logMax - logMin));
        double s = clamp01(strength);
        return 1.0D + (multiplier - 1.0D) * s;
    }

    /** Yaw drift in degrees per second, in [-MAX, MAX] scaled by strength. / 偏航漂移（度/秒）。 */
    public static double aromaYawDriftDegPerSec(long seed, float elapsedTicks, float strength) {
        double raw = smoothKeyframes(seed, elapsedTicks, SALT_DRIFT_LENGTH, SALT_YAW,
                AROMA_DRIFT_MIN_SEGMENT_TICKS, AROMA_DRIFT_MAX_SEGMENT_TICKS);
        return (raw * 2.0D - 1.0D) * AROMA_MAX_YAW_DRIFT_DEG_PER_SEC * clamp01(strength);
    }

    /** Pitch drift in degrees per second, in [-MAX, MAX] scaled by strength. / 俯仰漂移（度/秒）。 */
    public static double aromaPitchDriftDegPerSec(long seed, float elapsedTicks, float strength) {
        double raw = smoothKeyframes(seed, elapsedTicks, SALT_DRIFT_LENGTH, SALT_PITCH,
                AROMA_DRIFT_MIN_SEGMENT_TICKS, AROMA_DRIFT_MAX_SEGMENT_TICKS);
        return (raw * 2.0D - 1.0D) * AROMA_MAX_PITCH_DRIFT_DEG_PER_SEC * clamp01(strength);
    }

    /**
     * Smoothstep interpolation between hashed keyframes in [0, 1]; segment lengths vary per segment.
     * 在 [0,1] 的哈希关键帧之间做 smoothstep 插值；每段长度各不相同。
     */
    static double smoothKeyframes(long seed, float t, long lengthSalt, long valueSalt, int minLen, int maxLen) {
        float time = Math.max(0.0F, t);
        float start = 0.0F;
        int span = Math.max(1, maxLen - minLen + 1);
        for (int k = 0; k < 4096; k++) {
            int length = minLen + (int) Math.floorMod(mix(seed, k, lengthSalt), (long) span);
            if (time < start + length) {
                double u = (time - start) / length;
                double eased = u * u * (3.0D - 2.0D * u);
                double a = unit(mix(seed, k, valueSalt));
                double b = unit(mix(seed, k + 1, valueSalt));
                return a + (b - a) * eased;
            }
            start += length;
        }
        return unit(mix(seed, 0, valueSalt));
    }

    private static long mix(long seed, long index, long salt) {
        long z = seed ^ (index * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
