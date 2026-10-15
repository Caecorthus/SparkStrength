package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.record.BlackoutRecorder;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Achievement record (S8): the blackout start with its buyer, right where Wathe's blackout shop entry
 * ({@code useBlackout}, the only caller of {@code triggerBlackout}) returns. Observes only; the result is unchanged.
 * 成就记录（S8）：在 Wathe 商店停电（useBlackout，triggerBlackout 的唯一调用方）返回处记录停电开始及其购买者。只观察，不改返回值。
 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class BlackoutPurchaseRecordMixin {
    @Inject(method = "useBlackout", at = @At("RETURN"), require = 1, allow = 1)
    private static void sparkstrength$recordBlackoutStart(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && player instanceof ServerPlayerEntity buyer) {
            BlackoutRecorder.purchased(buyer);
        }
    }
}
