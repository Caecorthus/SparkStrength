package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import annina.sparkstrength.role.taotie.TaotieHeadRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player carried by the Partygoer can't fire, stab or use skills: the same action payloads a Taotie-head daze drops
 * ({@link TaotieHeadRules#DAZE_BLOCKED_PAYLOADS}) are dropped on the server thread while the sender is carried. Same
 * seam as {@code TaotieHeadDazePayloadGuardMixin}; the wrappers chain.
 * 被派对客扛着的玩家不能开枪、捅刀或使用技能：与饕餮头颅眩晕相同的动作数据包（DAZE_BLOCKED_PAYLOADS）在发送者被扛着期间于服务端主线程丢弃。
 * 与 TaotieHeadDazePayloadGuardMixin 使用同一接缝，两个包装串联。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class PartygoerCarriedPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkstrength$dropCarriedPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!TaotieHeadRules.isDazeBlockedPayload(payloadId)
                && (payloadId == null || !PartygoerCarry.EXTRA_BLOCKED_PAYLOADS.contains(payloadId))) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!PartygoerCarry.isCarried(context.player())) {
                handler.run();
            }
        });
    }
}
