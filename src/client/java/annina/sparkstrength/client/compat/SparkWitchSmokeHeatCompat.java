package annina.sparkstrength.client.compat;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * SparkWitch BEAR 烟雾进入卧底热成像的客户端软兼容桥（BEAR S4“热烟挡住热成像”，Q6 默认：烟显示为暖色薄雾并遮住其后的人体）。
 * Client-only optional bridge that puts SparkWitch's BEAR smoke into the Undercover thermal heat image (owner S4 "hot
 * smoke blocks thermal", Q6 default: smoke shows as a warm haze and hides the bodies behind it).
 *
 * <p>SparkStrength 不能在编译期依赖 SparkWitch，因此仅在检测到 sparkwitch 时反射其冻结的客户端门面
 * {@code dev.caecorthus.sparkwitch.api.client.BearSmokeViewApi}（只用 JDK 类型）：{@code hasSmoke()}、
 * {@code maskHeatTarget}、{@code drawHeatHaze}、{@code heatSmokeRect} 与 {@code DEFAULT_HEAT_HAZE}。完整类名与签名是跨模组
 * 契约。顺序遵循门面说明：画完人体之后、柔光合成之前，先遮罩，失败时隐藏烟雾矩形，再画热雾。未安装或没有 BEAR 门面
 * （旧版 SparkWitch 根本没有烟）时什么也不做；门面存在但有烟时任何一步失败都按失败即遮挡处理：把可能有烟的区域
 * （未知时为整个热图）交给调用方清掉，宁可多藏不可透烟。只记录一次日志，绝不抛出。
 * SparkStrength must not compile against SparkWitch, so this reflects only that frozen facade (JDK types only); its FQCN
 * and signatures are a cross-mod contract. Call order follows the facade: after the bodies, before the bloom
 * composite, mask first, hide the smoke rectangle when the mask fails, then the haze. Absent SparkWitch, or one without
 * the BEAR facade (an older SparkWitch has no smoke at all): nothing happens. Facade present and smoke known: every
 * failure fails closed, the area smoke may touch (the whole target when unknown) goes to the caller to clear, so heat
 * never shows through smoke. Logged once, never throws. Render thread only.</p>
 */
public final class SparkWitchSmokeHeatCompat {
    private static final String MOD_ID = "sparkwitch";
    private static final String BEAR_SMOKE_VIEW_API_CLASS = "dev.caecorthus.sparkwitch.api.client.BearSmokeViewApi";

    /** Render thread only. / 仅渲染线程。 */
    private static boolean resolved;
    /** Null: no BEAR smoke can exist on this client (absent or older SparkWitch). / 为 null：本客户端不可能有 BEAR 烟。 */
    private static @Nullable Method hasSmoke;
    private static @Nullable Method maskHeatTarget;
    private static @Nullable Method drawHeatHaze;
    private static @Nullable Method heatSmokeRect;
    private static float hazeHeat = Float.NaN;
    /**
     * A throwing smoke query or rectangle makes every later frame hide the whole heat target while thermal is on; a
     * throwing mask makes every later frame hide the smoke rectangle instead (fail closed, permanent like SparkWitch's
     * own GL failure). / 烟雾查询或矩形抛出异常后，之后每帧热成像都隐藏整个热图；遮罩抛出异常后，之后每帧改为隐藏烟雾矩形
     * （失败即遮挡，与 SparkWitch 自身的 GL 失败一样永久）。
     */
    private static boolean smokeUnknown;
    private static boolean maskBroken;
    private static boolean hazeBroken;
    private static boolean logged;

    private SparkWitchSmokeHeatCompat() {
    }

    /**
     * Applies BEAR smoke to a heat target that already holds its bodies (and their depth): {@code colorTexture} and
     * {@code depthTexture} are its GL texture ids, {@code width} x {@code height} its size, covering exactly this world
     * render's view. {@code hide} receives {@code {x0, y0, x1, y1}} (exclusive ends, GL origin bottom-left, clamped
     * to the target) whenever the heat there must be cleared because the mask could not run; it is called at most once,
     * before the haze. Call inside the same {@code WorldRenderer#render} pass, on the render thread.
     * 把 BEAR 烟雾作用到已画好人体（及其深度）的热图上：{@code colorTexture} 与 {@code depthTexture} 为其 GL 纹理 id，
     * {@code width} x {@code height} 为其尺寸，恰好覆盖本次世界渲染的视野。遮罩无法执行时，{@code hide} 收到需清空热值的
     * {@code {x0, y0, x1, y1}}（不含终点，GL 原点在左下，已裁剪到热图内）；最多调用一次，且在热雾之前。须在同一次
     * {@code WorldRenderer#render} 中、于渲染线程调用。
     */
    public static void applyToHeat(int colorTexture, int depthTexture, int width, int height, Consumer<int[]> hide) {
        if (hide == null || width <= 0 || height <= 0 || !resolve()) {
            return;
        }
        int[] whole = {0, 0, width, height};
        if (smokeUnknown) {
            hide.accept(whole);
            return;
        }
        try {
            if (!Boolean.TRUE.equals(hasSmoke.invoke(null))) {
                return;
            }
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            smokeUnknown = true;
            warnOnce("SparkWitch BEAR smoke query failed; the thermal view now hides all heat while it is on.", e);
            hide.accept(whole);
            return;
        }
        if (!mask(colorTexture, depthTexture, width, height)) {
            int[] rect = hiddenRect(width, height);
            if (rect != null) {
                hide.accept(rect);
            }
        }
        haze(colorTexture, depthTexture, width, height);
    }

