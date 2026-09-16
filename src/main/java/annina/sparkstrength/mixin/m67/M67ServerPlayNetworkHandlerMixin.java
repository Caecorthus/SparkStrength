package annina.sparkstrength.mixin.m67;

import annina.sparkstrength.item.m67.M67UseService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class M67ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    // Only the vanilla release branch authorizes a throw; other stopUsingItem calls cancel.
    // 仅原版松开分支授权投掷；其他 stopUsingItem 调用均按取消处理。
    @WrapOperation(method = "onPlayerAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;stopUsingItem()V"))
    private void sparkstrength$authorizeM67Release(ServerPlayerEntity player, Operation<Void> original) {
        M67UseService.withReleaseAuthorization(player, () -> original.call(player));
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"))
    private void sparkstrength$refreshM67BeforeInteract(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        // Detect first equip even when vanilla's cooldown gate skips Item.use. / 即使原版冷却跳过 Item.use 也检测首次装备。
        if (player.getServerWorld().getServer().isOnThread()) {
            M67UseService.refreshEquipment(player);
        }
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"))
    private void sparkstrength$cancelM67InventoryAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        // HEAD also runs before vanilla reschedules Netty packets; never touch state off-thread.
        // HEAD 也会在原版调度前执行，网络线程不得修改会话。
        if (!player.getServerWorld().getServer().isOnThread()) {
            return;
        }
        switch (packet.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> M67UseService.cancel(player);
            default -> { }
        }
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"))
    private void sparkstrength$cancelM67HotbarChange(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (player.getServerWorld().getServer().isOnThread() && packet.getSelectedSlot() >= 0
                && packet.getSelectedSlot() < 9 && packet.getSelectedSlot() != player.getInventory().selectedSlot) {
            M67UseService.cancel(player);
        }
    }

    @Inject(method = "onClickSlot", at = @At("RETURN"))
    private void sparkstrength$validateM67InventoryChange(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (player.getServerWorld().getServer().isOnThread()) {
            M67UseService.tick(player);
        }
    }
    // Observe actual mutations, including away-and-back packets within one tick. / 观察实际变更，包括同一 tick 切走再切回。
    @Inject(method = "onUpdateSelectedSlot", at = @At("RETURN"))
    private void sparkstrength$refreshM67Hotbar(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (player.getServerWorld().getServer().isOnThread()) {
            M67UseService.refreshEquipment(player);
        }
    }

    @Inject(method = "onPlayerAction", at = @At("RETURN"))
    private void sparkstrength$refreshM67Action(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (player.getServerWorld().getServer().isOnThread()) {
            M67UseService.tick(player);
        }
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("RETURN"))
    private void sparkstrength$refreshM67CreativeInventory(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        if (player.getServerWorld().getServer().isOnThread()) {
            M67UseService.tick(player);
        }
    }
}
