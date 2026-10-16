package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional bridge to SparkTraits' public facade for the sabotage system: is the Master Saboteur trait active.
 * Only {@code dev.caecorthus.sparktraits.api.SparkTraitsApi#hasActiveTrait} is reflected and it fails closed, so
 * without SparkTraits nobody counts as a Master Saboteur.
 * 破坏系统使用的 SparkTraits 公开门面可选桥接：是否拥有生效中的「破坏大师」。只反射公开门面的 hasActiveTrait，
 * 缺失时返回 false，因此没装 SparkTraits 时没有人算作破坏大师。
 */
public final class SparkTraitsSabotageCompat {
    public static final Identifier MASTER_SABOTEUR_ID = Identifier.of("sparktraits", "master_saboteur");
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparktraits");
    private static final Method HAS_ACTIVE_TRAIT = findHasActiveTrait();

    private SparkTraitsSabotageCompat() {
    }

    public static boolean isMasterSaboteur(PlayerEntity player) {
        if (player == null || HAS_ACTIVE_TRAIT == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(HAS_ACTIVE_TRAIT.invoke(null, player, MASTER_SABOTEUR_ID));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    private static Method findHasActiveTrait() {
        if (!INSTALLED) {
            return null;
        }
        try {
            Method method = Class.forName(API_CLASS).getMethod("hasActiveTrait", PlayerEntity.class, Identifier.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }
}
