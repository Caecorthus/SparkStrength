package annina.sparkstrength.client.mixin.spiritualist;

import annina.sparkstrength.client.role.spiritualist.SpiritPossessionClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Possession look: the view is the Wraith's eyes, so mouse look is dropped before it reaches the body or
 * NoellesRoles' spirit redirect (the spirit then resumes facing where it did). Wraps the single
 * {@code changeLookDirection(DD)V} call in {@code updateMouse}, chaining with the drone and Perfumer wrappers.
 * Pass-through unless possessing.
 * 附身视角：画面就是冤魂的视线，因此鼠标转向在到达肉身或 NoellesRoles 灵魂重定向之前就被丢弃（灵魂随后保持原来的朝向）。
 * 包装 updateMouse 中唯一一处 changeLookDirection(DD)V 调用，与无人机、调香师的包装串联。未附身时直接放行。
 */
@Mixin(Mouse.class)
public abstract class SpiritPossessionMouseMixin {
    @WrapOperation(method = "updateMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void sparkstrength$dropLookWhilePossessing(ClientPlayerEntity player, double cursorDeltaX,
                                                       double cursorDeltaY, Operation<Void> original) {
        if (!SpiritPossessionClient.blocksLook()) {
            original.call(player, cursorDeltaX, cursorDeltaY);
        }
    }
}
