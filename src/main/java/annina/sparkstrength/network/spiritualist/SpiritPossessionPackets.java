package annina.sparkstrength.network.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Spiritualist possession packet registration. Every receiver runs on the server thread and re-validates the sender.
 * 灵界行者附身网络包注册。所有接收器都在服务端线程运行，并重新校验发送者。
 */
public final class SpiritPossessionPackets {
    private SpiritPossessionPackets() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(SpiritPossessC2SPacket.ID, SpiritPossessC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SpiritPossessExitC2SPacket.ID, SpiritPossessExitC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SpiritPossessionStateS2CPacket.ID, SpiritPossessionStateS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SpiritPossessC2SPacket.ID,
                (payload, context) -> SpiritPossessionService.start(context.player(), payload.targetEntityId()));
        ServerPlayNetworking.registerGlobalReceiver(SpiritPossessExitC2SPacket.ID,
                (payload, context) -> SpiritPossessionService.exit(context.player()));
    }
}
