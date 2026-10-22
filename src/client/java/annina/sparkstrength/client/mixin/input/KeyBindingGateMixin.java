package annina.sparkstrength.client.mixin.input;

import annina.sparkstrength.client.input.ClientKeyGate;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link KeyBinding} side of the client key gate; the rules and their order live in {@link ClientKeyGate}. One HEAD
 * hook per method for the Engineer stun (cancelling, so a stunned call skips every RETURN hook), one RETURN hook per
 * polled method for the rest. Same mixin priority as the hooks it replaces (default), so its place among other mods'
 * hooks on these methods is unchanged.
 * 客户端按键闸门的 {@link KeyBinding} 部分；规则与顺序见 {@link ClientKeyGate}。每个方法一个 HEAD 钩子处理工程师定身（可取消，
 * 定身时的调用会跳过所有 RETURN 钩子），每个查询方法一个 RETURN 钩子处理其余规则。mixin 优先级与被取代的钩子相同（默认），
 * 因此它在其他模组同方法钩子中的位置不变。
 */
@Mixin(KeyBinding.class)
public abstract class KeyBindingGateMixin {
    @Shadow
    private int timesPressed;

    @Inject(method = "onKeyPressed", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$dropPressWhenStunned(InputUtil.Key key, CallbackInfo ci) {
        if (ClientKeyGate.dropsPress()) {
            ci.cancel();
        }
    }

    @Inject(method = "wasPressed", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$blockWasPressedWhenStunned(CallbackInfoReturnable<Boolean> cir) {
        if (ClientKeyGate.releasesEveryKey()) {
            this.timesPressed = 0;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$blockIsPressedWhenStunned(CallbackInfoReturnable<Boolean> cir) {
        if (ClientKeyGate.releasesEveryKey()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
    private boolean sparkstrength$gateHeld(boolean original) {
        return ClientKeyGate.held((KeyBinding) (Object) this, original);
    }

    @ModifyReturnValue(method = "wasPressed", at = @At("RETURN"))
    private boolean sparkstrength$gateClicked(boolean original) {
        return ClientKeyGate.clicked((KeyBinding) (Object) this, original);
    }
}
