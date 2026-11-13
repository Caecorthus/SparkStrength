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
 * <p>The server also redacts identities per viewer ({@code TabletIdentityRules}): in the anonymous killer channel by
 * the viewer's links, in the police channel by the viewer's completed tasks this round. A hidden sender's
 * {@link ChatRow} carries no UUID or name, and hidden members are left out of {@code connections}.
 * 服务端还会按查看者裁剪身份：杀手匿名频道按互认状态，义警频道按查看者本局已完成的任务数。被隐藏发送者的聊天行不含 UUID
 * 与名字，被隐藏成员不会出现在 connections 中。</p>
 *
 * <p>Wire layout (v10, in order; changing it requires a new sync payload id):
 * 线上布局（v10，按顺序；修改它必须更换同步包 id）：
 * <ol>
 *   <li>bool localHasTablet, varint channelWire, varint allowedChannelMask, bool canSend,
 *   varlong channelSwitchCooldownEndTick (varint seconds before v10), bool channelLocked</li>
 *   <li>bool localMeetingParticipant, varlong meetingCooldownEndTick (varint seconds before v10),
 *   varint localMeetingCallsRemaining</li>
 *   <li>list connections ({@link PlayerRow}), list chat ({@link ChatRow}), {@link Meeting},
 *   list suspects ({@link SuspectRow})</li>
 *   <li>v4: varint featureMask, list doorLog ({@link DoorLogRow})</li>
 *   <li>v5: varint identityTasksRemaining</li>
 *   <li>v6: list drones ({@link DroneRow})</li>
 *   <li>v7: list sabotage ({@link SabotageRow}), list sabotageLog ({@link SabotageLogRow})</li>
 *   <li>v8: a door-log row carries its absolute world tick (varlong) instead of an age</li>
 *   <li>v9: a chat row carries its public-channel signal (varint, 0 outside the public channel); varint
 *   cooldownChannelMask appended</li>
 *   <li>v10: the three countdowns become absolute world ticks (varlong, 0 = none) in the same places: the switch
 *   cooldown and the meeting cooldown in item 1 and 2, the meeting's end in {@link Meeting}. The client derives the
 *   seconds from its own world time, so a running countdown no longer changes the snapshot every second.
 *   v10：三个倒计时在原位置改为绝对世界 tick（varlong，0 表示无）：第 1、2 项中的切换冷却与会议冷却，以及 Meeting 中的会议结束
 *   时间。客户端按自己的世界时间计算秒数，因此进行中的倒计时不再让快照每秒变化。</li>
 * </ol>
 * Lists are a varint count followed by the rows.
 * 列表为 varint 数量后接各行。</p>
 *
 * <p>{@code featureMask} ({@link TabletFeature}) is independent of the channel; {@code doorLog} is non-empty only for
 * viewers holding {@link TabletFeature#DOOR_LOG}, newest first.
 * featureMask 与频道无关；doorLog 只对拥有房门监控的观看者非空，最新在前。</p>
 *
 * <p>{@code identityTasksRemaining} is how many more tasks the viewer must complete this round before the viewed
 * channel reveals other members (police only); 0 when already unlocked or when the viewed channel is not task-gated.
 * It only describes the viewer's own progress and never anyone else's membership.
 * identityTasksRemaining 表示查看者本局还需完成多少任务，当前查看的频道才会显示其他成员（仅义警）；已解锁或该频道不按任务
 * 解锁时为 0。它只描述查看者自己的进度，从不涉及他人的成员身份。</p>
 */
public record TabletSnapshot(
        boolean localHasTablet,
        int channelWire,
        int allowedChannelMask,
        boolean canSend,
        long channelSwitchCooldownEndTick,
        boolean channelLocked,
        boolean localMeetingParticipant,
        long meetingCooldownEndTick,
        int localMeetingCallsRemaining,
        List<PlayerRow> connections,
        List<ChatRow> chat,
        Meeting meeting,
        List<SuspectRow> suspects,
        int featureMask,
        List<DoorLogRow> doorLog,
        int identityTasksRemaining,
        List<DroneRow> drones,
        List<SabotageRow> sabotage,
        List<SabotageLogRow> sabotageLog,
        int cooldownChannelMask
) {
    public TabletSnapshot {
        identityTasksRemaining = Math.max(0, identityTasksRemaining);
        drones = drones == null ? List.of() : List.copyOf(drones);
        sabotage = sabotage == null ? List.of() : List.copyOf(sabotage);
        sabotageLog = sabotageLog == null ? List.of() : List.copyOf(sabotageLog);
    }

    /** Pre-v9 shape: no channel is blocked by the switch cooldown. / v9 之前的形状：没有频道被切换冷却阻止。 */
    public TabletSnapshot(
            boolean localHasTablet,
            int channelWire,
            int allowedChannelMask,
            boolean canSend,
            long channelSwitchCooldownEndTick,
            boolean channelLocked,
            boolean localMeetingParticipant,
            long meetingCooldownEndTick,
            int localMeetingCallsRemaining,
            List<PlayerRow> connections,
            List<ChatRow> chat,
            Meeting meeting,
            List<SuspectRow> suspects,
            int featureMask,
            List<DoorLogRow> doorLog,
            int identityTasksRemaining,
            List<DroneRow> drones,
            List<SabotageRow> sabotage,
            List<SabotageLogRow> sabotageLog
    ) {
        this(localHasTablet, channelWire, allowedChannelMask, canSend, channelSwitchCooldownEndTick, channelLocked,
                localMeetingParticipant, meetingCooldownEndTick, localMeetingCallsRemaining, connections, chat, meeting, suspects,
                featureMask, doorLog, identityTasksRemaining, drones, sabotage, sabotageLog, 0);
    }

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
                List.of(),
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public @Nullable TabletChannel channel() {
        return TabletChannel.fromWire(channelWire);
    }

    public EnumSet<TabletChannel> allowedChannels() {
        return TabletChannel.fromMask(allowedChannelMask);
    }

    /** Channels the faction switch cooldown currently blocks. / 阵营切换冷却当前阻止的频道。 */
    public EnumSet<TabletChannel> cooldownBlockedChannels() {
        return TabletChannel.fromMask(cooldownChannelMask);
    }

    public EnumSet<TabletFeature> features() {
        return TabletFeature.fromMask(featureMask);
    }

    /** Whole seconds of the faction switch cooldown left at {@code worldTime}. / worldTime 时阵营切换冷却剩余的整秒数。 */
    public int channelSwitchCooldownSeconds(long worldTime) {
        return secondsUntil(channelSwitchCooldownEndTick, worldTime);
    }

    /** Whole seconds of the meeting cooldown left at {@code worldTime}. / worldTime 时会议冷却剩余的整秒数。 */
    public int meetingCooldownSeconds(long worldTime) {
        return secondsUntil(meetingCooldownEndTick, worldTime);
    }

    /**
     * Whole seconds from {@code worldTime} until {@code endTick}, rounded up like {@code TabletRules#secondsCeil}; 0 once
     * reached and for "none" (0). The client passes its own world time, synced by vanilla every second.
     * 从 worldTime 到 endTick 的整秒数，与 TabletRules#secondsCeil 一样向上取整；到达后以及“无”（0）时为 0。客户端传入自己的
     * 世界时间（原版每秒同步一次）。
     */
    public static int secondsUntil(long endTick, long worldTime) {
        if (endTick <= worldTime) {
            return 0;
        }
        long ticks = endTick - worldTime;
        long seconds = ticks / 20 + (ticks % 20 == 0 ? 0 : 1);
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    public boolean hasFeature(TabletFeature feature) {
        return (featureMask & (1 << feature.wire())) != 0;
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(localHasTablet);
        buf.writeVarInt(channelWire);
        buf.writeVarInt(allowedChannelMask);
        buf.writeBoolean(canSend);
        buf.writeVarLong(channelSwitchCooldownEndTick);
        buf.writeBoolean(channelLocked);
        buf.writeBoolean(localMeetingParticipant);
        buf.writeVarLong(meetingCooldownEndTick);
        buf.writeVarInt(localMeetingCallsRemaining);
        writeList(buf, connections, (targetBuf, row) -> row.write(targetBuf));
        writeList(buf, chat, (targetBuf, row) -> row.write(targetBuf));
        meeting.write(buf);
        writeList(buf, suspects, (targetBuf, row) -> row.write(targetBuf));
        buf.writeVarInt(featureMask);
        writeList(buf, doorLog, (targetBuf, row) -> row.write(targetBuf));
        buf.writeVarInt(identityTasksRemaining);
        writeList(buf, drones, (targetBuf, row) -> row.write(targetBuf));
        writeList(buf, sabotage, (targetBuf, row) -> row.write(targetBuf));
        writeList(buf, sabotageLog, (targetBuf, row) -> row.write(targetBuf));
        buf.writeVarInt(cooldownChannelMask);
    }

    public static TabletSnapshot read(PacketByteBuf buf) {
        return new TabletSnapshot(
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarLong(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarLong(),
                buf.readVarInt(),
                readList(buf, PlayerRow::read),
                readList(buf, ChatRow::read),
                Meeting.read(buf),
                readList(buf, SuspectRow::read),
                buf.readVarInt(),
                readList(buf, DoorLogRow::read),
                buf.readVarInt(),
                readList(buf, DroneRow::read),
                readList(buf, SabotageRow::read),
                readList(buf, SabotageLogRow::read),
                buf.readVarInt()
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
     * {@code signal} is the public channel's reception ({@code TabletPublicRules#signal}), 0 in faction channels.
     * senderUuid 为 null 表示匿名行；匿名行绝不携带名字，客户端显示“???”。signal 为公共频道的接收信号，阵营频道为 0。
     */
    public record ChatRow(@Nullable UUID senderUuid, String senderName, String message, int signal) {
        public ChatRow {
            senderName = senderUuid == null || senderName == null ? "" : senderName;
            message = message == null ? "" : message;
            signal = Math.max(0, signal);
        }

        public ChatRow(@Nullable UUID senderUuid, String senderName, String message) {
            this(senderUuid, senderName, message, 0);
        }

        public static ChatRow hidden(String message) {
            return new ChatRow(null, "", message, 0);
        }

        public static ChatRow hidden(String message, int signal) {
            return new ChatRow(null, "", message, signal);
        }

        public boolean isAnonymous() {
            return senderUuid == null;
        }

        private void write(PacketByteBuf buf) {
            writeOptionalUuid(buf, senderUuid);
            buf.writeString(senderName);
            buf.writeString(message);
            buf.writeVarInt(signal);
        }

        private static ChatRow read(PacketByteBuf buf) {
            return new ChatRow(readOptionalUuid(buf), buf.readString(32767), buf.readString(32767), buf.readVarInt());
        }
    }

    public record Meeting(
            boolean active,
            long endTick,
            boolean localConfirmed,
            boolean localAbstained,
            @Nullable UUID localVoteTarget,
            List<VoteTarget> targets
    ) {
        public static Meeting inactive() {
            return new Meeting(false, 0, false, false, null, List.of());
        }

        /** Whole seconds left at the client's {@code worldTime}. / 按客户端 worldTime 计算的剩余整秒数。 */
        public int remainingSeconds(long worldTime) {
            return secondsUntil(endTick, worldTime);
        }

        private void write(PacketByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarLong(endTick);
            buf.writeBoolean(localConfirmed);
            buf.writeBoolean(localAbstained);
            writeOptionalUuid(buf, localVoteTarget);
            writeList(buf, targets, (targetBuf, row) -> row.write(targetBuf));
        }

        private static Meeting read(PacketByteBuf buf) {
            return new Meeting(
                    buf.readBoolean(),
                    buf.readVarLong(),
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
     * One door-log line. {@code id} is the server's monotonic entry id (unread tracking); {@code lastTick} is the world
     * tick the entry last happened, and the client derives the age from its synced world time, so an ageing log never
     * changes the snapshot. Only {@link DoorLogKind#KEY_OPENED} rows carry an actor; an anonymous actor has no uuid and
     * no name. {@code actorUuid} is the apparent (disguise-aware) identity, never a disguised player's real uuid.
     * 一行房门记录。id 为服务端单调递增的条目 id（用于未读）；lastTick 为该条目最后一次发生时的世界 tick，客户端用同步的世界时间
     * 计算经过时间，因此记录变旧不会改变快照。只有 KEY_OPENED 行带操作者；匿名操作者不带 uuid 与名字。actorUuid 是表面身份
     * （考虑伪装），绝不是伪装者的真实 uuid。
     */
    public record DoorLogRow(
            int id,
            int kindWire,
            String doorName,
            boolean hasActor,
            @Nullable UUID actorUuid,
            String actorName,
            long lastTick,
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
            lastTick = Math.max(0L, lastTick);
            count = Math.max(1, count);
        }

        public @Nullable DoorLogKind kind() {
            return DoorLogKind.fromWire(kindWire);
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
            buf.writeVarLong(lastTick);
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
            return new DoorLogRow(id, kindWire, doorName, hasActor, actorUuid, actorName, buf.readVarLong(), buf.readVarInt());
        }
    }

    /**
     * One of the viewer's own Bomber drones; non-empty only for viewers holding {@link TabletFeature#DRONE}. Rows never
     * describe another player's drones.
     * 查看者自己的一架炸弹客无人机；只有拥有 {@link TabletFeature#DRONE} 的查看者才会收到。绝不包含他人的无人机。
     *
     * @param entityId        placed drone's entity id, or -1 while it is an item / 已放置无人机的实体 id；仍为物品时为 -1
     * @param kindWire        {@code DroneKind} wire / 型号
     * @param statusWire      {@link #STATUS_ITEM} .. {@link #STATUS_FALLING} / 状态
     * @param chargePercent   battery, whole percent / 电量整数百分比
     * @param payload         grenade drone has a bound M67 / 已挂载 M67
     * @param cooldownSeconds remaining item cooldown (opening lock or 45 s loss cooldown) / 剩余冷却秒数
     * @param distance        whole blocks from the viewer to a placed drone, -1 for items / 与已放置无人机的距离（格），物品为 -1
     */
    public record DroneRow(
            int entityId,
            int kindWire,
            int statusWire,
            int chargePercent,
            boolean payload,
            int cooldownSeconds,
            int distance
    ) {
        public static final int STATUS_ITEM = 0;
        public static final int STATUS_GROUNDED = 1;
        public static final int STATUS_HOVERING = 2;
        public static final int STATUS_FLYING = 3;
        public static final int STATUS_PILOTING = 4;
        public static final int STATUS_FALLING = 5;

        public DroneRow {
            chargePercent = Math.clamp(chargePercent, 0, 100);
            cooldownSeconds = Math.max(0, cooldownSeconds);
        }

        public boolean placed() {
            return entityId >= 0;
        }

        private void write(PacketByteBuf buf) {
            buf.writeVarInt(entityId + 1);
            buf.writeVarInt(kindWire);
            buf.writeVarInt(statusWire);
            buf.writeVarInt(chargePercent);
            buf.writeBoolean(payload);
            buf.writeVarInt(cooldownSeconds);
            buf.writeVarInt(distance + 1);
        }

        private static DroneRow read(PacketByteBuf buf) {
            return new DroneRow(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt() - 1);
        }
    }

    /**
     * One sabotage card for a viewer holding {@link TabletFeature#SABOTAGE}; one row per sabotage type, in type order.
     * 拥有 {@link TabletFeature#SABOTAGE} 的查看者看到的一张破坏卡片；每种破坏一行，按类型顺序。
     *
     * @param typeWire  {@code SabotageType} wire / 破坏类型
     * @param stateWire {@code SabotageRowState} wire / 卡片状态
     * @param seconds   remaining (arming, cooldown) or elapsed (active) seconds; an active engine card counts the repair
     *                  window down while CRITICAL, then the halted time up / 剩余（准备、冷却）或已持续（进行中）秒数；
     *                  进行中的动力卡片在 CRITICAL 时是向下数的抢修剩余时间，之后是向上数的已停驶时间
     * @param modeWire  while active: {@code OutageMode} wire for lights, {@code EnginePhase} wire for the engine /
     *                  进行中时：灯光为熄灯模式，动力为动力阶段
     * @param boxes     junction boxes of this type on the map / 本图该类接线箱数量
     * @param opened    of those, pried open and not yet hacked / 其中已撬开未入侵的数量
     * @param hacked    of those, hacked / 其中已入侵的数量
     */
    public record SabotageRow(int typeWire, int stateWire, int seconds, int modeWire, int boxes, int opened,
                              int hacked) {
        public SabotageRow {
            seconds = Math.max(0, seconds);
            boxes = Math.max(0, boxes);
            opened = Math.max(0, opened);
            hacked = Math.max(0, hacked);
        }

        private void write(PacketByteBuf buf) {
            buf.writeVarInt(typeWire);
            buf.writeVarInt(stateWire);
            buf.writeVarInt(seconds);
            buf.writeVarInt(modeWire);
            buf.writeVarInt(boxes);
            buf.writeVarInt(opened);
            buf.writeVarInt(hacked);
        }

        private static SabotageRow read(PacketByteBuf buf) {
            return new SabotageRow(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /**
     * One line of the killers' sabotage log, newest first. {@code actor} is a game name or empty: empty means the
     * line has no actor, or the viewer has not linked with the actor (the client then shows "???").
     * 杀手破坏日志的一行，最新在前。actor 为游戏名或空：空表示这一行没有操作者，或查看者与操作者未互认（客户端显示「???」）。
     *
     * @param kindWire     {@code SabotageWorldComponent.LogKind} wire / 日志类型
     * @param typeWire     {@code SabotageType} wire / 破坏类型
     * @param roundSeconds seconds since the round started / 距本局开始的秒数
     */
    public record SabotageLogRow(int kindWire, int typeWire, String actor, int roundSeconds) {
        public SabotageLogRow {
            actor = actor == null ? "" : actor;
            roundSeconds = Math.max(0, roundSeconds);
        }

        private void write(PacketByteBuf buf) {
            buf.writeVarInt(kindWire);
            buf.writeVarInt(typeWire);
            buf.writeString(actor);
            buf.writeVarInt(roundSeconds);
        }

        private static SabotageLogRow read(PacketByteBuf buf) {
            return new SabotageLogRow(buf.readVarInt(), buf.readVarInt(), buf.readString(32767), buf.readVarInt());
        }
    }
}
