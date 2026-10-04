package annina.sparkstrength.network.drone;

import annina.sparkstrength.role.bomber.drone.DronePilotService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Drone pilot packet registration. Every receiver runs on the server thread and re-validates the sender.
 * 无人机驾驶网络包注册。所有接收器都在服务端线程运行，并重新校验发送者。
 */
public final class DronePackets {
    private DronePackets() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(DronePilotStartC2SPacket.ID, DronePilotStartC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DronePilotMoveC2SPacket.ID, DronePilotMoveC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DronePilotActionC2SPacket.ID, DronePilotActionC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(DronePilotExitC2SPacket.ID, DronePilotExitC2SPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(DronePilotStateS2CPacket.ID, DronePilotStateS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(DronePilotCorrectionS2CPacket.ID, DronePilotCorrectionS2CPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DronePilotStartC2SPacket.ID,
                (payload, context) -> DronePilotService.start(context.player(), payload.entityId()));
        ServerPlayNetworking.registerGlobalReceiver(DronePilotMoveC2SPacket.ID,
                (payload, context) -> DronePilotService.move(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(DronePilotActionC2SPacket.ID,
                (payload, context) -> DronePilotService.action(context.player(), payload.entityId(), payload.action()));
        ServerPlayNetworking.registerGlobalReceiver(DronePilotExitC2SPacket.ID,
                (payload, context) -> DronePilotService.exit(context.player(), payload.entityId()));
    }
}
