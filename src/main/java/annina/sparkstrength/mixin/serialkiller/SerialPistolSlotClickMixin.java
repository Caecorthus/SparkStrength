package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.role.serialkiller.SerialPistolInventoryRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server authority: any slot click that would move a serial pistol (into a container, the crafting grid, another
 * slot, the cursor or out of the window) is refused and the client's prediction is resynced.
 * 服务端权威：任何会移动连环手枪的栏位点击（放入容器、合成格、其他栏位、光标或窗口外）都会被拒绝，并重新同步客户端预测。
 */
@Mixin(ScreenHandler.class)
public abstract class SerialPistolSlotClickMixin {
    @Inject(
            method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkstrength$keepSerialPistolInPlace(int slotIndex, int button, SlotActionType actionType,
                                                       PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity
                && SerialPistolInventoryRules.blocksSlotClick(player, slotIndex, button, actionType)) {
            player.currentScreenHandler.sendContentUpdates();
            ci.cancel();
        }
    }
}
