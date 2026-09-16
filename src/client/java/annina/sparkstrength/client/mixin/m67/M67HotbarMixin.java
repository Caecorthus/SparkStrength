package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftClient.class)
public abstract class M67HotbarMixin {
    // Cancel before vanilla can synchronize a same-item switch and subsequently release.
    // 在原版同步同类物品切槽并随后松手之前取消，避免意外投掷。
    @WrapOperation(method = "handleInputEvents", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", opcode = Opcodes.PUTFIELD))
    private void sparkstrength$m67CancelBeforeSlot(PlayerInventory inventory, int slot,
                                                   Operation<Void> original) {
        if (inventory.selectedSlot != slot) {
            M67Client.cancel();
        }
        original.call(inventory, slot);
        M67Client.refreshEquipment();
    }
}
