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
    private static final String PUBLIC_API = "dev.caecorthus.sparkwitch.api.SparkWitchApi";
    /** Render thread only. / 仅渲染线程。 */
    private static boolean blindFeatureGateResolved;
    private static @Nullable Method blindFeatureGate;

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
     * Render thread, client presentation only: whether SparkWitch's Blind view on this client strips every feature from
     * {@code player}'s body right now ({@code SparkWitchApi#hidesFeaturesFromBlind}). A SparkStrength extra drawn
     * outside the feature loop skips itself while this holds; today that is the skateboard under a replaced (Pig) body.
     * The lookup runs once, because this is asked every frame. An absent, older or failing SparkWitch answers false, so
     * everything draws as before; a call that throws stops further calls.
     * 仅限渲染线程、仅用于客户端展示：此客户端上 SparkWitch 的盲人视图此刻是否去掉 {@code player} 身体上的全部附加层
     * （{@code SparkWitchApi#hidesFeaturesFromBlind}）。在附加层循环之外绘制的 SparkStrength 附加物在此为真时跳过自身，
     * 目前即被替换的（猪）身体下方的滑板。由于每帧都会询问，方法只查找一次。SparkWitch 缺失、版本过旧或调用失败时返回
     * false，一切照旧绘制；调用抛出异常后不再继续调用。
     */
    public static boolean hidesFeaturesFromBlind(PlayerEntity player) {
        Method gate = blindFeatureGate();
        if (gate == null || player == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(gate.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            blindFeatureGate = null;
            return false;
        }
    }

    private static @Nullable Method blindFeatureGate() {
        if (!blindFeatureGateResolved) {
            blindFeatureGateResolved = true;
            if (isLoaded()) {
                try {
                    Method method = Class.forName(PUBLIC_API).getMethod("hidesFeaturesFromBlind", PlayerEntity.class);
                    if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class) {
                        blindFeatureGate = method;
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // SparkWitch builds before 2026-10-07 have no Blind feature gate; keep drawing.
                    // 2026-10-07 之前的 SparkWitch 没有盲人附加层闸门；照旧绘制。
                }
            }
        }
        return blindFeatureGate;
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
}
