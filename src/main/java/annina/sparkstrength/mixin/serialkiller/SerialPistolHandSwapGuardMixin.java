package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.role.serialkiller.SerialPistolInventoryRules;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The F-key hand swap would trade the two pistols' hands, breaking the per-hand fire check and the hotbar lock; it is
 * refused while either hand holds one (Wathe only blocks it while the train HUD is on).
 * F 键换手会对调两把手枪的手位，破坏按手位的开火校验与快捷栏锁定；任一手持枪时拒绝（Wathe 只在列车 HUD 开启时拦截）。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class SerialPistolHandSwapGuardMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$keepSerialPistolHands(PlayerActionC2SPacket packet, CallbackInfo ci) {
        // HEAD also runs on the Netty thread before vanilla reschedules; decide only on the main-thread pass.
        // HEAD 也会在原版转交主线程前于网络线程执行；只在主线程那一次判定，网络线程取消会让包永远不再处理。
        if (!player.getServerWorld().getServer().isOnThread()) {
            return;
        }
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && SerialPistolInventoryRules.blocksHandSwap(player.getMainHandStack(), player.getOffHandStack())) {
            ci.cancel();
        }
    }
}
