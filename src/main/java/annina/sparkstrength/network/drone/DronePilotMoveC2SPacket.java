package annina.sparkstrength.network.drone;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Per-tick pilot movement: the client-predicted drone pose. The server caps the step, re-runs block collisions and
 * answers with a correction when its result differs.
 * 每刻驾驶移动：客户端预测的无人机位姿。服务器限制单步距离、重新计算方块碰撞，结果不一致时回发纠正。
 *
 * @param moving the pilot is giving movement input this tick (flight drain vs hover drain) / 本刻驾驶者有移动输入（飞行耗电或悬停耗电）
 */
public record DronePilotMoveC2SPacket(int entityId, double x, double y, double z, float yaw, float pitch, boolean moving)
        implements CustomPayload {
    public static final Id<DronePilotMoveC2SPacket> ID = new Id<>(SparkStrength.id("drone_pilot_move"));
    public static final PacketCodec<RegistryByteBuf, DronePilotMoveC2SPacket> CODEC =
            PacketCodec.of(DronePilotMoveC2SPacket::write, DronePilotMoveC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeBoolean(moving);
    }

    private static DronePilotMoveC2SPacket read(RegistryByteBuf buf) {
        return new DronePilotMoveC2SPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }
}
