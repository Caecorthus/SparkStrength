package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mouse wheel can't switch the hotbar while carried by the Partygoer (the number keys are locked separately).
 * 被派对客扛着时鼠标滚轮不能切换快捷栏（数字键另行锁定）。
 */
@Mixin(PlayerInventory.class)
public abstract class PartygoerCarriedScrollMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$carriedNoScroll(double scrollAmount, CallbackInfo ci) {
        if (PartygoerCarry.isCarried(player)) {
            ci.cancel();
        }
    }
}
