package annina.sparkstrength.network.undercover;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Empty hand, Shift + right-click at nothing while wearing the CQCM mask: take it off. Vanilla sends nothing for an
 * empty-hand use, hence this packet; the server re-checks everything.
 * 戴着 CQCM 面具时空手 Shift + 右键空处：摘下面具。原版空手使用不发包，所以需要这个包；服务端会重新检查一切。
 */
public record UndercoverMaskOffC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("undercover_mask_off");
    public static final Id<UndercoverMaskOffC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, UndercoverMaskOffC2SPacket> CODEC =
            PacketCodec.of(UndercoverMaskOffC2SPacket::write, UndercoverMaskOffC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static UndercoverMaskOffC2SPacket read(PacketByteBuf buf) {
        return new UndercoverMaskOffC2SPacket();
    }
}
