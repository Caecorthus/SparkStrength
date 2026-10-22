package annina.sparkstrength.client.mixin.engineer;

import annina.sparkstrength.client.role.engineer.EngineerStunClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 定身期间禁用所有按键状态。
 *
 * <p>移动、跳跃、蹲下、攻击/使用对应的 key binding，以及打开背包等界面入口，
 * 最终都会查询 {@link KeyBinding#isPressed()} 或 {@link KeyBinding#wasPressed()}。</p>
 *
 * <p>Presses made during the stun are discarded, not saved up: a press is not queued while stunned, and a poll while
 * stunned drains whatever was queued, so nothing (drop, hotbar, swap...) fires when the stun ends. {@code isPressed}
 * reads the live held state, which queues nothing, so it only needs to read false. Toggle sneak/sprint latch on the
 * press itself; {@link EngineerStunnedStickyKeyMixin} keeps their state from before the stun.
 * 定身期间的按键会被丢弃而不是积攒：定身时按下不入队，定身时的查询会清空已入队的次数，因此定身结束时不会有任何操作
 * （丢弃、快捷栏、换手……）补发。{@code isPressed} 读取的是实时按住状态，不会入队，因此只需读作 false。切换式潜行/疾跑
 * 在按下时就会锁定，由 {@link EngineerStunnedStickyKeyMixin} 保持其定身前的状态。</p>
 */
@Mixin(KeyBinding.class)
public abstract class EngineerStunnedKeyBindingMixin {
    @Shadow
    private int timesPressed;

    @Inject(method = "onKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$dropPressWhenStunned(InputUtil.Key key, CallbackInfo ci) {
        if (EngineerStunClient.isLocalPlayerStunned()) {
            ci.cancel();
        }
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$blockWasPressedWhenStunned(CallbackInfoReturnable<Boolean> cir) {
        if (EngineerStunClient.isLocalPlayerStunned()) {
            this.timesPressed = 0;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$blockIsPressedWhenStunned(CallbackInfoReturnable<Boolean> cir) {
        if (EngineerStunClient.isLocalPlayerStunned()) {
            cir.setReturnValue(false);
        }
    }
}
