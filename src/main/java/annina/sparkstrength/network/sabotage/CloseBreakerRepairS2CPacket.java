package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Closes a repair panel whose session ended on the server (power already back, round over, moved away). Both the
 * circuit breaker and the engine unit panels use it; session ids never repeat between the two.
 * 关闭服务端已结束的维修会话对应的面板（已来电、对局结束、走远了）。电闸与动力装置面板都用它；两者的会话 id 不会重复。
 *
 * @param reason 0 = fixed by someone else, 1 = repaired by you, 2 = other, 3 = the engine was wrecked mid-repair /
 *               0 已被别人修好，1 由你修好，2 其他，3 维修途中动力已损毁
 */
public record CloseBreakerRepairS2CPacket(int sessionId, int reason) implements CustomPayload {
    public static final int REASON_POWER_BACK = 0;
    public static final int REASON_REPAIRED = 1;
    public static final int REASON_OTHER = 2;
    /** A quick engine repair outlived the deadline: the engine is wrecked and needs the overhaul. / 抢修超过了截止时间：动力已损毁，需要大修。 */
    public static final int REASON_PHASE_CHANGED = 3;
    public static final Identifier PAYLOAD_ID = SparkStrength.id("close_breaker_repair");
    public static final Id<CloseBreakerRepairS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, CloseBreakerRepairS2CPacket> CODEC =
            PacketCodec.of(CloseBreakerRepairS2CPacket::write, CloseBreakerRepairS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(reason);
    }

    public static CloseBreakerRepairS2CPacket read(PacketByteBuf buf) {
        return new CloseBreakerRepairS2CPacket(buf.readVarInt(), buf.readVarInt());
    }
}
