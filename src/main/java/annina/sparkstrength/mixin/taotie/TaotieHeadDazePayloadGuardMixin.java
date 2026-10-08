package annina.sparkstrength.mixin.taotie;

import annina.sparkstrength.role.taotie.TaotieHeadDaze;
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
 * External seam (Fabric networking internals, the same seam as SparkWitch's Control Expert stun guard; both wrappers
 * chain): wraps the single main-thread hand-off in {@code receive} so a denied payload from a dazed sender is dropped on
 * the server thread, atomically with the handler it would have run. Only ids in
 * {@link TaotieHeadRules#DAZE_BLOCKED_PAYLOADS} are wrapped; every other payload is scheduled unchanged. Server
 * authority: a modified client cannot bypass the daze.
 * 外部接缝（Fabric 网络内部实现，与 SparkWitch 控场专家眩晕拦截相同的接缝；两个包装会串联）：包装 {@code receive} 中
 * 唯一一次移交主线程的调用，使被眩晕发送者的受限数据包在服务端主线程上、与其原本要执行的处理器原子地被丢弃。仅包装
 * {@link TaotieHeadRules#DAZE_BLOCKED_PAYLOADS} 中的 id；其他数据包原样调度。服务端权威：修改过的客户端无法绕过眩晕。
 */
@Mixin(value = ServerPlayNetworkAddon.class, remap = false)
public abstract class TaotieHeadDazePayloadGuardMixin {
    @Shadow
    @Final
    private ServerPlayNetworking.Context context;

    @WrapOperation(
            method = "receive(Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayPayloadHandler;"
                    + "Lnet/minecraft/network/packet/CustomPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V")
    )
    private void sparkstrength$dropDazedPayload(
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
            if (!TaotieHeadDaze.isDazed(context.player())) {
                handler.run();
            }
        });
    }
}
