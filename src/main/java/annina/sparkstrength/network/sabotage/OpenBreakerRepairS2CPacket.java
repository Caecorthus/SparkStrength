package annina.sparkstrength.network.sabotage;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Opens the circuit breaker repair panel. {@code seed} lays out which switches tripped.
 * 打开电闸维修面板。seed 决定哪些开关跳闸。
 */
public record OpenBreakerRepairS2CPacket(int sessionId, BlockPos pos, long seed, int switches, int holdTicks)
        implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("open_breaker_repair");
    public static final Id<OpenBreakerRepairS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, OpenBreakerRepairS2CPacket> CODEC =
            PacketCodec.of(OpenBreakerRepairS2CPacket::write, OpenBreakerRepairS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        buf.writeLong(seed);
        buf.writeVarInt(switches);
        buf.writeVarInt(holdTicks);
    }

    public static OpenBreakerRepairS2CPacket read(PacketByteBuf buf) {
        return new OpenBreakerRepairS2CPacket(buf.readVarInt(), buf.readBlockPos(), buf.readLong(), buf.readVarInt(),
                buf.readVarInt());
    }
}
