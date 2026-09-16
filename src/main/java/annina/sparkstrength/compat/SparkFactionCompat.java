package annina.sparkstrength.compat;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Optional target permission check for attacks which replace a provider's entire handler.
 *  完整接管上游攻击处理时，先通过可选公开 API 检查目标权限，不依赖 mixin 执行顺序。 */
public final class SparkFactionCompat {
    private static final Method CAN_AFFECT = findCanAffect();

    private SparkFactionCompat() {
    }

    public static boolean canAffectPlayer(PlayerEntity actor, PlayerEntity target, Identifier action) {
        if (CAN_AFFECT == null) {
            return true;
        }
        try {
            return !Boolean.FALSE.equals(CAN_AFFECT.invoke(
                    null, actor, target, action, GameWorldComponent.KEY.get(actor.getWorld())));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return true;
        }
    }

    private static Method findCanAffect() {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return null;
        }
        try {
            Method method = Class.forName("dev.caecorthus.sparkfactionapi.api.SparkFactionApi").getMethod(
                    "canAffectPlayer", PlayerEntity.class, PlayerEntity.class, Identifier.class, GameWorldComponent.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }
}
