package annina.sparkstrength.mixin.engineer;

import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
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
 * Security guard: a player held by an Engineer capture stun (or the Jester Moment freeze, which reuses it) can't fire,
 * stab or use skills. Its own client already reads every key as released for the whole stun; this drops the same action
 * payloads a Taotie-head daze drops ({@link TaotieHeadRules#DAZE_BLOCKED_PAYLOADS}) on the server thread while the
 * sender is stunned, so a modified client can't shoot out of the stun. Same seam as
 * {@code TaotieHeadDazePayloadGuardMixin}; the wrappers chain, and every other payload is scheduled unchanged.
 * 安全闸门：被工程师捕捉定身（或复用它的小丑时刻定身）的玩家不能开枪、捅刀或使用技能。其客户端在整个定身期间已把所有按键读作
 * 未按下；这里在发送者被定身期间于服务端主线程丢弃与饕餮头颅眩晕相同的动作数据包（DAZE_BLOCKED_PAYLOADS），使修改过的客户端
 * 无法在定身中开枪。与 TaotieHeadDazePayloadGuardMixin 使用同一接缝，各包装串联；其他数据包原样调度。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class EngineerStunPayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkstrength$dropStunnedPayload(
            MinecraftServer server,
            Runnable handler,
            Operation<Void> original,
            @Local(argsOnly = true) CustomPayload payload
    ) {
        Identifier payloadId = payload == null || payload.getId() == null ? null : payload.getId().id();
        if (!TaotieHeadRules.isDazeBlockedPayload(payloadId)) {
            original.call(server, handler);
            return;
        }
        original.call(server, (Runnable) () -> {
            if (!EngineerStunnedPlayerComponent.KEY.get(context.player()).isStunned()) {
                handler.run();
            }
        });
    }
}
