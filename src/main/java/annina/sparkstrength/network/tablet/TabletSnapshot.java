package annina.sparkstrength.network.tablet;

import annina.sparkstrength.role.attendant.DoorLogKind;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletFeature;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Per-viewer tablet state. The server redacts it to the viewer's selected channel before sending.
 * 按观看者生成的平板状态；服务端发送前已按其所选频道裁剪。
 *
 * <p>In anonymous channels the server also redacts by the viewer's links: an unlinked sender's {@link ChatRow} carries
 * no UUID or name, and unlinked members are left out of {@code connections}.
 * 在匿名频道中，服务端还会按观看者的互认状态裁剪：未互认发送者的聊天行不含 UUID 与名字，未互认成员不会出现在 connections 中。</p>
 *
 * <p>Channel fields follow {@code localHasTablet}; changing this codec requires a new sync payload id.
 * 频道字段紧跟 localHasTablet；修改此编解码必须更换同步包 id。</p>
 *
 * <p>{@code featureMask} ({@link TabletFeature}) is independent of the channel; {@code doorLog} is non-empty only for
 * viewers holding {@link TabletFeature#DOOR_LOG}, newest first.
 * featureMask 与频道无关；doorLog 只对拥有房门监控的观看者非空，最新在前。</p>
 */
public record TabletSnapshot(
        boolean localHasTablet,
        int channelWire,
        int allowedChannelMask,
        boolean canSend,
        int channelSwitchCooldownSeconds,
        boolean channelLocked,
        boolean localMeetingParticipant,
        int cooldownSeconds,
        int localMeetingCallsRemaining,
        List<PlayerRow> connections,
        List<ChatRow> chat,
        Meeting meeting,
        List<SuspectRow> suspects,
        int featureMask,
        List<DoorLogRow> doorLog
) {
    public static TabletSnapshot empty() {
        return new TabletSnapshot(
                false,
                TabletChannel.NO_CHANNEL_WIRE,
                0,
                false,
                0,
                false,
                false,
                0,
                0,
                List.of(),
                List.of(),
                Meeting.inactive(),
                List.of(),
                0,
                List.of()
        );
    }

    public @Nullable TabletChannel channel() {
        return TabletChannel.fromWire(channelWire);
    }

    public EnumSet<TabletChannel> allowedChannels() {
        return TabletChannel.fromMask(allowedChannelMask);
    }

    public EnumSet<TabletFeature> features() {
        return TabletFeature.fromMask(featureMask);
    }

    public boolean hasFeature(TabletFeature feature) {
        return (featureMask & (1 << feature.wire())) != 0;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(localHasTablet);
        buf.writeVarInt(channelWire);
        buf.writeVarInt(allowedChannelMask);
        buf.writeBoolean(canSend);
        buf.writeVarInt(channelSwitchCooldownSeconds);
        buf.writeBoolean(channelLocked);
        buf.writeBoolean(localMeetingParticipant);
        buf.writeVarInt(cooldownSeconds);
        buf.writeVarInt(localMeetingCallsRemaining);
        writeList(buf, connections, (targetBuf, row) -> row.write(targetBuf));
        writeList(buf, chat, (targetBuf, row) -> row.write(targetBuf));
        meeting.write(buf);
        writeList(buf, suspects, (targetBuf, row) -> row.write(targetBuf));
        buf.writeVarInt(featureMask);
        writeList(buf, doorLog, (targetBuf, row) -> row.write(targetBuf));
    }

    public static TabletSnapshot read(PacketByteBuf buf) {
        return new TabletSnapshot(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                readList(buf, PlayerRow::read),
                readList(buf, ChatRow::read),
                Meeting.read(buf),
                readList(buf, SuspectRow::read),
                buf.readVarInt(),
                readList(buf, DoorLogRow::read)
        );
    }

    private static <T> void writeList(PacketByteBuf buf, List<T> list, Writer<T> writer) {
        buf.writeVarInt(list.size());
        for (T value : list) {
            writer.write(buf, value);
        }
    }

    private static <T> List<T> readList(PacketByteBuf buf, Reader<T> reader) {
        int size = buf.readVarInt();
        ArrayList<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(reader.read(buf));
        }
        return List.copyOf(values);
    }

    private static void writeOptionalUuid(PacketByteBuf buf, @Nullable UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUuid(uuid);
        }
    }

    private static @Nullable UUID readOptionalUuid(PacketByteBuf buf) {
        return buf.readBoolean() ? buf.readUuid() : null;
    }

    private interface Writer<T> {
        void write(PacketByteBuf buf, T value);
    }

    private interface Reader<T> {
        T read(PacketByteBuf buf);
    }

    public record PlayerRow(UUID uuid, String name, boolean inGame) {
        private void write(PacketByteBuf buf) {
            buf.writeUuid(uuid);
            buf.writeString(name);
            buf.writeBoolean(inGame);
        }

        private static PlayerRow read(PacketByteBuf buf) {
            return new PlayerRow(buf.readUuid(), buf.readString(32767), buf.readBoolean());
        }
    }

    /**
     * A null {@code senderUuid} marks an anonymous row; it never carries a name, so the client shows "???".
     * senderUuid 为 null 表示匿名行；匿名行绝不携带名字，客户端显示“???”。
     */
    public record ChatRow(@Nullable UUID senderUuid, String senderName, String message) {
        public ChatRow {
            senderName = senderUuid == null || senderName == null ? "" : senderName;
        }

        public static ChatRow hidden(String message) {
            return new ChatRow(null, "", message);
        }

        public boolean isAnonymous() {
            return senderUuid == null;
        }

        private void write(PacketByteBuf buf) {
            writeOptionalUuid(buf, senderUuid);
            buf.writeString(senderName);
            buf.writeString(message);
        }

        private static ChatRow read(PacketByteBuf buf) {
            return new ChatRow(readOptionalUuid(buf), buf.readString(32767), buf.readString(32767));
        }
    }

    public record Meeting(
            boolean active,
            int remainingSeconds,
            boolean localConfirmed,
            boolean localAbstained,
            @Nullable UUID localVoteTarget,
            List<VoteTarget> targets
    ) {
        public static Meeting inactive() {
            return new Meeting(false, 0, false, false, null, List.of());
        }

        private void write(PacketByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarInt(remainingSeconds);
            buf.writeBoolean(localConfirmed);
            buf.writeBoolean(localAbstained);
            writeOptionalUuid(buf, localVoteTarget);
            writeList(buf, targets, (targetBuf, row) -> row.write(targetBuf));
        }

        private static Meeting read(PacketByteBuf buf) {
            return new Meeting(
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    readOptionalUuid(buf),
                    readList(buf, VoteTarget::read)
            );
        }
    }

    public record VoteTarget(UUID uuid, String name, boolean selectable, int votes) {
        private void write(PacketByteBuf buf) {
            buf.writeUuid(uuid);
            buf.writeString(name);
            buf.writeBoolean(selectable);
            buf.writeVarInt(votes);
        }

        private static VoteTarget read(PacketByteBuf buf) {
            return new VoteTarget(buf.readUuid(), buf.readString(32767), buf.readBoolean(), buf.readVarInt());
        }
    }

    public record SuspectRow(UUID uuid, String name, int approvals, int requiredApprovals, boolean localApproved) {
        private void write(PacketByteBuf buf) {
            buf.writeUuid(uuid);
            buf.writeString(name);
            buf.writeVarInt(approvals);
            buf.writeVarInt(requiredApprovals);
            buf.writeBoolean(localApproved);
        }

        private static SuspectRow read(PacketByteBuf buf) {
            return new SuspectRow(
                    buf.readUuid(),
                    buf.readString(32767),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean()
            );
        }
    }

    /**
     * One door-log line. {@code id} is the server's monotonic entry id (unread tracking); {@code ageSeconds} is
     * computed at snapshot time. Only {@link DoorLogKind#KEY_OPENED} rows carry an actor; an anonymous actor has no
     * uuid and no name. {@code actorUuid} is the apparent (disguise-aware) identity, never a disguised player's real uuid.
     * 一行房门记录。id 为服务端单调递增的条目 id（用于未读）；ageSeconds 在生成快照时计算。只有 KEY_OPENED 行带操作者；
     * 匿名操作者不带 uuid 与名字。actorUuid 是表面身份（考虑伪装），绝不是伪装者的真实 uuid。
     */
    public record DoorLogRow(
            int id,
            int kindWire,
            String doorName,
            boolean hasActor,
            @Nullable UUID actorUuid,
            String actorName,
            int ageSeconds,
            int count
    ) {
        public DoorLogRow {
            doorName = doorName == null ? "" : doorName;
            if (!hasActor || actorUuid == null) {
                actorUuid = null;
                actorName = "";
            } else if (actorName == null) {
                actorName = "";
            }
            ageSeconds = Math.max(0, ageSeconds);
            count = Math.max(1, count);
        }

        public @Nullable DoorLogKind kind() {
            return DoorLogKind.fromWire(kindWire);
        }

        /** An actor is present but hidden (psycho / Jester moment). / 有操作者但被隐藏（疯魔 / 小丑时刻）。 */
        public boolean isAnonymousActor() {
            return hasActor && actorUuid == null;
        }

        private void write(PacketByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(kindWire);
            buf.writeString(doorName);
            buf.writeBoolean(hasActor);
            if (hasActor) {
                writeOptionalUuid(buf, actorUuid);
                buf.writeString(actorName);
            }
            buf.writeVarInt(ageSeconds);
            buf.writeVarInt(count);
        }

        private static DoorLogRow read(PacketByteBuf buf) {
            int id = buf.readVarInt();
            int kindWire = buf.readVarInt();
            String doorName = buf.readString(32767);
            boolean hasActor = buf.readBoolean();
            UUID actorUuid = null;
            String actorName = "";
            if (hasActor) {
                actorUuid = readOptionalUuid(buf);
                actorName = buf.readString(32767);
            }
            return new DoorLogRow(id, kindWire, doorName, hasActor, actorUuid, actorName, buf.readVarInt(), buf.readVarInt());
        }
    }
}
