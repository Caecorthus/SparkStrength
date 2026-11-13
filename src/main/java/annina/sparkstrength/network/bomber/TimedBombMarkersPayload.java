package annina.sparkstrength.network.bomber;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Private S2C update of the planted timed-bomb traps (bed and tray): the positions of the bed head or tray block
 * entities holding one. Sent only to players who may see the traps ({@code BomberTrapService#seesTimedBombTraps});
 * every other client receives no packet at all, so neither the positions nor the moment a trap is planted or sprung
 * reach it. The block entities' client NBT no longer carries the trap owner. {@code reset} empties the client store
 * before {@code removed} and {@code added} are applied, in that order.
 * 已埋设定时炸弹陷阱（床与托盘）的私有 S2C 更新：持有陷阱的床头或托盘方块实体的位置。只发送给可以看到陷阱的玩家
 * （BomberTrapService#seesTimedBombTraps）；其他客户端完全收不到任何数据包，因此既得不到位置，也得不到陷阱被埋设或触发的时机。
 * 方块实体发给客户端的 NBT 不再携带埋设者。reset 会先清空客户端存储，然后依次应用 removed 与 added。
 */
public record TimedBombMarkersPayload(boolean reset, List<BlockPos> added, List<BlockPos> removed) implements CustomPayload {
    /** Upper bound of each position list in one packet; larger updates are split by {@link #split}. / 单个数据包中每个位置列表的上限。 */
    public static final int MAX_POSITIONS = 1024;
    public static final Id<TimedBombMarkersPayload> ID = new Id<>(SparkStrength.id("timed_bomb_markers"));
    public static final PacketCodec<RegistryByteBuf, TimedBombMarkersPayload> CODEC =
            PacketCodec.of(TimedBombMarkersPayload::write, TimedBombMarkersPayload::read);

    public TimedBombMarkersPayload {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        if (added.size() > MAX_POSITIONS || removed.size() > MAX_POSITIONS) {
            throw new IllegalArgumentException("Timed bomb marker update exceeds " + MAX_POSITIONS + " positions");
        }
    }

    /**
     * Packets carrying one update without exceeding the per-packet bound. Only the first one resets, removals travel
     * before additions, and an update with nothing to say (no reset, no positions) yields no packet.
     * 在不超过单包上限的前提下承载一次更新的数据包列表。只有第一个包携带重置，移除先于新增；没有任何内容的更新不产生数据包。
     */
    public static List<TimedBombMarkersPayload> split(boolean reset, List<BlockPos> added, List<BlockPos> removed) {
        List<TimedBombMarkersPayload> packets = new ArrayList<>();
        boolean pendingReset = reset;
        for (int from = 0; from < removed.size(); from += MAX_POSITIONS) {
            packets.add(new TimedBombMarkersPayload(
                    pendingReset, List.of(), removed.subList(from, Math.min(removed.size(), from + MAX_POSITIONS))));
            pendingReset = false;
        }
        for (int from = 0; from < added.size(); from += MAX_POSITIONS) {
            packets.add(new TimedBombMarkersPayload(
                    pendingReset, added.subList(from, Math.min(added.size(), from + MAX_POSITIONS)), List.of()));
            pendingReset = false;
        }
        if (pendingReset) {
            packets.add(new TimedBombMarkersPayload(true, List.of(), List.of()));
        }
        return packets;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    void write(PacketByteBuf buf) {
        buf.writeBoolean(reset);
        writePositions(buf, added);
        writePositions(buf, removed);
    }

    static TimedBombMarkersPayload read(PacketByteBuf buf) {
        boolean reset = buf.readBoolean();
        List<BlockPos> added = readPositions(buf);
        List<BlockPos> removed = readPositions(buf);
        return new TimedBombMarkersPayload(reset, added, removed);
    }

    private static void writePositions(PacketByteBuf buf, List<BlockPos> positions) {
        buf.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
    }

    /** Bounded: a count outside 0..MAX_POSITIONS is refused before anything is allocated. / 有界：数量超出范围时在分配前拒绝。 */
    private static List<BlockPos> readPositions(PacketByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_POSITIONS) {
            throw new IllegalArgumentException("Invalid timed bomb marker count " + size);
        }
        List<BlockPos> positions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        return positions;
    }
}
