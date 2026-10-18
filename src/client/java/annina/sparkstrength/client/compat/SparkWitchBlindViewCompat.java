package annina.sparkstrength.client.compat;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * SparkWitch 盲人视图透视描边的客户端软兼容桥。
 * Client-only optional bridge to SparkWitch's Blind see-through outlines.
 *
 * <p>SparkWitch 的盲人回声画面在全部世界渲染之后覆盖屏幕，{@code WorldRenderEvents.LAST} 画的描边到不了盲人眼前。
 * SparkStrength 不能在编译期依赖 SparkWitch，因此仅在检测到 sparkwitch 时反射其冻结的公开门面
 * {@code dev.caecorthus.sparkwitch.api.client.BlindViewApi#addSeeThroughOutlines(Consumer)}；参数只用 JDK 与 Minecraft
 * 类型，无需 Proxy。完整类名与签名是与 SparkWitch 的跨模组契约。未安装、旧版、签名不符或调用失败时整体失效（只记录一次
 * 日志）：盲人看不到这些描边，其他人照旧，绝不崩溃。
 * SparkWitch's Blind echo view paints over the screen after all world rendering, so outlines drawn at
 * {@code WorldRenderEvents.LAST} never reach a Blind. SparkStrength must not compile against SparkWitch, so this
 * reflects only that frozen public facade; the parameter uses JDK and Minecraft types only, so no Proxy is needed. The
 * FQCN and signature are a cross-mod contract; when SparkWitch is absent, older, mismatched or the call fails, the
 * bridge fails closed (logged once): the Blind just misses these outlines, everyone else is unchanged, nothing throws.</p>
 */
public final class SparkWitchBlindViewCompat {
    private static final String MOD_ID = "sparkwitch";
    private static final String BLIND_VIEW_API_CLASS = "dev.caecorthus.sparkwitch.api.client.BlindViewApi";
    /** Client init / render thread only. / 仅客户端初始化 / 渲染线程。 */
    private static boolean resolved;
    private static @Nullable Method addSource;
    private static boolean logged;

    private SparkWitchBlindViewCompat() {
    }

    /**
     * Hands {@code source} to SparkWitch: on the render thread, once per frame and only while the local Blind's echo
     * view is shown, it writes world-space boxes as {@code (box, 0xAARRGGBB)} into the sink and must not render.
     * Returns whether SparkWitch took it.
     * 把 {@code source} 交给 SparkWitch：仅在本地盲人的回声画面显示时，于渲染线程每帧调用一次，以
     * {@code (box, 0xAARRGGBB)} 向接收器写入世界坐标的包围盒，不得自行渲染。返回 SparkWitch 是否接收。
     */
    public static boolean addSeeThroughOutlines(Consumer<ObjIntConsumer<Box>> source) {
        Method add = addSourceMethod();
        if (add == null || source == null) {
            return false;
        }
        try {
            add.invoke(null, source);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            warnOnce("SparkWitch Blind see-through outlines could not be added; the Blind will not see "
                    + "sabotage outlines.", e);
            return false;
        }
    }

    /** Looked up once; null when SparkWitch is absent, older or mismatched. / 只查找一次；SparkWitch 缺失、较旧或签名不符时为 null。 */
    private static @Nullable Method addSourceMethod() {
        if (resolved) {
            return addSource;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            logOnce(false, "SparkWitch not installed; Blind see-through outlines stay off.", null);
            return null;
        }
        try {
            Method method = Class.forName(BLIND_VIEW_API_CLASS).getMethod("addSeeThroughOutlines", Consumer.class);
            if (Modifier.isStatic(method.getModifiers())) {
                addSource = method;
                return method;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            // SparkWitch builds before 2026-10-18 have no BlindViewApi; warned below.
            // 2026-10-18 之前的 SparkWitch 没有 BlindViewApi；下方记录警告。
        }
        warnOnce("SparkWitch Blind see-through outlines unavailable: installed SparkWitch lacks " + BLIND_VIEW_API_CLASS
                + ".addSeeThroughOutlines(Consumer); the Blind will not see sabotage outlines.", null);
        return null;
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
