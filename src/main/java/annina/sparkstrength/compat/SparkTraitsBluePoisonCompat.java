package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Optional bridge to SparkTraits' public blue-poison facade, used by the Toxicologist buff.
 * Only {@code dev.caecorthus.sparktraits.api.SparkTraitsApi} is reflected; every method is optional and fails closed,
 * so a missing or older SparkTraits simply disables Blue Vitriol, Blue Belladonna and the blue passive.
 * 毒理学家增强使用的 SparkTraits 蓝毒公开门面可选桥接。只反射公开门面；每个方法都可缺失且缺失时视为不可用，
 * 因此没装或旧版 SparkTraits 只会让蓝矾、蓝颠茄和蓝毒被动失效。
 */
public final class SparkTraitsBluePoisonCompat {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparktraits");
    private static final Method CONVERT_PLATE_POISON_TO_BLUE = findOptionalMethod(
            "convertPlatePoisonToBlue", boolean.class, World.class, BlockPos.class, UUID.class);
    private static final Method CONVERT_BED_POISON_TO_BLUE = findOptionalMethod(
            "convertBedPoisonToBlue", boolean.class, World.class, BlockPos.class, UUID.class);
    private static final Method CONVERT_STACK_POISON_TO_BLUE = findOptionalMethod(
            "convertStackPoisonToBlue", boolean.class, ItemStack.class, UUID.class);
    private static final Method MARK_STACK_BLUE_POISON = findOptionalMethod(
            "markStackBluePoison", void.class, ItemStack.class, UUID.class);
    private static final Method GET_STACK_BLUE_POISONER = findOptionalMethod(
            "getStackBluePoisoner", UUID.class, ItemStack.class);
    private static final Method APPLY_BLUE_TRAP = findOptionalMethod(
            "applyBlueTrap", void.class, ServerPlayerEntity.class, UUID.class);
    private static final Method APPLY_BLUE_SANITY_DRAIN = findOptionalMethod(
            "applyBlueSanityDrain", void.class, ServerPlayerEntity.class, int.class);
    private static final Method GET_BLUE_SANITY_DRAIN_TICKS = findOptionalMethod(
            "getBlueSanityDrainTicks", int.class, PlayerEntity.class);
    private static final Method REGISTER_BLUE_SANITY_DRAIN_EXEMPTION = findOptionalMethod(
            "registerBlueSanityDrainExemption", void.class, Predicate.class);

    private SparkTraitsBluePoisonCompat() {
    }

    /**
     * True only when every blue-poison facade method the Toxicologist buff needs is present.
     * Shop listings never depend on this (client/server entry order must match); purchases and uses check it server-side.
     * 仅当毒理学家增强所需的全部蓝毒门面方法都存在时为 true。商店列表不依赖它（客户端与服务端条目顺序必须一致），
     * 只在服务端购买和使用时检查。
     */
    public static boolean isBluePoisonApiAvailable() {
        return CONVERT_PLATE_POISON_TO_BLUE != null
                && CONVERT_BED_POISON_TO_BLUE != null
                && CONVERT_STACK_POISON_TO_BLUE != null
                && MARK_STACK_BLUE_POISON != null
                && GET_STACK_BLUE_POISONER != null
                && APPLY_BLUE_TRAP != null
                && APPLY_BLUE_SANITY_DRAIN != null
                && GET_BLUE_SANITY_DRAIN_TICKS != null
                && REGISTER_BLUE_SANITY_DRAIN_EXEMPTION != null;
    }

    /** Server-side: swaps a plate's native Wathe poison for blue poison owned by {@code poisoner}.
     *  服务端：把餐盘上的 Wathe 原生毒换成归属 {@code poisoner} 的蓝毒。 */
    public static boolean convertPlatePoisonToBlue(World world, BlockPos pos, UUID poisoner) {
        return world != null && pos != null && poisoner != null
                && Boolean.TRUE.equals(invokeOptional(CONVERT_PLATE_POISON_TO_BLUE, world, pos, poisoner));
    }

