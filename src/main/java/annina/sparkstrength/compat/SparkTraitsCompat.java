package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional SparkTraits bridge. Missing or incompatible SparkTraits simply disables trait-only bonuses.
 */
public final class SparkTraitsCompat {
    private static final String MOD_ID = "sparktraits";
    private static final Method HAS_IMPOSTOR = findHasImpostorMethod();

    private SparkTraitsCompat() {
    }

    public static boolean hasImpostor(PlayerEntity player) {
        if (player == null || HAS_IMPOSTOR == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(HAS_IMPOSTOR.invoke(null, player));
        } catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
            return false;
        }
    }

    private static Method findHasImpostorMethod() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> service = Class.forName("dev.caecorthus.sparktraits.impl.effective.EffectiveTraitService");
            return service.getMethod("hasImpostor", PlayerEntity.class);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }
}
