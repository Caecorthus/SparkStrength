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
 * hook per method for the Engineer stun (cancelling, so a stunned call skips every RETURN hook), one RETURN hook for
 * {@code isPressed} and three for {@code wasPressed}, the middle one cancelling. Same mixin priority as the hooks it
 * replaces (default), so its place among other mods' hooks on these methods is unchanged.
 * Hooks at the same point run in declaration order: Mixin merges a mixin's methods in class-file order, prepares one
 * injector per merged method in that order, and applies a mixin's injectors of one injector order (1000 for both
 * {@code @Inject} and {@code @ModifyReturnValue}) in that list order, each inserted right before the RETURN found in
 * the prepare pass. So the three {@code wasPressed} RETURN hooks below must stay in this order.
 * 客户端按键闸门的 {@link KeyBinding} 部分；规则与顺序见 {@link ClientKeyGate}。每个方法一个 HEAD 钩子处理工程师定身（可取消，
 * 定身时的调用会跳过所有 RETURN 钩子），isPressed 一个 RETURN 钩子，wasPressed 三个，中间那个可取消。mixin 优先级与被取代的
 * 钩子相同（默认），因此它在其他模组同方法钩子中的位置不变。
 * 同一注入点的钩子按声明顺序执行：Mixin 按类文件顺序合并 mixin 的方法，按此顺序为每个合并的方法准备一个注入器，并按列表顺序
 * 应用同一注入器顺序值（@Inject 与 @ModifyReturnValue 都是 1000）的注入器，每个都插在准备阶段找到的 RETURN 之前。因此下面
 * 三个 wasPressed RETURN 钩子必须保持这个顺序。
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
    private boolean sparkstrength$clickPastDrone(boolean original) {
        return ClientKeyGate.clickedPastDrone((KeyBinding) (Object) this, original);
    }

    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkstrength$claimClick(CallbackInfoReturnable<Boolean> cir) {
        if (ClientKeyGate.claimsClick((KeyBinding) (Object) this, cir.getReturnValueZ())) {
            cir.setReturnValue(false);
        }
    }

    @ModifyReturnValue(method = "wasPressed", at = @At("RETURN"))
    private boolean sparkstrength$clickPastCoolingOil(boolean original) {
        return ClientKeyGate.clickedPastCoolingOil((KeyBinding) (Object) this, original);
    }
}
