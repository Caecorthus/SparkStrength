package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class M67InteractionMixin {
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$m67WaitForRelease(PlayerEntity player, Hand hand,
                                                CallbackInfoReturnable<ActionResult> cir) {
        if (player == MinecraftClient.getInstance().player) {
            M67Client.refreshEquipment();
            if (M67Client.blocksUse(player.getStackInHand(hand))) {
                cir.setReturnValue(ActionResult.PASS);
            }
        }
    }

    @Inject(method = "interactItem", at = @At("RETURN"))
    private void sparkstrength$m67RememberCharge(PlayerEntity player, Hand hand,
                                                CallbackInfoReturnable<ActionResult> cir) {
        if (player == MinecraftClient.getInstance().player && cir.getReturnValue().isAccepted()) {
            M67Client.observeUse();
        }
    }

    // Inventory actions terminate the current gesture before vanilla mutates or sends packets.
    // 物品栏操作在原版修改物品或发包之前结束本次使用，防止同类物品继承蓄力。
    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void sparkstrength$m67CancelBeforeInventory(int syncId, int slotId, int button,
                                                       SlotActionType actionType, PlayerEntity player,
                                                       CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player) {
            M67Client.cancel();
        }
    }

    @Inject(method = "clickSlot", at = @At("RETURN"))
    private void sparkstrength$m67ObserveInventory(int syncId, int slotId, int button,
                                                   SlotActionType actionType, PlayerEntity player,
                                                   CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player) {
            M67Client.refreshEquipment();
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$m67ValidateBeforeRelease(PlayerEntity player, CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player) {
            boolean cancelled = M67Client.cancelChangedCharge();
            M67Client.refreshEquipment();
            M67Client.beforeRelease();
            if (cancelled) {
                ci.cancel();
            }
        }
    }
}
