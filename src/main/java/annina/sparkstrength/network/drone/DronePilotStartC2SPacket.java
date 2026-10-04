package annina.sparkstrength.network.drone;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Tablet "connect" button: ask to pilot one of the sender's drones. The server re-validates everything.
 * 平板“连接”按钮：请求驾驶发送者自己的某架无人机。服务器重新校验全部条件。
 */
public record DronePilotStartC2SPacket(int entityId) implements CustomPayload {
    public static final Id<DronePilotStartC2SPacket> ID = new Id<>(SparkStrength.id("drone_pilot_start"));
    public static final PacketCodec<RegistryByteBuf, DronePilotStartC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DronePilotStartC2SPacket::entityId,
            DronePilotStartC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
