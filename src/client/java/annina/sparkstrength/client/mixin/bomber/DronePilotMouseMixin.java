package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drone pilot mouse: look goes to the drone, the hotbar does not scroll.
 * <ul>
 *   <li>Look: wraps the single {@code changeLookDirection(DD)V} call in {@code updateMouse}, so it chains with
 *   Perfumer's aroma wrapper and add-on wrappers on the same call (whichever is outer, the body never turns while
 *   piloting); HEAD cancels of {@code updateMouse} (Engineer capture, riot shield) still freeze the view.</li>
 *   <li>Scroll: only the hotbar call is skipped; screen scrolling (chat) is untouched.</li>
 * </ul>
 * Both are pass-through unless the local player pilots a drone.
 * 无人机驾驶的鼠标：视角转给无人机，快捷栏不随滚轮切换。
 * 视角：包装 updateMouse 中唯一一处 changeLookDirection(DD)V 调用，可与调香师香薰及附属模组在同一调用上的包装串联
 * （无论谁在外层，驾驶时本体都不会转动）；对 updateMouse 的 HEAD 取消（工程师捕捉、防暴盾）仍会冻结视角。
 * 滚轮：只跳过快捷栏调用，界面滚动（聊天）不受影响。未驾驶时两者都直接放行。
 */
@Mixin(Mouse.class)
public abstract class DronePilotMouseMixin {
    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void sparkstrength$steerDrone(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY,
                                         Operation<Void> original) {
        if (!DronePilotClient.steer(cursorDeltaX, cursorDeltaY)) {
            original.call(player, cursorDeltaX, cursorDeltaY);
        }
    }

    @WrapWithCondition(method = "onMouseScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V"))
    private boolean sparkstrength$lockHotbarScroll(PlayerInventory inventory, double amount) {
        return !DronePilotClient.blocksBodyActions();
    }
}
