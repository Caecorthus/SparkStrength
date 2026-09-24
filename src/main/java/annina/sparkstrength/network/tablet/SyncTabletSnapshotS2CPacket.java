package annina.sparkstrength.network.tablet;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Full per-viewer tablet state. v2 added channel fields; v3 made the chat sender optional (anonymous rows) and dropped
 * the chat timestamp. Old clients never register this id, so the server checks {@code ServerPlayNetworking.canSend}
 * instead of disconnecting them with unknown bytes.
 * 完整的按观看者平板状态。v2 增加了频道字段；v3 将聊天发送者改为可选（匿名行）并移除聊天时间戳。旧客户端不会注册此 id，
 * 服务端先检查 canSend，避免未知字节导致断线。
 */
public record SyncTabletSnapshotS2CPacket(TabletSnapshot snapshot) implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("sync_tablet_snapshot_v3");
    public static final Id<SyncTabletSnapshotS2CPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SyncTabletSnapshotS2CPacket> CODEC =
            PacketCodec.of(SyncTabletSnapshotS2CPacket::write, SyncTabletSnapshotS2CPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
        snapshot.write(buf);
    }

    public static SyncTabletSnapshotS2CPacket read(PacketByteBuf buf) {
        return new SyncTabletSnapshotS2CPacket(TabletSnapshot.read(buf));
    }
}
