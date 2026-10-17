package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Prying or hacking progress for the actor's HUD: a snapshot plus a per-tick rate the client extrapolates from.
 * 撬开或入侵的进度，供本人 HUD 显示：一份快照加上每 tick 速率，客户端据此外推。
 *
 * @param channelId the server's channel number, echoed by the cancel packet / 服务端的读条编号，取消包会原样带回
 * @param kind     0 = pry, 1 = hack / 0 撬开，1 入侵
 * @param state    0 = running, 1 = finished, 2 = stopped / 0 进行中，1 完成，2 中断
 * @param progress units done, out of {@code total} / 已完成量，满值为 total
 * @param rate     units per tick while running / 进行中每 tick 的增量
 */
public record ChannelProgressS2CPacket(int channelId, int kind, BlockPos pos, int state, int progress, int total,
                                       int rate)
        implements CustomPayload {
    public static final int KIND_PRY = 0;
    public static final int KIND_HACK = 1;
    public static final int STATE_RUNNING = 0;
    public static final int STATE_FINISHED = 1;
    public static final int STATE_STOPPED = 2;
    public static final Identifier PAYLOAD_ID = SparkStrength.id("sabotage_channel_progress");
    public static final Id<ChannelProgressS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, ChannelProgressS2CPacket> CODEC =
            PacketCodec.of(ChannelProgressS2CPacket::write, ChannelProgressS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(channelId);
        buf.writeVarInt(kind);
        buf.writeBlockPos(pos);
        buf.writeVarInt(state);
        buf.writeVarInt(progress);
        buf.writeVarInt(total);
        buf.writeVarInt(rate);
    }

    public static ChannelProgressS2CPacket read(PacketByteBuf buf) {
        return new ChannelProgressS2CPacket(buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
