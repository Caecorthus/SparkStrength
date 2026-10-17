package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Opens an engine unit panel. A status panel ({@code modeWire} 0) has session 0 and can submit nothing; a repair panel
 * finishes with {@link CompleteBreakerRepairC2SPacket} and is dropped with {@link CancelBreakerRepairC2SPacket} (the
 * wire ids are shared with the breaker panel so every mod's action restriction list keeps covering them), and the
 * server closes it with {@link CloseBreakerRepairS2CPacket}. {@code seed} lays out the needles and the burnt fuses.
 * 打开动力装置面板。状态面板（modeWire 为 0）的会话为 0，不能提交；维修面板用 CompleteBreakerRepairC2SPacket 完成、用
 * CancelBreakerRepairC2SPacket 放弃（wire id 与电闸面板共用，各模组的动作限制名单因此继续覆盖它们），服务端用
 * CloseBreakerRepairS2CPacket 关闭。seed 决定指针位置与哪些保险丝烧坏。
 *
 * @param modeWire  {@code EngineRepairMode} wire / 面板模式
 * @param minTicks  the server's minimum time; the panel never submits earlier / 服务端最短用时，面板不会更早提交
 */
public record OpenEngineRepairS2CPacket(int sessionId, BlockPos pos, int modeWire, long seed, int gauges, int fuses,
                                        int holdTicks, int minTicks) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("open_engine_repair");
    public static final Id<OpenEngineRepairS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenEngineRepairS2CPacket> CODEC =
            PacketCodec.of(OpenEngineRepairS2CPacket::write, OpenEngineRepairS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        buf.writeVarInt(modeWire);
        buf.writeLong(seed);
        buf.writeVarInt(gauges);
        buf.writeVarInt(fuses);
        buf.writeVarInt(holdTicks);
        buf.writeVarInt(minTicks);
    }

    public static OpenEngineRepairS2CPacket read(PacketByteBuf buf) {
        return new OpenEngineRepairS2CPacket(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readLong(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
