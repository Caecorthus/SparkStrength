package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional bridge to SparkTraits' public facade for the Bomber drones' interaction locks.
 * Only {@code dev.caecorthus.sparktraits.api.SparkTraitsApi} is reflected; every method fails closed to {@code false},
 * so without SparkTraits (or with an older facade) drones are simply never locked by traits.
 * 炸弹客无人机交互锁使用的 SparkTraits 公开门面可选桥接。只反射公开门面；每个方法缺失时都返回 {@code false}，
 * 因此没装（或旧版）SparkTraits 时无人机不会被词条锁住。
 */
public final class SparkTraitsDroneCompat {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparktraits");
    private static final Method IS_LAST_STAND_PENDING = findOptionalMethod("isLastStandPending");
    private static final Method IS_ROLE_SKILL_BLOCKED = findOptionalMethod("isRoleSkillBlocked");

    private SparkTraitsDroneCompat() {
    }

    /** True while Last Stand owns a pending death transition for {@code player} (all interactions are locked).
     *  背水一战正持有该玩家的待决死亡转换时为 true（此时锁定一切交互）。 */
    public static boolean isLastStandPending(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invokeOptional(IS_LAST_STAND_PENDING, player));
    }

    /** True while SparkTraits blocks {@code player}'s role skills (Last Escape, silenced killer ...).
     *  SparkTraits 封锁该玩家职业技能时为 true（脱险、被沉默的杀手等）。 */
    public static boolean isRoleSkillBlocked(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invokeOptional(IS_ROLE_SKILL_BLOCKED, player));
    }

    private static Method findOptionalMethod(String name) {
        if (!INSTALLED) {
            return null;
        }
        try {
            Method method = Class.forName(API_CLASS).getMethod(name, PlayerEntity.class);
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
