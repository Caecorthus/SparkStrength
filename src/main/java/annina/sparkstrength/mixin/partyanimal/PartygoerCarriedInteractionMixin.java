package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server side of the carry lock: a player held by the Partygoer can't attack or use anything, nor drop or swap items.
 * Checked on the server thread (right after the hand-off), so a modified client gains nothing.
 * 扛人限制的服务端部分：被派对客抓着的玩家不能攻击或使用任何东西，也不能丢弃或换手。在移交服务端主线程之后检查，修改过的客户端也无法绕过。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class PartygoerCarriedInteractionMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerInteractEntity", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER))
    private void sparkstrength$carriedNoEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        if (PartygoerCarry.isCarried(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractItem", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER))
    private void sparkstrength$carriedNoItem(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (PartygoerCarry.isCarried(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerInteractBlock", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER))
    private void sparkstrength$carriedNoBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (PartygoerCarry.isCarried(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER))
    private void sparkstrength$carriedNoDropOrSwap(PlayerActionC2SPacket packet, CallbackInfo ci) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        if (PartygoerCarry.isCarried(player) && (action == PlayerActionC2SPacket.Action.DROP_ITEM
                || action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS
                || action == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND)) {
            ci.cancel();
        }
    }
}
