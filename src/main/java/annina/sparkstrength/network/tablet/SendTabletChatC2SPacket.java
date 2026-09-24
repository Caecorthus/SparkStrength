package annina.sparkstrength.network.tablet;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Chat send request. {@code expectedChannelWire} is the channel the client was viewing; the server rejects the
 * message if its authoritative selection differs, so a stale screen never posts into the wrong network.
 * 聊天发送请求。expectedChannelWire 为客户端当时查看的频道；若与服务端权威选择不一致则拒绝，避免过期界面发错网络。
 */
public record SendTabletChatC2SPacket(int expectedChannelWire, String message) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("send_tablet_chat_v2");
    public static final Id<SendTabletChatC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SendTabletChatC2SPacket> CODEC =
            PacketCodec.of(SendTabletChatC2SPacket::write, SendTabletChatC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(expectedChannelWire);
        buf.writeString(message);
    }

    public static SendTabletChatC2SPacket read(PacketByteBuf buf) {
        return new SendTabletChatC2SPacket(buf.readVarInt(), buf.readString(32767));
    }
}
