package annina.sparkstrength.client.compat;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * SparkWitch “职业技能 2”键（默认 N）的客户端软兼容桥。
 * Client-only optional bridge to SparkWitch's Role Skill 2 key (default N).
 *
 * <p>SparkStrength 不能在编译期依赖 SparkWitch，因此仅在检测到 sparkwitch 时反射其公开门面
 * {@code dev.caecorthus.sparkwitch.api.client.SecondarySkillKeyApi}。完整类名与
 * {@code register(Identifier, Runnable)}、{@code boundKeyText()} 两个签名是与 SparkWitch 的跨模组契约，
 * 不得单方面改名。未安装、旧版或签名不符时整体失效：不注册按键、HUD 不显示，绝不崩溃。
 * SparkStrength must not compile against SparkWitch, so this reflects only that public facade. Its FQCN and both
 * signatures are a cross-mod contract; when SparkWitch is absent, older or mismatched the bridge fails closed
 * (no key, no HUD line) and never throws.</p>
 */
public final class SparkWitchSecondaryKeyCompat {
    private static final String KEY_API_CLASS = "dev.caecorthus.sparkwitch.api.client.SecondarySkillKeyApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparkwitch");
    private static final @Nullable KeyApi KEY_API = findKeyApi();
    private static boolean invokeFailed;

    private SparkWitchSecondaryKeyCompat() {
    }

    /**
     * Puts {@code onPressed} on Role Skill 2 for {@code roleId}; SparkWitch runs it on the client thread only while the
     * local player's role id matches. False when the hook is unavailable or the role already has a handler.
     * 为 {@code roleId} 挂接“职业技能 2”；SparkWitch 仅在本地玩家职业匹配时于客户端线程调用。
     * 钩子不可用或该职业已有处理器时返回 false。
     */
    public static boolean register(Identifier roleId, Runnable onPressed) {
        if (KEY_API == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(KEY_API.register().invoke(null, roleId, onPressed));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            warnInvokeFailed("register", e);
            return false;
        }
    }

    /** Localized Role Skill 2 key name, or null when unavailable. / “职业技能 2”当前按键名；不可用时为 null。 */
    public static @Nullable Text boundKeyText() {
        if (KEY_API == null) {
            return null;
        }
        try {
            return KEY_API.boundKeyText().invoke(null) instanceof Text text ? text : null;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError e) {
            warnInvokeFailed("boundKeyText", e);
            return null;
        }
    }

    /** Both methods resolve together, or the hook is unavailable. / 两个方法须同时解析成功，否则视为钩子不可用。 */
    private static @Nullable KeyApi findKeyApi() {
        if (!INSTALLED) {
            return null;
        }
        try {
            Class<?> api = Class.forName(KEY_API_CLASS);
            Method register = api.getMethod("register", Identifier.class, Runnable.class);
            Method boundKeyText = api.getMethod("boundKeyText");
            if (Modifier.isStatic(register.getModifiers())
                    && register.getReturnType() == boolean.class
                    && Modifier.isStatic(boundKeyText.getModifiers())
                    && Text.class.isAssignableFrom(boundKeyText.getReturnType())) {
                return new KeyApi(register, boundKeyText);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            // Older SparkWitch without the facade; warned once below. / 旧版 SparkWitch 没有该门面，下方记录一次。
        }
        SparkStrength.LOGGER.warn("SparkWitch secondary skill key bridge disabled: installed SparkWitch lacks "
                + "{}.register(Identifier, Runnable) / boundKeyText().", KEY_API_CLASS);
        return null;
    }

    private static void warnInvokeFailed(String method, Throwable error) {
        if (!invokeFailed) {
            invokeFailed = true;
            SparkStrength.LOGGER.warn("SparkWitch secondary skill key call {} failed.", method, error);
        }
    }

    private record KeyApi(Method register, Method boundKeyText) {
    }
}
