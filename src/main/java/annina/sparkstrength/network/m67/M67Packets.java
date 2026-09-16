package annina.sparkstrength.network.m67;

import annina.sparkstrength.item.m67.M67UseService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class M67Packets {
    private M67Packets() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(M67SoundPayload.ID, M67SoundPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(M67CancelPayload.ID, M67CancelPayload.CODEC);
        // Fabric dispatches on the server thread; only the authenticated sender can cancel.
        // Fabric 在服务端线程分发；只能取消已认证发送者自己的使用。
        ServerPlayNetworking.registerGlobalReceiver(M67CancelPayload.ID,
                (payload, context) -> M67UseService.cancel(context.player()));
    }
}
