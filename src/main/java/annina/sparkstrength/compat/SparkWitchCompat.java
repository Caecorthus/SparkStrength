package annina.sparkstrength.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
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

    /**
     * Client pick for the Serial Killer pistols, through SparkWitch's frozen
     * {@code SparkWitchApi.preferNearerGunWorldTarget(PlayerEntity, HitResult, double)}: the Seeker device step SparkWitch
     * wraps inside Wathe's {@code RevolverItem#use}, which the pistols' own {@code use} never runs. Returns
     * {@code gunTarget} unchanged without SparkWitch, with an older facade, or when the call fails.
     * 连环杀手手枪的客户端选靶，经 SparkWitch 冻结的 {@code SparkWitchApi.preferNearerGunWorldTarget} 补上 SparkWitch 包装在
     * Wathe {@code RevolverItem#use} 内、而手枪自己的 {@code use} 不会执行的搜寻者设备一步。未安装 SparkWitch、门面较旧或调用
     * 失败时原样返回 {@code gunTarget}。
     */
    public static HitResult preferNearerGunWorldTarget(PlayerEntity shooter, HitResult gunTarget, double range) {
        Method method = GunWorldHitQueries.PREFER_NEARER_TARGET;
        if (method == null || shooter == null || gunTarget == null) {
            return gunTarget;
        }
        try {
            return method.invoke(null, shooter, gunTarget, range) instanceof HitResult result ? result : gunTarget;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return gunTarget;
        }
    }

    /**
     * Server thread: lets SparkWitch end a Magician puppet or break a Seeker device that a pistol shot's non-player
     * target id names, exactly as for a Wathe revolver shot, through its frozen
     * {@code SparkWitchApi.hitGunWorldTarget(ServerPlayerEntity, Entity, ItemStack, double)}. Call where Wathe's receiver
     * records the shot (after the click, before the record and cooldown); the caller then finishes the shot as a miss.
     * False (nothing happens) without SparkWitch, with an older facade, or when the call fails.
     * 服务端线程：经 SparkWitch 冻结的 {@code SparkWitchApi.hitGunWorldTarget}，让 SparkWitch 像处理 Wathe 左轮射击一样结束
     * 手枪射击中非玩家目标 id 所指的魔术师皮套或打坏搜寻者设备。在 Wathe 接收器记录这一枪的位置调用（扳机声之后、记录与冷却
     * 之前）；调用方随后按未命中收尾。未安装 SparkWitch、门面较旧或调用失败时返回 false（无事发生）。
     */
    public static boolean hitGunWorldTarget(ServerPlayerEntity shooter, Entity target, ItemStack gun,
                                            double maxDistance) {
        Method method = GunWorldHitQueries.HIT_WORLD_TARGET;
        if (method == null || shooter == null || target == null || gun == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, shooter, target, gun, maxDistance));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Server authority: whether {@code target} is the active SparkWitch Vendetta bound to {@code actor}, through the
     * frozen {@code SparkWitchApi.isBoundKillerTargetingVendetta(PlayerEntity, PlayerEntity)}. Wathe counts that
     * Vendetta as dead, yet a revolver shot by its bound killer still reaches killPlayer, which resolves its terminal
     * death. False without SparkWitch, with an older facade, or when the call fails.
     * 以服务端为准：{@code target} 是否为与 {@code actor} 绑定的激活 SparkWitch 仇杀客，经冻结的
     * {@code SparkWitchApi.isBoundKillerTargetingVendetta} 查询。Wathe 将该仇杀客视为已死亡，但其绑定凶手的左轮射击仍会进入
     * killPlayer 并结算其终局死亡。未安装 SparkWitch、门面较旧或调用失败时返回 false。
     */
    public static boolean isBoundKillerTargetingVendetta(PlayerEntity actor, PlayerEntity target) {
        Method method = GunWorldHitQueries.BOUND_VENDETTA;
        if (method == null || actor == null || target == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, actor, target));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
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

    /**
     * SparkWitch's add-on gun seam (2026-10-07), resolved once on first use; each method is null when SparkWitch is
     * absent or older, or its signature or return type differs.
     * SparkWitch 的附属模组枪械接缝（2026-10-07），首次使用时解析一次；SparkWitch 缺失、版本较旧或签名与返回类型不符时为 null。
     */
    private static final class GunWorldHitQueries {
        private static final @Nullable Method PREFER_NEARER_TARGET = resolve("preferNearerGunWorldTarget",
                HitResult.class, PlayerEntity.class, HitResult.class, double.class);
        private static final @Nullable Method HIT_WORLD_TARGET = resolve("hitGunWorldTarget",
                boolean.class, ServerPlayerEntity.class, Entity.class, ItemStack.class, double.class);
        private static final @Nullable Method BOUND_VENDETTA = resolve("isBoundKillerTargetingVendetta",
                boolean.class, PlayerEntity.class, PlayerEntity.class);

        private static @Nullable Method resolve(String name, Class<?> returnType, Class<?>... parameterTypes) {
            if (!isLoaded()) {
                return null;
            }
            try {
                Method method = Class.forName(PUBLIC_API).getMethod(name, parameterTypes);
                return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == returnType
                        ? method : null;
            } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
                // SparkWitch builds before 2026-10-07 lack this seam; the pistols then ignore its entities.
                // 2026-10-07 之前的 SparkWitch 没有此接缝；手枪随之忽略其实体。
                return null;
            }
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
                Method method = Class.forName(PUBLIC_API).getMethod("isControlExpertStunned", PlayerEntity.class);
                return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class
                        ? method : null;
            } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
                return null;
            }
        }
    }
}
