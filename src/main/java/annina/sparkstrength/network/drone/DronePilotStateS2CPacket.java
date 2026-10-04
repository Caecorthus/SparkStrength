package annina.sparkstrength.network.drone;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Authoritative pilot session state, sent only to the pilot. {@code entityId < 0} ends the session; {@code reason} is a
 * {@code DronePilotEndReason} wire value (ignored on start).
 * 权威驾驶会话状态，只发给驾驶者。entityId < 0 表示结束会话；reason 为 DronePilotEndReason 的 wire 值（开始时忽略）。
 */
public record DronePilotStateS2CPacket(int entityId, byte kindWire, byte reason) implements CustomPayload {
    public static final Id<DronePilotStateS2CPacket> ID = new Id<>(SparkStrength.id("drone_pilot_state"));
    public static final PacketCodec<RegistryByteBuf, DronePilotStateS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DronePilotStateS2CPacket::entityId,
            PacketCodecs.BYTE, DronePilotStateS2CPacket::kindWire,
            PacketCodecs.BYTE, DronePilotStateS2CPacket::reason,
            DronePilotStateS2CPacket::new
    );

    public boolean active() {
        return entityId >= 0;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