    /** True when the heat behind smoke is right (masked, or no smoke touches this pass). / 烟后的热值正确时为 true。 */
    private static boolean mask(int colorTexture, int depthTexture, int width, int height) {
        Method method = maskHeatTarget;
        if (method == null || maskBroken) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, colorTexture, depthTexture, width, height));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            maskBroken = true;
            warnOnce("SparkWitch BEAR smoke heat mask failed; the thermal view hides heat inside smoke instead.", e);
            return false;
        }
    }

    /**
     * The area to clear after a failed mask: SparkWitch's smoke rectangle clamped to the target, null when smoke
     * cannot touch it, the whole target when that is unknown or the answer is malformed.
     * 遮罩失败后需清空的区域：SparkWitch 的烟雾矩形（裁剪到热图内）；烟不可能触及时为 null；无法得知或答案异常时为整个热图。
     */
    private static @Nullable int[] hiddenRect(int width, int height) {
        int[] whole = {0, 0, width, height};
        Method method = heatSmokeRect;
        if (method == null || smokeUnknown) {
            return whole;
        }
        Object answer;
        try {
            answer = method.invoke(null, width, height);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            smokeUnknown = true;
            warnOnce("SparkWitch BEAR smoke rectangle failed; the thermal view now hides all heat while it is on.", e);
            return whole;
        }
        if (answer == null) {
            return null;
        }
        if (!(answer instanceof int[] rect) || rect.length != 4) {
            return whole;
        }
        int x0 = Math.max(0, rect[0]);
        int y0 = Math.max(0, rect[1]);
        int x1 = Math.min(width, rect[2]);
        int y1 = Math.min(height, rect[3]);
        return x1 > x0 && y1 > y0 ? new int[]{x0, y0, x1, y1} : null;
    }

    /** Cosmetic: a failure only switches the haze off. / 仅外观：失败只会关掉热雾。 */
    private static void haze(int colorTexture, int depthTexture, int width, int height) {
        Method method = drawHeatHaze;
        if (method == null || hazeBroken || !(hazeHeat > 0.0F && hazeHeat <= 1.0F)) {
            return;
        }
        try {
            method.invoke(null, colorTexture, depthTexture, width, height, hazeHeat);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            hazeBroken = true;
            warnOnce("SparkWitch BEAR smoke heat haze failed; the thermal view shows no smoke haze.", e);
        }
    }

    /**
     * Looked up once. False when no BEAR smoke can exist here (SparkWitch absent, or a build without
     * {@code hasSmoke()}). A build with {@code hasSmoke()} but without the heat entries fails closed every frame that
     * has smoke. / 只查找一次。本客户端不可能有 BEAR 烟时（未安装 SparkWitch，或没有 {@code hasSmoke()} 的版本）为 false。
     * 有 {@code hasSmoke()} 却缺少热图入口的版本在有烟的每一帧都失败即遮挡。
     */
    private static boolean resolve() {
        if (resolved) {
            return hasSmoke != null;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            logOnce(false, "SparkWitch not installed; the thermal view has no BEAR smoke to show.", null);
            return false;
        }
        Class<?> api;
        try {
            api = Class.forName(BEAR_SMOKE_VIEW_API_CLASS);
            hasSmoke = staticMethod(api, "hasSmoke", boolean.class);
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            // SparkWitch builds before BEAR (2026-11-05) have no smoke at all. / BEAR 之前的 SparkWitch 根本没有烟。
            api = null;
        }
        if (hasSmoke == null) {
            logOnce(false, "Installed SparkWitch has no BEAR smoke; the thermal view is unchanged.", null);
            return false;
        }
        maskHeatTarget = staticMethod(api, "maskHeatTarget", boolean.class, int.class, int.class, int.class, int.class);
        drawHeatHaze = staticMethod(api, "drawHeatHaze", boolean.class, int.class, int.class, int.class, int.class,
                float.class);
        heatSmokeRect = staticMethod(api, "heatSmokeRect", int[].class, int.class, int.class);
        hazeHeat = hazeConstant(api);
        if (maskHeatTarget == null || heatSmokeRect == null) {
            warnOnce("Installed SparkWitch lacks " + BEAR_SMOKE_VIEW_API_CLASS + ".maskHeatTarget/heatSmokeRect; the "
                    + "thermal view hides all heat while BEAR smoke exists.", null);
        }
        return true;
    }

    private static @Nullable Method staticMethod(Class<?> api, String name, Class<?> returnType,
                                                 Class<?>... parameterTypes) {
        try {
            Method method = api.getMethod(name, parameterTypes);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType ? method : null;
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    private static float hazeConstant(Class<?> api) {
        try {
            Field field = api.getField("DEFAULT_HEAT_HAZE");
            return Modifier.isStatic(field.getModifiers()) && field.getType() == float.class
                    ? field.getFloat(null) : Float.NaN;
        } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
            return Float.NaN;
        }
    }

    private static void warnOnce(String message, @Nullable Throwable error) {
        logOnce(true, message, error);
    }

    private static void logOnce(boolean warn, String message, @Nullable Throwable error) {
        if (logged) {
            return;
        }
        logged = true;
        if (!warn) {
            SparkStrength.LOGGER.info(message);
        } else if (error == null) {
            SparkStrength.LOGGER.warn(message);
        } else {
            SparkStrength.LOGGER.warn(message, error);
        }
    }
}
