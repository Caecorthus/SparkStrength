package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The repair panel was closed unfinished. Always allowed; a lost packet only leaves a session that times out.
 * 维修面板未完成就关闭了。任何时候都放行；包丢了也只是留下一个会超时的会话。
 */
public record CancelBreakerRepairC2SPacket(int sessionId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("cancel_breaker_repair");
    public static final Id<CancelBreakerRepairC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, CancelBreakerRepairC2SPacket> CODEC =
            PacketCodec.of(CancelBreakerRepairC2SPacket::write, CancelBreakerRepairC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static CancelBreakerRepairC2SPacket read(PacketByteBuf buf) {
        return new CancelBreakerRepairC2SPacket(buf.readVarInt());
    }
}
