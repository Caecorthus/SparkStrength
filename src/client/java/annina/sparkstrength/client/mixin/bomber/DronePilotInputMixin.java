package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drone pilot click capture and body lock. {@code handleInputEvents} runs only with no screen open; its HEAD turns
 * queued attack/use clicks into drone FIRE/EXIT and drains hotbar/pick/drop/swap/inventory presses before vanilla reads
 * them. The HEAD cancels on {@code doAttack}/{@code doItemUse}/{@code doItemPick} cover the held-use repeat and any
 * other caller, and block breaking is forced off. Every hook is a no-op unless the local player pilots a drone.
 * 无人机驾驶的点击捕获与本体锁定。handleInputEvents 只在没有打开界面时运行；其 HEAD 把排队的攻击/使用点击转为无人机
 * FIRE/EXIT，并在原版读取前吞掉快捷栏、选取、丢弃、换手与背包按键。对 doAttack/doItemUse/doItemPick 的 HEAD 取消覆盖
 * 长按重复使用及其他调用方，方块破坏被强制关闭。仅在本地玩家驾驶无人机时生效，否则不做任何事。
 */
@Mixin(MinecraftClient.class)
public abstract class DronePilotInputMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void sparkstrength$captureDroneClicks(CallbackInfo ci) {
        DronePilotClient.captureInput((MinecraftClient) (Object) this);
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$noBodyAttackWhilePiloting(CallbackInfoReturnable<Boolean> cir) {
        if (DronePilotClient.blocksBodyActions()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$noBodyUseWhilePiloting(CallbackInfo ci) {
        if (DronePilotClient.blocksBodyActions()) {
            ci.cancel();
        }
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$noBodyPickWhilePiloting(CallbackInfo ci) {
        if (DronePilotClient.blocksBodyActions()) {
            ci.cancel();
        }
    }

    // false lets vanilla cancel any in-progress block breaking cleanly. / 传 false 让原版正常取消进行中的方块破坏。
    @ModifyArg(method = "handleInputEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MinecraftClient;handleBlockBreaking(Z)V"))
    private boolean sparkstrength$noBlockBreakingWhilePiloting(boolean breaking) {
        return breaking && !DronePilotClient.blocksBodyActions();
    }
}
