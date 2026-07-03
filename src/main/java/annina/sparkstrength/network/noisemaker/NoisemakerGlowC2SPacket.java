package annina.sparkstrength.network.noisemaker;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * 客户端点击大嗓门背包头像后发给服务器的请求。
 *
 * <p>客户端只提交 UUID，所有职业、冷却、目标是否存活等判定都必须由服务端完成，
 * 防止客户端通过改包绕过冷却或点亮非允许目标。</p>
 */
public record NoisemakerGlowC2SPacket(UUID targetPlayer) implements CustomPayload {
    public static final CustomPayload.Id<NoisemakerGlowC2SPacket> ID =
            new CustomPayload.Id<>(SparkStrength.id("noisemaker_glow"));
    public static final PacketCodec<RegistryByteBuf, NoisemakerGlowC2SPacket> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, NoisemakerGlowC2SPacket::targetPlayer,
            NoisemakerGlowC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
