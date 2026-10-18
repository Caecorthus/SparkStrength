package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.BlackoutSightClient;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Blackout instinct rules, key layer: while {@link BlackoutSightClient} evaluates a player the key may not reveal,
 * Wathe's instinct key reads as released, for add-on hooks (SparkWitch Curser, Saboteur, Wind Spirit, Insider ...) that
 * read {@code WatheClient.instinctKeybind.isPressed()} instead of Wathe's gates. Outside that evaluation it is untouched.
 * 熄灯本能规则的按键层：BlackoutSightClient 计算本能键不能透视的玩家时，Wathe 本能键视为未按下，覆盖直接读取
 * {@code WatheClient.instinctKeybind.isPressed()} 而不经过 Wathe 入口的附属模组钩子（SparkWitch 诅咒者、破坏者、风灵、
 * 内应……）。计算之外完全不受影响。
 */
@Mixin(KeyBinding.class)
public abstract class BlackoutInstinctKeyMixin {
    @ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
    private boolean sparkstrength$releaseInstinctKey(boolean original) {
        return original && !BlackoutSightClient.releasesKey((KeyBinding) (Object) this);
    }
}
