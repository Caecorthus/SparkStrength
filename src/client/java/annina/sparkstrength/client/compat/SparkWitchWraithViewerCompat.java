package annina.sparkstrength.client.compat;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BiPredicate;

/**
 * SparkWitch 冤魂观察者闸门的客户端软兼容桥。
 * Client-only optional bridge to SparkWitch's Wraith viewer gate.
 *
 * <p>SparkStrength 不能在编译期依赖 SparkWitch，因此仅在检测到 sparkwitch 时反射其公开门面
 * {@code dev.caecorthus.sparkwitch.api.client.WraithViewerApi#addViewerGate(BiPredicate)}；参数只用 JDK 类型，无需 Proxy。
 * 完整类名与签名是与 SparkWitch 的跨模组契约。未安装、旧版或签名不符时整体失效：冤魂对灵界行者保持隐身，绝不崩溃。
 * SparkStrength must not compile against SparkWitch, so this reflects only that public facade; the parameter is a JDK
 * type, so no Proxy is needed. The FQCN and signature are a cross-mod contract; when SparkWitch is absent, older or
 * mismatched the bridge fails closed (Wraiths stay invisible) and never throws.</p>
 */
public final class SparkWitchWraithViewerCompat {
    private static final String VIEWER_API_CLASS = "dev.caecorthus.sparkwitch.api.client.WraithViewerApi";

    private SparkWitchWraithViewerCompat() {
    }

    /**
     * Asks SparkWitch to reveal active Wraiths' bodies to viewers {@code gate} accepts ({@code (viewer, wraith)}, render
     * thread). Returns whether the gate was installed.
     * 请 SparkWitch 向 {@code gate} 接受的观察者显示活跃冤魂的身体（参数为 (观察者, 冤魂)，渲染线程）。返回是否安装成功。
     */
    public static boolean addViewerGate(BiPredicate<PlayerEntity, PlayerEntity> gate) {
        if (!FabricLoader.getInstance().isModLoaded("sparkwitch")) {
            return false;
        }
        try {
            Method add = Class.forName(VIEWER_API_CLASS).getMethod("addViewerGate", BiPredicate.class);
            if (!Modifier.isStatic(add.getModifiers())) {
                return false;
            }
            add.invoke(null, gate);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ignored) {
            SparkStrength.LOGGER.warn("SparkWitch Wraith viewer gate unavailable: installed SparkWitch lacks {}.addViewerGate"
                    + "(BiPredicate); the Spiritualist will not see Wraiths.", VIEWER_API_CLASS);
            return false;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            SparkStrength.LOGGER.warn("SparkWitch Wraith viewer gate install failed.", e);
            return false;
        }
    }
}
