package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional SparkTraits bridge. Missing or incompatible SparkTraits disables trait-only bonuses.
 * 可选 SparkTraits 桥接；缺失或不兼容时关闭仅天赋加成，不影响基础玩法。
 */
public final class SparkTraitsCompat {
    private static final String MOD_ID = "sparktraits";
    private static final Identifier IMPOSTOR_ID = Identifier.of("sparktraits", "impostor");
    private static final Method HAS_ACTIVE_TRAIT = findHasActiveTraitMethod();

    private SparkTraitsCompat() {
    }

    public static boolean hasImpostor(PlayerEntity player) {
        if (player == null || HAS_ACTIVE_TRAIT == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(HAS_ACTIVE_TRAIT.invoke(null, player, IMPOSTOR_ID));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    private static Method findHasActiveTraitMethod() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            // Reflect only the public facade so internal SparkTraits package moves cannot break this optional seam.
            // 只反射公开门面，避免 SparkTraits 内部包移动破坏这个可选兼容接缝。
            Class<?> api = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi");
            return api.getMethod("hasActiveTrait", PlayerEntity.class, Identifier.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }
}
