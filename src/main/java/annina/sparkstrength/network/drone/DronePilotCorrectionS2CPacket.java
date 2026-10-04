package annina.sparkstrength.network.drone;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server position correction for the drone the receiver pilots (its client ignores ordinary tracker moves).
 * 服务器对接收者所驾驶无人机的位置纠正（其客户端忽略普通追踪位移）。
 */
public record DronePilotCorrectionS2CPacket(int entityId, double x, double y, double z) implements CustomPayload {
    public static final Id<DronePilotCorrectionS2CPacket> ID = new Id<>(SparkStrength.id("drone_pilot_correction"));
    public static final PacketCodec<RegistryByteBuf, DronePilotCorrectionS2CPacket> CODEC =
            PacketCodec.of(DronePilotCorrectionS2CPacket::write, DronePilotCorrectionS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    private static DronePilotCorrectionS2CPacket read(RegistryByteBuf buf) {
        return new DronePilotCorrectionS2CPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
