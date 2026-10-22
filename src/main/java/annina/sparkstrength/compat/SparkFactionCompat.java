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
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.function.Predicate;

/** Optional public faction API bridges; missing/old providers preserve standalone behavior.
 *  可选公开阵营 API 桥接；缺失或旧版提供方保持独立运行行为。 */
public final class SparkFactionCompat {
    private static final Method CAN_AFFECT = findCanAffect();
    private static final Method REGISTER_POLICE_ROLE = findPoliceRoleMethod("register", void.class);
    private static final Method CONTAINS_POLICE_ROLE = findPoliceRoleMethod("contains", boolean.class);
    private static final Method RESOLVE_BASE_FACTION = findResolveBaseFaction();
    private static final Method IS_SETTLEMENT_EXCLUDED = findSettlementExcluded();
    /** {@code [resolveEffectiveFaction, capabilities, FactionCapabilities.canUseInstinct]}, or null. / 不可用时为 null。 */
    private static final Method[] INSTINCT_CAPABILITY = findInstinctCapability();
    /** SparkWitch's witch faction id (Grand Witch, Accomplice, Curser). / SparkWitch 魔女阵营 id。 */
    public static final Identifier WITCH_FACTION_ID = Identifier.of("sparkwitch", "witch");

    private SparkFactionCompat() {
    }

    /**
     * Registers a veto with SparkFactionAPI's player-affect policies: {@code action} from any actor is denied on targets
     * {@code protectedTarget} accepts. A dynamic proxy keeps SparkFactionAPI optional; false when it is absent or old.
     * 向 SparkFactionAPI 的玩家影响策略注册一条否决：任何人对 protectedTarget 认可的目标执行 action 都会被拒绝。
     * 用动态代理保持 SparkFactionAPI 为可选依赖；缺失或旧版时返回 false。
     */
    public static boolean denyAction(Identifier action, Predicate<PlayerEntity> protectedTarget) {
        try {
            Class<?> api = Class.forName("dev.caecorthus.sparkfactionapi.api.SparkFactionApi");
            Class<?> policy = Class.forName("dev.caecorthus.sparkfactionapi.api.PlayerAffectPolicy");
            Method register = api.getMethod("registerPlayerAffectPolicy", policy);
            Object proxy = Proxy.newProxyInstance(policy.getClassLoader(), new Class<?>[]{policy}, (self, method, args) ->
                    switch (method.getName()) {
                        case "canAffect" -> !(action.equals(args[2]) && args[1] instanceof PlayerEntity target
                                && protectedTarget.test(target));
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "toString" -> "SparkStrength deny " + action;
                        default -> null;
                    });
            register.invoke(null, proxy);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
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

    /** Police tablet-network classification only: this does not change NoellesRoles allocation or faction.
     *  仅注册义警平板网络分类，不改变 NoellesRoles 的身份分配或阵营。 */
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
        // 幂等重试也保证临时初始化失败后，黑警仍可恢复义警网络资格。
        if (!registerCorruptCopPoliceRole()) {
            return null;
        }
        try {
            return (Boolean) CONTAINS_POLICE_ROLE.invoke(null, role.identifier());
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    /** Role-only base faction, deterministic on client and server; null means the API is unavailable.
     *  仅由身份决定的基础阵营，客户端与服务端结果一致；null 表示 API 不可用。 */
    public static @Nullable Identifier baseFaction(@Nullable Role role) {
        if (role == null || RESOLVE_BASE_FACTION == null) {
            return null;
        }
        try {
            Object result = RESOLVE_BASE_FACTION.invoke(null, role);
            return result instanceof Identifier id ? id : null;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    /** SparkWitch hard-depends on SparkFactionAPI, so an unavailable API exactly means no witch roles exist.
     *  SparkWitch 硬依赖 SparkFactionAPI，因此 API 不可用恰好意味着不存在魔女身份。 */
    public static boolean isWitchFactionRole(@Nullable Role role) {
        return WITCH_FACTION_ID.equals(baseFaction(role));
    }

    /**
     * Whether the player's effective faction declares an instinct key ({@code FactionCapabilities.canUseInstinct}):
     * Wathe killers, SparkWitch's witch faction and the Murderous Witch today. Per faction only, so role-specific keys
     * (Insider, Wind Spirit, ...) are not covered; false when the API is absent or old.
     * 玩家的有效阵营是否声明了本能键（FactionCapabilities.canUseInstinct）：目前为 Wathe 杀手、SparkWitch 魔女阵营与杀意魔女。
     * 只按阵营判断，因此不覆盖职业专属按键（内应、风精灵……）；API 缺失或旧版时返回 false。
     */
    public static boolean canUseInstinct(PlayerEntity player, GameWorldComponent game) {
        if (INSTINCT_CAPABILITY == null) {
            return false;
        }
        try {
            Object faction = INSTINCT_CAPABILITY[0].invoke(null, player, game);
            Object capabilities = faction == null ? null : INSTINCT_CAPABILITY[1].invoke(null, faction);
            return capabilities != null && Boolean.TRUE.equals(INSTINCT_CAPABILITY[2].invoke(capabilities));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    private static Method[] findInstinctCapability() {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("dev.caecorthus.sparkfactionapi.api.SparkFactionApi");
            Method resolve = api.getMethod("resolveEffectiveFaction", PlayerEntity.class, GameWorldComponent.class);
            Method capabilities = api.getMethod("capabilities", Identifier.class);
            Method canUseInstinct = capabilities.getReturnType().getMethod("canUseInstinct");
            return Modifier.isStatic(resolve.getModifiers()) && resolve.getReturnType() == Identifier.class
                    && Modifier.isStatic(capabilities.getModifiers()) && canUseInstinct.getReturnType() == boolean.class
                    ? new Method[]{resolve, capabilities, canUseInstinct}
                    : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Whether SparkFactionAPI's {@code SettlementExclusions} (0.1.5.16+) says this player never counts as a living player
     * for round settlement (SparkWitch's Black Raven). It reads the raw role, never {@code isRole}, and checks no
     * liveness: callers keep their own alive test. Fails closed: without SparkFactionAPI, with an older one lacking the
     * class, or on any reflective failure nobody is excluded, which is the standalone behaviour.
     * SparkFactionAPI 的 SettlementExclusions（0.1.5.16 起）是否判定该玩家在回合结算中不算存活玩家（SparkWitch 的黑羽鸦）。
     * 读取原始身份而非 isRole，且不判断存活，调用方保留自己的存活判断。失败即关闭：未安装 SparkFactionAPI、旧版缺少该类
     * 或反射失败时不排除任何人，即独立运行时的行为。
     */
    public static boolean isSettlementExcluded(@Nullable GameWorldComponent game, @Nullable UUID player) {
        if (IS_SETTLEMENT_EXCLUDED == null || game == null || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(IS_SETTLEMENT_EXCLUDED.invoke(null, game, player));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    private static Method findSettlementExcluded() {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return null;
        }
        try {
            Method method = Class.forName("dev.caecorthus.sparkfactionapi.api.settlement.SettlementExclusions")
                    .getMethod("isExcluded", GameWorldComponent.class, UUID.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findResolveBaseFaction() {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return null;
        }
        try {
            Method method = Class.forName("dev.caecorthus.sparkfactionapi.api.SparkFactionApi")
                    .getMethod("resolveBaseFaction", Role.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Identifier.class ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
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
