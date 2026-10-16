package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The client stopped prying or hacking (button released, aim lost, a screen opened). Always allowed, even while dazed.
 * 客户端停止了撬开或入侵（松开按键、准星离开、打开了界面）。任何时候都放行，眩晕时也一样。
 */
public record CancelChannelC2SPacket() implements CustomPayload {
    public static final CancelChannelC2SPacket INSTANCE = new CancelChannelC2SPacket();
    public static final Identifier PAYLOAD_ID = SparkStrength.id("cancel_sabotage_channel");
    public static final Id<CancelChannelC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, CancelChannelC2SPacket> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
