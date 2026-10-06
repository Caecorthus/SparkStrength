package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional bridge to SparkTraits' public facade for the Pathogen's T-Virus: SparkTraits' fake corpses (Depression,
 * Last Stand pending) and deaths Last Stand still owns must never be revived. Only
 * {@code dev.caecorthus.sparktraits.api.SparkTraitsApi} is reflected. Without SparkTraits, or with an older facade,
 * both answers are false, so the revive keeps its own checks: the body owner must be in Wathe's dead set and spectating.
 * 病原体 T病毒使用的 SparkTraits 公开门面可选桥接：SparkTraits 的假尸体（抑郁、背水一战待决）以及仍由背水一战接管的
 * 死亡绝不能被复活。只反射公开门面；未安装或门面较旧时两个查询都返回 false，复活仍保留自身检查：尸体主人必须在 Wathe
 * 死亡名单中且处于旁观。
 */
public final class SparkTraitsReviveCompat {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparktraits");
    private static final Method IS_FAKE_DEATH_BODY = findOptionalMethod("isFakeDeathBody", Entity.class);
    private static final Method IS_LAST_STAND_DEATH_INTERCEPTED =
            findOptionalMethod("isLastStandDeathIntercepted", PlayerEntity.class);

    private SparkTraitsReviveCompat() {
    }

    public static boolean isFakeDeathBody(Entity body) {
        return body != null && Boolean.TRUE.equals(invokeOptional(IS_FAKE_DEATH_BODY, body));
    }

    public static boolean isLastStandDeathIntercepted(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invokeOptional(IS_LAST_STAND_DEATH_INTERCEPTED, player));
    }

    private static Method findOptionalMethod(String name, Class<?>... parameters) {
        if (!INSTALLED) {
            return null;
        }
        try {
            Method method = Class.forName(API_CLASS).getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    private static Object invokeOptional(Method method, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }
}
