package annina.sparkstrength.compat;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Optional bridge to SparkFactionAPI's public {@code api.cooldown.ForcedCooldowns}: SparkStrength never compiles
 * against it, so a {@link Proxy} implements {@code RoleSkillCooldownStore}'s abstract methods with the given functions
 * and forwards its default methods ({@code mayForce}, {@code raiseTo}, {@code extendBy}) to the interface's own code
 * through {@link InvocationHandler#invokeDefault}. Absent or incompatible SparkFactionAPI → nothing is registered.
 * Role-mechanic item clears go through {@link #clearItemCooldownKeepingForced} (plain remove without the API).
 * SparkFactionAPI 公开 {@code api.cooldown.ForcedCooldowns} 的可选桥接：SparkStrength 不在编译期依赖它，因此用 {@link Proxy}
 * 以给定函数实现 {@code RoleSkillCooldownStore} 的抽象方法，其默认方法（{@code mayForce}、{@code raiseTo}、{@code extendBy}）
 * 经 {@link InvocationHandler#invokeDefault} 交给接口自身实现。未安装或不兼容的 SparkFactionAPI → 不注册任何内容。
 * 职业机制的物品冷却清除经由 {@link #clearItemCooldownKeepingForced}（无该 API 时为普通 remove）。
 */
public final class SparkFactionCooldownCompat {
    private static final String STORE_CLASS = "dev.caecorthus.sparkfactionapi.api.cooldown.RoleSkillCooldownStore";
    private static final String REGISTRY_CLASS = "dev.caecorthus.sparkfactionapi.api.cooldown.ForcedCooldowns";
    private static final Method CLEAR_ITEM_KEEPING_FORCED = findClearItemKeepingForced();
    private static final AtomicBoolean CLEAR_FAILURE_LOGGED = new AtomicBoolean();

    private SparkFactionCooldownCompat() {
    }

    /**
     * Role-mechanic item clear (Serial Killer kill reset, Timekeeper item refresh): SparkFactionAPI's
     * {@code ForcedCooldowns.clearItemKeepingForced} removes the natural cooldown but keeps a penalty another feature
     * forced on the item (Fiend aura, Abyss Shriek, anti-tank shell). Without that API (absent or older
     * SparkFactionAPI) or if the call fails, falls back to the old plain {@code remove}. Server thread only.
     * 职业机制的物品冷却清除（连环杀手击杀重置、计时员物品刷新）：SparkFactionAPI 的
     * {@code ForcedCooldowns.clearItemKeepingForced} 清除自然冷却，但保留其他功能强制施加在该物品上的惩罚（魔人光环、
     * 聆渊尖啸、反坦克弹）。缺少该 API（未安装或旧版 SparkFactionAPI）或调用失败时，退回原先的普通 {@code remove}。
     * 仅限服务端线程。
     */
    public static void clearItemCooldownKeepingForced(ServerPlayerEntity player, Item item) {
        if (CLEAR_ITEM_KEEPING_FORCED != null) {
            try {
                CLEAR_ITEM_KEEPING_FORCED.invoke(null, player, item);
                return;
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError failure) {
                if (CLEAR_FAILURE_LOGGED.compareAndSet(false, true)) {
                    SparkStrength.LOGGER.warn("SparkFactionAPI clearItemKeepingForced failed; using a plain cooldown remove",
                            failure);
                }
            }
        }
        player.getItemCooldownManager().remove(item);
    }

    /**
     * Registers one role-skill counter; call once during mod initialization (SparkFactionAPI rejects duplicate ids).
     * SparkFactionAPI calls the functions on the server thread only. Returns whether a store was registered.
     * 注册一项职业技能计数；只在模组初始化时调用一次（SparkFactionAPI 拒绝重复 id）。SparkFactionAPI 只在服务端线程调用这些函数。
     * 返回是否注册成功。
     */
    public static boolean registerRoleSkillStore(
            Identifier id,
            Predicate<ServerPlayerEntity> appliesTo,
            ToIntFunction<ServerPlayerEntity> remainingTicks,
            Function<ServerPlayerEntity, OptionalInt> nominalTicks,
            ObjIntConsumer<ServerPlayerEntity> setRemainingTicks
    ) {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return false;
        }
        try {
            Class<?> storeType = Class.forName(STORE_CLASS);
            Method register = Class.forName(REGISTRY_CLASS).getMethod("registerRoleSkillStore", storeType);
            if (!storeType.isInterface() || !Modifier.isStatic(register.getModifiers())) {
                SparkStrength.LOGGER.warn("Incompatible SparkFactionAPI forced cooldown API; store {} not registered", id);
                return false;
            }
            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> "SparkStrengthRoleSkillCooldownStore[" + id + "]";
                    };
                }
                return switch (method.getName()) {
                    case "id" -> id;
                    case "appliesTo" -> appliesTo.test((ServerPlayerEntity) args[0]);
                    case "remainingTicks" -> remainingTicks.applyAsInt((ServerPlayerEntity) args[0]);
                    case "nominalTicks" -> nominalTicks.apply((ServerPlayerEntity) args[0]);
                    case "setRemainingTicks" -> {
                        setRemainingTicks.accept((ServerPlayerEntity) args[0], (Integer) args[1]);
                        yield null;
                    }
                    default -> {
                        if (method.isDefault()) {
                            yield InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        throw new UnsupportedOperationException(method.toString());
                    }
                };
            };
            Object store = Proxy.newProxyInstance(storeType.getClassLoader(), new Class<?>[]{storeType}, handler);
            register.invoke(null, store);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
                 | InvocationTargetException | IllegalArgumentException | LinkageError failure) {
            SparkStrength.LOGGER.warn("Could not register SparkFactionAPI forced cooldown store {}", id, failure);
            return false;
        }
    }

    private static Method findClearItemKeepingForced() {
        if (!FabricLoader.getInstance().isModLoaded("sparkfactionapi")) {
            return null;
        }
        try {
            Method method = Class.forName(REGISTRY_CLASS)
                    .getMethod("clearItemKeepingForced", ServerPlayerEntity.class, Item.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == int.class ? method : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }
}
