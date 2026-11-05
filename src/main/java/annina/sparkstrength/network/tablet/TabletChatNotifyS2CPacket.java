package annina.sparkstrength.network.tablet;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Tells one receiver that a message from someone else just arrived on {@code channelWire} (any of its networks, not
 * only the viewed one), so its tablet buzzes and shows the unread screen. Sent only to that receiver: the buzz and the
 * unread texture are local, nobody nearby learns that this player got a message.
 * 告诉某名接收者：别人刚在 channelWire 频道（其所属任一网络，不限当前查看的频道）发来消息，使其平板振动并显示未读屏幕。
 * 只发给该接收者：振动与未读材质都只在本地，附近的人不会知道这名玩家收到了消息。
 */
public record TabletChatNotifyS2CPacket(int channelWire) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("tablet_chat_notify");
    public static final Id<TabletChatNotifyS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, TabletChatNotifyS2CPacket> CODEC =
            PacketCodec.of(TabletChatNotifyS2CPacket::write, TabletChatNotifyS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(channelWire);
    }

    public static TabletChatNotifyS2CPacket read(PacketByteBuf buf) {
        return new TabletChatNotifyS2CPacket(buf.readVarInt());
    }
}
