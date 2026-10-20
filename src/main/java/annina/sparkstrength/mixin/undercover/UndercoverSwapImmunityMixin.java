package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.SwapperC2SPacket;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A stage-2 Undercover cannot be swapped (owner 2026-10-20): a swap with it on either side is turned away before
 * NoellesRoles teleports anyone, so the Swapper keeps its cooldown. Same seam as UndercoverAssassinImmunityMixin, and
 * build.gradle pins this selector against the libs jar the same way.
 * 二阶段卧底不能被交换（负责人 2026-10-20）：任意一方是它的交换都在 NoellesRoles 传送前被拦下，交换者不进冷却。
 * 注入点同 UndercoverAssassinImmunityMixin，build.gradle 也同样对照 libs jar 锁定这个选择器。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class UndercoverSwapImmunityMixin {
    @Inject(
            method = "lambda$registerPackets$4(Lorg/agmas/noellesroles/packet/SwapperC2SPacket;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void sparkstrength$stage2UndercoverCannotBeSwapped(
            SwapperC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        ServerPlayerEntity swapper = context.player();
        ServerWorld world = swapper.getServerWorld();
        if (!GameWorldComponent.KEY.get(world).isRole(swapper, Noellesroles.SWAPPER)
                || !GameFunctions.isPlayerPlayingAndAlive(swapper)
                || SwallowedPlayerComponent.isPlayerSwallowed(swapper)
                || payload.player() == null || payload.player2() == null) {
            return;
        }
        PlayerEntity first = world.getPlayerByUuid(payload.player());
        PlayerEntity second = world.getPlayerByUuid(payload.player2());
        if (UndercoverRoles.isLivingStage2(first) || UndercoverRoles.isLivingStage2(second)) {
            swapper.sendMessage(Text.translatable("message.sparkstrength.undercover.cannot_swap"), true);
            ci.cancel();
        }
    }
}
