package annina.sparkstrength.client.mixin.perfumer;

import annina.sparkstrength.client.role.perfumer.PerfumerClientEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Aroma aim disruption on the single {@code changeLookDirection(DD)V} call in {@code updateMouse}. Vanilla runs
 * {@code updateMouse(timeDelta)} once per frame (seconds since the previous frame) only while the cursor is locked,
 * the window is focused and a player exists, so screens and pause menus never drift. The call is wrapped, not
 * redirected or cancelled: it chains with SparkWitch Control Expert's {@code @WrapWithCondition} and Seeker's
 * {@code @WrapOperation}, while HEAD cancels of the whole method (Engineer capture, NoellesRoles riot shield) still
 * freeze the view, drift included.
 * 香薰准心干扰，作用于 {@code updateMouse} 中唯一一处 {@code changeLookDirection(DD)V} 调用。原版每帧调用一次
 * {@code updateMouse(timeDelta)}（距上一帧的秒数），且仅在光标锁定、窗口聚焦且存在玩家时调用，因此打开界面或暂停时不会漂移。
 * 这里采用包装而非重定向或取消：可与 SparkWitch 控场专家的 {@code @WrapWithCondition} 与搜寻者的 {@code @WrapOperation}
 * 串联；而整段方法的 HEAD 取消（工程师捕捉、NoellesRoles 防暴盾）仍会冻结视角，包括漂移。
 */
@Mixin(Mouse.class)
public abstract class PerfumerAromaMouseMixin {
    @WrapOperation(
            method = "updateMouse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void sparkstrength$disruptAromaAim(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY,
                                               Operation<Void> original,
                                               @Local(argsOnly = true) double timeDelta) {
        PerfumerClientEffects.AromaLook look = PerfumerClientEffects.aromaLook(cursorDeltaX, cursorDeltaY, timeDelta);
        if (look == null) {
            original.call(player, cursorDeltaX, cursorDeltaY);
            return;
        }
        original.call(player, look.deltaX(), look.deltaY());
    }
}
