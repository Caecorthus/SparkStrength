package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class M67KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void sparkstrength$m67CancelIntent(long window, int key, int scanCode, int action,
                                              int modifiers, CallbackInfo ci) {
        M67Client.keyboard(window, key, scanCode, action);
    }
}
