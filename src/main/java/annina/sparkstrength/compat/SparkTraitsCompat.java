package annina.sparkstrength.compat;

import dev.doctor4t.wathe.api.Role;
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
    private static final Identifier CONSCIENCE_ID = Identifier.of("sparktraits", "conscience");
    private static final Identifier IMPOSTOR_ID = Identifier.of("sparktraits", "impostor");
    private static final Method HAS_ACTIVE_TRAIT = findHasActiveTraitMethod();

    private SparkTraitsCompat() {
    }

    public static boolean hasImpostor(PlayerEntity player) {
        return hasTrait(player, IMPOSTOR_ID);
    }

    /**
     * 判断玩家是否属于 SparkTraits 规则下的有效杀手阵营。
     *
     * <p>规则与 SparkTraits 的公开有效阵营实现保持一致：内鬼词条会把原始好人
     * 翻为杀手；善良词条会把原始杀手翻为好人。若两个词条同时存在，则按
     * SparkTraits 的优先级由内鬼词条负责判定为杀手。</p>
     */
    public static boolean isEffectiveKiller(Role role, PlayerEntity player) {
        if (role == null) {
            return false;
        }
        if (hasImpostor(player)) {
            return true;
        }
        if (hasConscience(player)) {
            return false;
        }
        return role.canUseKiller();
    }

    /**
     * 判断玩家是否持有指定的 SparkTraits 词条。
     *
     * <p>这里通过 SparkTraits 公开门面反射调用，避免 SparkStrength 直接依赖
     * SparkTraits 的内部实现包。mod 不存在、API 不兼容或反射失败时都返回 false。</p>
     */
    public static boolean hasTrait(PlayerEntity player, Identifier traitId) {
        if (player == null || traitId == null || HAS_ACTIVE_TRAIT == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(HAS_ACTIVE_TRAIT.invoke(null, player, traitId));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    /** 软兼容判断玩家是否持有善良词条。 */
    public static boolean hasConscience(PlayerEntity player) {
        return hasTrait(player, CONSCIENCE_ID);
    }

    /**
     * 判断玩家是否属于“有效好人阵营”。
     *
     * <p>规则优先级和 SparkTraits 保持一致：
     * 善良词条优先视作好人；内鬼词条优先视作杀手；没有 SparkTraits 或
     * 没有相关词条时回退到 Wathe 原始角色阵营。</p>
     */
    public static boolean isEffectiveCivilian(Role role, PlayerEntity player) {
        if (role == null) {
            return false;
        }
        if (hasConscience(player)) {
            return true;
        }
        if (hasImpostor(player)) {
            return false;
        }
        return role.isInnocent();
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
