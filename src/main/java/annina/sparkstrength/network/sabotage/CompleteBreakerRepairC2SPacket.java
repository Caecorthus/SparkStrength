package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The repair panel was finished. The server checks the session, the outage it belongs to, timing and distance.
 * 维修面板已完成。服务端会校验会话、所属的熄灯、耗时与距离。
 */
public record CompleteBreakerRepairC2SPacket(int sessionId) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("complete_breaker_repair");
    public static final Id<CompleteBreakerRepairC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, CompleteBreakerRepairC2SPacket> CODEC =
            PacketCodec.of(CompleteBreakerRepairC2SPacket::write, CompleteBreakerRepairC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
    }

    public static CompleteBreakerRepairC2SPacket read(PacketByteBuf buf) {
        return new CompleteBreakerRepairC2SPacket(buf.readVarInt());
    }
}