    /** Server-side: swaps a bed's native scorpion for a blue scorpion owned by {@code poisoner}.
     *  服务端：把床上的原生蝎子换成归属 {@code poisoner} 的蓝毒蝎子。 */
    public static boolean convertBedPoisonToBlue(World world, BlockPos pos, UUID poisoner) {
        return world != null && pos != null && poisoner != null
                && Boolean.TRUE.equals(invokeOptional(CONVERT_BED_POISON_TO_BLUE, world, pos, poisoner));
    }

    /** Swaps a food/drink stack's native poison marker for a blue marker owned by {@code poisoner}.
     *  把食物/饮品上的原生毒标记换成归属 {@code poisoner} 的蓝毒标记。 */
    public static boolean convertStackPoisonToBlue(ItemStack stack, UUID poisoner) {
        return stack != null && !stack.isEmpty() && poisoner != null
                && Boolean.TRUE.equals(invokeOptional(CONVERT_STACK_POISON_TO_BLUE, stack, poisoner));
    }

    /** Stamps a blue-poison marker owned by {@code poisoner}; returns false when the facade is missing.
     *  写入归属 {@code poisoner} 的蓝毒标记；门面缺失时返回 false。 */
    public static boolean markStackBluePoison(ItemStack stack, UUID poisoner) {
        if (stack == null || stack.isEmpty() || poisoner == null || MARK_STACK_BLUE_POISON == null) {
            return false;
        }
        invokeOptional(MARK_STACK_BLUE_POISON, stack, poisoner);
        return true;
    }

    public static @Nullable UUID getStackBluePoisoner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return invokeOptional(GET_STACK_BLUE_POISONER, stack) instanceof UUID uuid ? uuid : null;
    }

    /** Server-side: springs a blue food trap with SparkTraits' alignment rules (sanity drain for civilians, lethal otherwise).
     *  服务端：按 SparkTraits 阵营规则触发蓝毒食物陷阱（好人扣理智，其余致死蓝毒）。 */
    public static void applyBlueTrap(ServerPlayerEntity target, UUID poisoner) {
        if (target != null && poisoner != null) {
            invokeOptional(APPLY_BLUE_TRAP, target, poisoner);
        }
    }

    /** Server-side: extends the blue sanity-drain window to at least {@code ticks}.
     *  服务端：把蓝毒扣理智窗口延长到至少 {@code ticks}。 */
    public static void applyBlueSanityDrain(ServerPlayerEntity target, int ticks) {
        if (target != null && ticks > 0) {
            invokeOptional(APPLY_BLUE_SANITY_DRAIN, target, ticks);
        }
    }

    /** Server-authoritative remaining blue sanity-drain ticks; 0 on clients or when the facade is missing.
     *  服务端权威的蓝毒扣理智剩余 tick；客户端或门面缺失时为 0。 */
    public static int getBlueSanityDrainTicks(PlayerEntity player) {
        if (player == null) {
            return 0;
        }
        return invokeOptional(GET_BLUE_SANITY_DRAIN_TICKS, player) instanceof Integer ticks ? Math.max(0, ticks) : 0;
    }

    /** Registers a server predicate that skips SparkTraits' blue sanity drain; returns false when unsupported.
     *  注册服务端谓词以跳过 SparkTraits 的蓝毒扣理智；不支持时返回 false。 */
    public static boolean registerBlueSanityDrainExemption(Predicate<ServerPlayerEntity> exemption) {
        if (exemption == null || REGISTER_BLUE_SANITY_DRAIN_EXEMPTION == null) {
            return false;
        }
        invokeOptional(REGISTER_BLUE_SANITY_DRAIN_EXEMPTION, exemption);
        return true;
    }

    private static Method findOptionalMethod(String name, Class<?> returnType, Class<?>... parameters) {
        if (!INSTALLED) {
            return null;
        }
        try {
            Method method = Class.forName(API_CLASS).getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType ? method : null;
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
