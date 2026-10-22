package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional bridge to SparkTraits' terminal death reasons: a death with a registered reason is final, so traits such as
 * Last Stand and Depression never step in. Only {@code dev.caecorthus.sparktraits.api.SparkTraitsApi} is reflected; a
 * missing or older SparkTraits makes this a no-op.
 * SparkTraits 终结死因的可选桥接：以已注册死因死亡即为最终死亡，背水一战、抑郁等词条不会介入。只反射公开门面；
 * 没装或旧版 SparkTraits 时什么也不做。
 */
public final class SparkTraitsDeathReasonCompat {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparktraits");

    private SparkTraitsDeathReasonCompat() {
    }

    /** Pass the caller's own constant; SparkTraits keeps it as is. / 传入调用方自己的常量；SparkTraits 原样保存。 */
    public static void registerTerminal(Identifier reason) {
        if (!INSTALLED || reason == null) {
            return;
        }
        try {
            Method method = Class.forName(API_CLASS).getMethod("registerTerminalDeathReason", Identifier.class);
            if (Modifier.isStatic(method.getModifiers())) {
                method.invoke(null, reason);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
                 | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            // Older SparkTraits without the facade: the traits may step in, nothing else breaks.
            // 没有该门面的旧版 SparkTraits：词条可能介入，其余不受影响。
        }
    }
}
