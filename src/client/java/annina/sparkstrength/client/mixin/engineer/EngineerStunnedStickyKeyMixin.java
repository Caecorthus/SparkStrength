package annina.sparkstrength.client.mixin.engineer;

import annina.sparkstrength.client.role.engineer.EngineerStunClient;
import net.minecraft.client.option.StickyKeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Toggle sneak/sprint during a stun. In toggle mode {@code StickyKeyBinding#setPressed(true)} flips the latch itself
 * (Keyboard#onKey calls it before onKeyPressed), and it reads {@code isPressed()}, which the stun forces to false, so
 * any press while stunned would latch it on for after the stun. Press transitions are ignored while stunned; releases
 * do nothing in toggle mode anyway, and untoggle/reset go straight to KeyBinding#setPressed, so the latch keeps the
 * state it had before the stun and nothing stays held. Hold mode is untouched: it follows the physical key.
 * 定身期间的切换式潜行/疾跑。切换模式下 {@code StickyKeyBinding#setPressed(true)} 自己翻转锁定（Keyboard#onKey 在
 * onKeyPressed 之前调用它），并读取被定身强制为 false 的 {@code isPressed()}，所以定身时的任何一次按下都会让它在定身后
 * 保持开启。定身时忽略按下；切换模式下松开本就不起作用，取消切换与重置直接走 KeyBinding#setPressed，因此锁定保持定身前的
 * 状态，也不会有按键一直处于按住状态。按住模式不受影响：它跟随实体按键。
 */
@Mixin(StickyKeyBinding.class)
public abstract class EngineerStunnedStickyKeyMixin {
    @Shadow
    @Final
    private BooleanSupplier toggleGetter;

    @Inject(method = "setPressed", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$keepToggleWhileStunned(boolean pressed, CallbackInfo ci) {
        if (pressed && this.toggleGetter.getAsBoolean() && EngineerStunClient.isLocalPlayerStunned()) {
            ci.cancel();
        }
    }
}
