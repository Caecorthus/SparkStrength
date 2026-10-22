package annina.sparkstrength.client.mixin.serialkiller;

import annina.sparkstrength.SparkStrengthItems;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/** 客户端快捷栏锁定：每 tick 恢复连环手枪槽；数字键写槽见 SerialKillerClientSlotWriteMixin。 */
@Mixin(MinecraftClient.class)
public abstract class SerialKillerClientSlotLockMixin {
    /**
     * 客户端快捷栏切换存在本地预测：即使服务端拒绝 UpdateSelectedSlotC2SPacket，
     * 客户端仍可能已经把 selectedSlot 改掉。因此每 tick 强制恢复到连环手枪槽，
     * 作为数字键、滚轮和其它客户端输入路径之外的最终兜底。
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void sparkstrength$restoreSerialKillerSlot(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        PlayerEntity player = client.player;
        if (player == null || PlayerPsychoComponent.KEY.get(player).getPsychoTicks() <= 0) return;
        PlayerInventory inventory = player.getInventory();
        int pistolSlot = findSerialPistolSlot(inventory);
        if (pistolSlot >= 0 && inventory.selectedSlot != pistolSlot) {
            inventory.selectedSlot = pistolSlot;
        }
    }

    private static int findSerialPistolSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(SparkStrengthItems.serialPistol())) return slot;
        }
        return -1;
    }
}
