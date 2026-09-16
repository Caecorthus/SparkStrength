package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class M67MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void sparkstrength$m67CancelIntent(long window, int button, int action,
                                              int modifiers, CallbackInfo ci) {
        M67Client.mouse(window, button, action);
    }

    @WrapOperation(method = "onMouseScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(D)V"))
    private void sparkstrength$m67CancelBeforeScroll(PlayerInventory inventory, double amount,
                                                     Operation<Void> original) {
        if (amount != 0.0) {
            M67Client.cancel();
        }
        original.call(inventory, amount);
        M67Client.refreshEquipment();
    }
}
