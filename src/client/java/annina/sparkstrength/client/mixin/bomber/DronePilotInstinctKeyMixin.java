package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Killer instinct is off while piloting: Wathe's instinct key reads as released, which also closes Wathe's own
 * {@code isInstinctEnabled*} gates (they read this key) and add-on hooks that poll the key directly. Same shape as
 * {@code PerfumerInstinctKeyMixin}: the original still runs, so a buffered click is consumed, not replayed later.
 * 驾驶时禁用杀手本能：Wathe 本能键视为未按下，这同时关闭 Wathe 自身的 isInstinctEnabled* 门控（它们读取此键）以及
 * 直接查询该键的附属模组钩子。结构与 PerfumerInstinctKeyMixin 相同：原方法照常执行，缓存的点击会被消耗而不会事后补发。
 */
@Mixin(KeyBinding.class)
public abstract class DronePilotInstinctKeyMixin {
    @ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
    private boolean sparkstrength$noInstinctHeldWhilePiloting(boolean original) {
        return original && !DronePilotClient.blocksInstinctKey((KeyBinding) (Object) this);
    }

    @ModifyReturnValue(method = "wasPressed", at = @At("RETURN"))
    private boolean sparkstrength$noInstinctClickWhilePiloting(boolean original) {
        return original && !DronePilotClient.blocksInstinctKey((KeyBinding) (Object) this);
    }
}
