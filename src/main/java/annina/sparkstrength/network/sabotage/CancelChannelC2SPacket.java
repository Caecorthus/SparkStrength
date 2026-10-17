package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The client stopped prying or hacking (button released, aim lost, a screen opened). Carries the channel number, so a
 * late cancel for box A never stops a channel that already moved on to box B. Always allowed, even while dazed.
 * 客户端停止了撬开或入侵（松开按键、准星离开、打开了界面）。带上读条编号，迟到的 A 箱取消不会停掉已经换到 B 箱的读条。
 * 任何时候都放行，眩晕时也一样。
 */
public record CancelChannelC2SPacket(int channelId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("cancel_sabotage_channel");
    public static final Id<CancelChannelC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, CancelChannelC2SPacket> CODEC =
            PacketCodec.of(CancelChannelC2SPacket::write, CancelChannelC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(channelId);
    }

    public static CancelChannelC2SPacket read(PacketByteBuf buf) {
        return new CancelChannelC2SPacket(buf.readVarInt());
    }
}
