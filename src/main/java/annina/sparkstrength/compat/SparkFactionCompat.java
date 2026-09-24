package annina.sparkstrength.compat;

import annina.sparkstrength.role.corruptcop.CorruptCopRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Optional public faction API bridges; missing/old providers preserve standalone behavior.
 *  可选公开阵营 API 桥接；缺失或旧版提供方保持独立运行行为。 */
public final class SparkFactionCompat {
    private static final Method CAN_AFFECT = findCanAffect();
    private static final Method REGISTER_POLICE_ROLE = findPoliceRoleMethod("register", void.class);
    private static final Method CONTAINS_POLICE_ROLE = findPoliceRoleMethod("contains", boolean.class);

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

    /** Shopping classification only: this does not change NoellesRoles allocation or faction.
     *  仅注册购物分类，不改变 NoellesRoles 的身份分配或阵营。 */
    public static boolean registerCorruptCopPoliceRole() {
        if (REGISTER_POLICE_ROLE == null || CONTAINS_POLICE_ROLE == null) {
            return false;
        }
        try {
            REGISTER_POLICE_ROLE.invoke(null, CorruptCopRules.CORRUPT_COP_ID);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    /** Null means unavailable, not a negative category result; callers retain their legacy fallback.
     *  null 表示 API 不可用而非“非警职”；调用方须保留旧版回退规则。
     *  Only method discovery is cached so registrations made later remain visible.
     *  仅缓存方法发现，确保稍后注册的身份立即可见。 */
    public static @Nullable Boolean isPoliceRole(@Nullable Role role) {
        if (role == null) {
            return false;
        }
        // Idempotent retry also preserves Corrupt Cop after a transient initialization failure.
        // 幂等重试也保证临时初始化失败后，黑警仍可恢复购物资格。
        if (!registerCorruptCopPoliceRole()) {
            return null;
        }
        try {
            return (Boolean) CONTAINS_POLICE_ROLE.invoke(null, role.identifier());
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findPoliceRoleMethod(String name, Class<?> returnType) {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return null;
        }
        try {
            Method method = Class.forName("dev.caecorthus.sparkfactionapi.api.PoliceRoles")
                    .getMethod(name, Identifier.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
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
