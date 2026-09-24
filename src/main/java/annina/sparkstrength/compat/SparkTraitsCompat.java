package annina.sparkstrength.compat;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.economy.KillerTeamEconomyRules;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional SparkTraits bridge. Missing or incompatible SparkTraits disables trait-only bonuses.
 * 可选 SparkTraits 桥接；缺失或不兼容时关闭仅天赋加成，不影响基础玩法。
 */
public final class SparkTraitsCompat {
    private static final String MOD_ID = "sparktraits";
    private static final Identifier IMPOSTOR_ID = Identifier.of("sparktraits", "impostor");
    private static final Identifier CONSCIENCE_ID = Identifier.of("sparktraits", "conscience");
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID);
    private static final Method HAS_ACTIVE_TRAIT = findHasActiveTraitMethod();
    private static final Identifier TEAM_FIRST_ID = Identifier.of("sparktraits", "team_first");
    private static final Method INTERACTION_BLOCKED = findOptionalMethod(
            "isKillerInteractionBlocked", boolean.class, PlayerEntity.class);
    private static final Method FORCED_MELEE_COOLDOWN = findOptionalMethod(
            "getForcedMeleeCooldownTicks", int.class, PlayerEntity.class, ItemStack.class);
    private static final Method CANCEL_MELEE = findOptionalMethod(
            "shouldCancelMeleeAttack", boolean.class,
            ServerPlayerEntity.class, ServerPlayerEntity.class, ItemStack.class);
    private static final Method CHARISMA_DISCOUNT = findOptionalMethod(
            "discountShopEntryForCharisma", ShopEntry.class, PlayerEntity.class, ShopEntry.class);
    private static boolean teamQueryFailed;

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

    /** Owner-synced like Impostor, so client shop listings and server validation agree.
     *  与内鬼一样同步给本人，保证客户端商店列表与服务端校验一致。 */
    public static boolean hasConscience(PlayerEntity player) {
        if (player == null || HAS_ACTIVE_TRAIT == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(HAS_ACTIVE_TRAIT.invoke(null, player, CONSCIENCE_ID));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * An installed but unreadable trait API disables only the shared economy, not existing bonuses.
     * 天赋已安装但公开 API 不可读时，仅关闭团队经济，不改变既有加成的查询行为。
     */
    public static boolean isTeamEconomyAvailable() {
        if (INSTALLED && (HAS_ACTIVE_TRAIT == null
                || !Modifier.isStatic(HAS_ACTIVE_TRAIT.getModifiers())
                || HAS_ACTIVE_TRAIT.getReturnType() != boolean.class)) {
            disableTeamEconomy();
        }
        return !teamQueryFailed;
    }

    public static boolean isGenuineKillerTeamMember(PlayerEntity player, boolean killerRole) {
        if (player == null || !isTeamEconomyAvailable()) {
            return false;
        }
        if (!INSTALLED) {
            return killerRole;
        }
        try {
            boolean conscience = (boolean) HAS_ACTIVE_TRAIT.invoke(null, player, CONSCIENCE_ID);
            boolean impostor = (boolean) HAS_ACTIVE_TRAIT.invoke(null, player, IMPOSTOR_ID);
            return KillerTeamEconomyRules.isGenuineKiller(killerRole, impostor, conscience);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            disableTeamEconomy();
            return false;
        }
    }

    public static boolean hasTeamFirst(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invokeOptional(HAS_ACTIVE_TRAIT, player, TEAM_FIRST_ID));
    }

    public static boolean isKillerInteractionBlocked(PlayerEntity player) {
        return player != null && Boolean.TRUE.equals(invokeOptional(INTERACTION_BLOCKED, player));
    }

    public static int getForcedMeleeCooldownTicks(PlayerEntity player, ItemStack weapon) {
        if (player == null || weapon == null) {
            return 0;
        }
        Object result = invokeOptional(FORCED_MELEE_COOLDOWN, player, weapon);
        return result instanceof Integer ticks ? Math.max(0, ticks) : 0;
    }

    /** Check phase before weapon locks; missing/old APIs never positively block an action.
     *  先检查脱险交互禁用，再检查武器锁；缺失或旧 API 绝不被当成禁止动作。 */
    public static boolean isMeleeActionBlocked(PlayerEntity player, ItemStack weapon) {
        return isKillerInteractionBlocked(player) || getForcedMeleeCooldownTicks(player, weapon) > 0;
    }

    /** Call only after normal role, range, target permission and charge checks, before any costs.
     *  仅在职业、距离、目标权限及次数校验后、消耗前调用；反制由服务端 Traits 结算。 */
    public static boolean shouldCancelMeleeAttack(
            ServerPlayerEntity attacker, ServerPlayerEntity victim, ItemStack weapon
    ) {
        if (attacker == null || victim == null || weapon == null) {
            return false;
        }
        return isMeleeActionBlocked(attacker, weapon)
                || Boolean.TRUE.equals(invokeOptional(CANCEL_MELEE, attacker, victim, weapon));
    }

    /** Preserve the provider wrapper: its marker prevents a second purchase discount.
     *  保留提供方包装对象：其标记防止购买时重复折扣。 */
    public static ShopEntry discountShopEntryForCharisma(PlayerEntity player, ShopEntry entry) {
        if (player == null || entry == null) {
            return entry;
        }
        Object result = invokeOptional(CHARISMA_DISCOUNT, player, entry);
        return result instanceof ShopEntry discounted ? discounted : entry;
    }

    private static Method findOptionalMethod(String name, Class<?> returnType, Class<?>... parameters) {
        if (!INSTALLED) {
            return null;
        }
        try {
            Method method = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi")
                    .getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    private static Object invokeOptional(Method method, Object... arguments) {
        if (method == null || !Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    private static void disableTeamEconomy() {
        if (!teamQueryFailed) {
            teamQueryFailed = true;
            SparkStrength.LOGGER.warn("Killer team economy disabled: installed SparkTraits public trait API is unavailable.");
        }
    }

    private static Method findHasActiveTraitMethod() {
        if (!INSTALLED) {
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
