package annina.sparkstrength.client.mixin.perfumer;

import annina.sparkstrength.client.role.perfumer.PerfumerClientEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cooling Oil instinct block, layer 1 (key): Wathe's instinct key reads as released while blinded. Needed because
 * several add-on hooks (SparkWitch Curser, Saboteur, Wind Spirit, Creative Wraith) read
 * {@code WatheClient.instinctKeybind.isPressed()} directly instead of Wathe's gates. Like Wathe's own
 * {@code KeyBindingMixin}, the original still runs, so a buffered {@code wasPressed} click is consumed, not replayed
 * after the block ends. Layers 2 and 3 live in {@link PerfumerInstinctGateMixin}.
 * 风油精本能屏蔽第 1 层（按键）：被糊眼期间 Wathe 本能键视为未按下。部分附属模组钩子（SparkWitch 诅咒者、
 * 破坏者、风精灵、创造冤魂）直接读取 {@code WatheClient.instinctKeybind.isPressed()}，不经过 Wathe 的门控，
 * 因此需要这一层。与 Wathe 自身的 {@code KeyBindingMixin} 一样原方法照常执行，缓存的 {@code wasPressed}
 * 点击会被消耗，不会在屏蔽结束后补发。第 2、3 层见 {@link PerfumerInstinctGateMixin}。
 */
@Mixin(KeyBinding.class)
public abstract class PerfumerInstinctKeyMixin {
    @ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
    private boolean sparkstrength$blockInstinctHeldWhileBlinded(boolean original) {
        return original && !PerfumerClientEffects.blocksInstinctKey((KeyBinding) (Object) this);
    }

    @ModifyReturnValue(method = "wasPressed", at = @At("RETURN"))
    private boolean sparkstrength$blockInstinctClickWhileBlinded(boolean original) {
        return original && !PerfumerClientEffects.blocksInstinctKey((KeyBinding) (Object) this);
    }
}
