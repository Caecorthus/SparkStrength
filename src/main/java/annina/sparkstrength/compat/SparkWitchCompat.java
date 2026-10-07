package annina.sparkstrength.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * SparkWitch 软兼容桥。
 *
 * <p>SparkStrength 不能在编译期依赖 SparkWitch，否则单独安装任意一方都会变成硬依赖。
 * 这里仅在运行时检测到 sparkwitch 时反射调用它暴露的稳定 compat 门面。</p>
 */
public final class SparkWitchCompat {
    private static final String MOD_ID = "sparkwitch";
    private static final String KIDNAPPER_COMPAT = "dev.caecorthus.sparkwitch.compat.SparkWitchKidnapperCompat";
    private static final String API = "dev.caecorthus.sparkwitch.api.SparkWitchApi";

    private SparkWitchCompat() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static @Nullable ItemStack createKidnapperDrugStack() {
        Object result = invoke("createKnockoutDrugStack");
        return result instanceof ItemStack stack ? stack : null;
    }

    public static @Nullable ShopEntry createKidnapperDrugShopEntry() {
        Object result = invoke("createKnockoutDrugShopEntry");
        return result instanceof ShopEntry entry ? entry : null;
    }

    public static @Nullable ShopEntry createKidnapperDrugShopEntry(PlayerEntity player) {
        Object result = invoke("createKnockoutDrugShopEntry", new Class<?>[]{PlayerEntity.class}, player);
        return result instanceof ShopEntry entry ? entry : null;
    }

    /**
     * SparkWitch Control Expert stun, read through its public facade {@code SparkWitchApi.isControlExpertStunned}.
     * False without SparkWitch, with an older facade, or when the call fails, so drones are then never stun-locked.
     * SparkWitch 控场专家眩晕，经其公开门面 SparkWitchApi.isControlExpertStunned 读取。未安装 SparkWitch、门面较旧或调用失败时
     * 返回 false，此时无人机不会被眩晕锁住。
     */
    public static boolean isControlExpertStunned(@Nullable PlayerEntity player) {
        Method method = ControlExpertStunQuery.METHOD;
        if (player == null || method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, player));
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    private static @Nullable Object invoke(String methodName) {
        return invoke(methodName, new Class<?>[0]);
    }

    private static @Nullable Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        if (!isLoaded()) {
            return null;
        }
        try {
            Class<?> compat = Class.forName(KIDNAPPER_COMPAT);
            return compat.getMethod(methodName, parameterTypes).invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** Resolved once, on the first stun query. / 首次查询眩晕时解析一次。 */
    private static final class ControlExpertStunQuery {
        private static final @Nullable Method METHOD = resolve();

        private static @Nullable Method resolve() {
            if (!isLoaded()) {
                return null;
            }
            try {
                Method method = Class.forName(API).getMethod("isControlExpertStunned", PlayerEntity.class);
                return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class
                        ? method : null;
            } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
                return null;
            }
        }
    }
}
