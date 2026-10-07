package annina.sparkstrength.network.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionRules;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Authoritative possession state, sent only to the Spiritualist. {@code targetEntityId < 0} ends the possession and
 * {@code reason} is then a {@code SpiritPossessionEndReason} wire value; on start {@code durationTicks} is the
 * possession's full length.
 * 权威附身状态，只发给灵界行者。targetEntityId < 0 表示附身结束，此时 reason 为 SpiritPossessionEndReason 的 wire 值；
 * 开始时 durationTicks 为本次附身的完整时长。
 */
public record SpiritPossessionStateS2CPacket(int targetEntityId, int durationTicks, byte reason)
        implements CustomPayload {
    public static final Id<SpiritPossessionStateS2CPacket> ID = new Id<>(SpiritPossessionRules.STATE_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SpiritPossessionStateS2CPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SpiritPossessionStateS2CPacket::targetEntityId,
            PacketCodecs.VAR_INT, SpiritPossessionStateS2CPacket::durationTicks,
            PacketCodecs.BYTE, SpiritPossessionStateS2CPacket::reason,
            SpiritPossessionStateS2CPacket::new
    );

    public boolean active() {
        return targetEntityId >= 0;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
