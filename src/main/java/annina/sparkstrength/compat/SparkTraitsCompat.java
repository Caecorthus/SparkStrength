package annina.sparkstrength.compat;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.economy.KillerTeamEconomyRules;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.doctor4t.wathe.api.Role;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional SparkTraits bridge. Missing or incompatible SparkTraits disables trait-only bonuses.
 * 可选 SparkTraits 桥接；缺失或不兼容时关闭仅天赋加成，不影响基础玩法。
 */
public final class SparkTraitsCompat {
    private static final String MOD_ID = "sparktraits";
    private static final Identifier CONSCIENCE_ID = Identifier.of("sparktraits", "conscience");
    private static final Identifier IMPOSTOR_ID = Identifier.of("sparktraits", "impostor");
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
    private static final Method THROW_CHARGE_TICKS = findOptionalMethod(
            "getThrowChargeTicks", int.class, PlayerEntity.class, int.class);
    private static final Method FINAL_MOMENT_ACTIVE = findOptionalMethod(
            "isFinalMomentActive", boolean.class, World.class);
    private static boolean teamQueryFailed;
    private static final Method SYNC_TRAIT_VISIBILITY = findSyncTraitVisibilityMethod();
    private static final Method SNAPSHOT_DEATH_TRAITS_FOR_DEBUG = findSnapshotDeathTraitsForDebugMethod();

    private SparkTraitsCompat() {
    }

    public static boolean hasImpostor(PlayerEntity player) {
        return hasTrait(player, IMPOSTOR_ID);
    }

    /**
     * 计时员普通模式的目标判断：原始好人和善良杀手可以被刷新，内鬼好人不能被刷新。
     * 该规则独立于通用“有效阵营”判断，避免改动其它职业已经依赖的优先级。
     */
    public static boolean isTimekeeperNormalTarget(Role role, PlayerEntity player) {
        return role != null
                && !hasImpostor(player)
                && (role.isInnocent() || hasConscience(player));
    }

    /** 计时员拥有 impostor 时的反转目标：非善良杀手和其它内鬼好人。 */
    public static boolean isTimekeeperReverseTarget(Role role, PlayerEntity player) {
        return role != null
                && !hasConscience(player)
                && (role.canUseKiller() || hasImpostor(player));
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

    /** Self-timed throw charges only: SparkTraits already scales launch speed, so never multiply it here.
     *  Missing, old or failing APIs keep the base charge, as does a non-positive answer.
     *  仅用于自行计时的投掷蓄力：初速已由 SparkTraits 统一放大，此处不得再乘。
     *  缺失、旧版或异常的 API 保持基础蓄力，非正数结果同样回退。 */
    public static int getThrowChargeTicks(PlayerEntity player, int baseTicks) {
        if (player == null) {
            return baseTicks;
        }
        Object result = invokeOptional(THROW_CHARGE_TICKS, player, baseTicks);
        return result instanceof Integer ticks && ticks > 0 ? ticks : baseTicks;
    }

    /** Reads SparkTraits' world-synced Final Moment flag; usable on clients. Missing or old APIs report false.
     *  读取 SparkTraits 同步到世界的终局时刻标记，客户端可用；缺失或旧版 API 一律视为未开启。 */
    public static boolean isFinalMomentActive(World world) {
        return world != null && Boolean.TRUE.equals(invokeOptional(FINAL_MOMENT_ACTIVE, world));
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

    /**
     * 软兼容通知 SparkTraits 重新同步目标的词条可见性。
     *
     * <p>调试存活命令只修改 Wathe 的 deadPlayers 集合，不会触发 Wathe 正常死亡事件；
     * 因此这里通过 SparkTraits 公共 API 请求一次组件同步。未安装 SparkTraits、版本没有
     * 该 API 或反射失败时静默跳过，不影响 SparkStrength 的基础命令。</p>
     */
    public static void syncTraitVisibility(ServerPlayerEntity player) {
        if (player == null || SYNC_TRAIT_VISIBILITY == null) {
            return;
        }
        try {
            SYNC_TRAIT_VISIBILITY.invoke(null, player);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            // SparkTraits 是可选依赖，兼容失败不能影响 Wathe 状态命令本身。
        }
    }

    /**
     * 软兼容请求 SparkTraits 创建调试用死亡词条快照。
     *
     * <p>真实死亡后的 RoleNameRenderer 使用世界级 deathTraitSnapshots 显示词条；
     * 该方法让 SparkStrength 的调试状态转换复用同一显示数据源，但不触发真实死亡流程。
     * SparkTraits 缺失或 API 不兼容时安全跳过。</p>
     */
    public static void snapshotDeathTraitsForDebug(ServerPlayerEntity player) {
        if (player == null || SNAPSHOT_DEATH_TRAITS_FOR_DEBUG == null) {
            return;
        }
        try {
            SNAPSHOT_DEATH_TRAITS_FOR_DEBUG.invoke(null, player);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            // 可选兼容失败不能阻止 deadPlayers 状态本身生效。
        }
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

    private static Method findSyncTraitVisibilityMethod() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> api = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi");
            return api.getMethod("syncTraitVisibility", ServerPlayerEntity.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findSnapshotDeathTraitsForDebugMethod() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return null;
        }
        try {
            Class<?> api = Class.forName("dev.caecorthus.sparktraits.api.SparkTraitsApi");
            return api.getMethod("snapshotDeathTraitsForDebug", ServerPlayerEntity.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }
}
