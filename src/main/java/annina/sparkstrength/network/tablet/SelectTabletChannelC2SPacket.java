package annina.sparkstrength.network.tablet;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Requests switching the viewed tablet channel; the server validates membership, cooldown and meeting lock.
 * 请求切换查看的平板频道；服务端校验成员资格、冷却与会议锁定。
 */
public record SelectTabletChannelC2SPacket(int channelWire) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("select_tablet_channel");
    public static final Id<SelectTabletChannelC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SelectTabletChannelC2SPacket> CODEC =
            PacketCodec.of(SelectTabletChannelC2SPacket::write, SelectTabletChannelC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(channelWire);
    }

    public static SelectTabletChannelC2SPacket read(PacketByteBuf buf) {
        return new SelectTabletChannelC2SPacket(buf.readVarInt());
    }
}
