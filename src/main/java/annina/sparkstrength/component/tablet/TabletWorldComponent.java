package annina.sparkstrength.component.tablet;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletChannelRules;
import annina.sparkstrength.tablet.TabletLinkRules;
import annina.sparkstrength.tablet.TabletRules;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-owned tablet network state; clients receive filtered snapshots through custom packets.
 * 服务端持有的平板网络状态；客户端通过专用网络包接收过滤后的快照。
 *
 * <p>NBT: {@code ChatHistory} keeps its legacy entry shape and holds police chat only, so pre-channel saves load as
 * police chat and older builds never read killer/witch chat. {@code ChannelChatHistory} holds the other channels.
 * NBT：ChatHistory 保持旧条目结构且只存义警聊天，旧存档按义警读取，旧版本也读不到杀手/魔女聊天；其余频道存于 ChannelChatHistory。</p>
 */
public final class TabletWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<TabletWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("tablet_world"),
            TabletWorldComponent.class
    );

    private final World world;
    private final EnumMap<TabletChannel, ArrayDeque<ChatMessage>> chatHistories = new EnumMap<>(TabletChannel.class);
    private final LinkedHashSet<UUID> suspects = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, LinkedHashSet<UUID>> removalApprovals = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Integer> meetingCalls = new LinkedHashMap<>();
    private @Nullable Meeting meeting;
    private long meetingCooldownEndTick;
    private int emergencyMeetingChances = TabletRules.DEFAULT_EMERGENCY_MEETING_CHANCES;
    private int meetingDurationTicks = TabletRules.DEFAULT_MEETING_DURATION_TICKS;
    // Round-scoped and in-memory only (never NBT): a restart simply re-derives channels from live identity.
    // 仅本局内存状态（绝不写入 NBT）：重启后直接按实时身份重新推导频道。
    private final HashMap<UUID, TabletChannel> selectedChannels = new HashMap<>();
    private final HashMap<UUID, Long> channelSwitchTicks = new HashMap<>();
    private final HashMap<UUID, EnumSet<TabletChannel>> frozenAllowedChannels = new HashMap<>();
    private final HashMap<UUID, Long> snapshotRequestTicks = new HashMap<>();
    private final LinkedHashSet<UUID> syncedViewers = new LinkedHashSet<>();
    // Identity links and pending requests are round-scoped and never persisted: a restart fails closed to "???".
    // 身份互认与待处理请求仅限本局且绝不持久化：重启后安全回退为“???”。
    private final HashMap<UUID, HashSet<UUID>> identityLinks = new HashMap<>();
    private final LinkedHashMap<LinkKey, LinkRequest> linkRequests = new LinkedHashMap<>();
    private final HashMap<UUID, LastLinkGesture> lastLinkGestures = new HashMap<>();

    public TabletWorldComponent(World world) {
        this.world = world;
    }

    public List<ChatMessage> chatHistory(@Nullable TabletChannel channel) {
        ArrayDeque<ChatMessage> history = channel == null ? null : chatHistories.get(channel);
        return history == null ? List.of() : List.copyOf(history);
    }

    public void addChatMessage(TabletChannel channel, UUID senderUuid, String senderName, String message, long timeMillis) {
        appendCapped(
                chatHistories.computeIfAbsent(channel, ignored -> new ArrayDeque<>()),
                new ChatMessage(senderUuid, senderName, message, timeMillis)
        );
    }

    public @Nullable TabletChannel selectedChannel(UUID playerUuid) {
        return selectedChannels.get(playerUuid);
    }

    public void setSelectedChannel(UUID playerUuid, @Nullable TabletChannel channel) {
        if (channel == null) {
            selectedChannels.remove(playerUuid);
        } else {
            selectedChannels.put(playerUuid, channel);
        }
    }

    public int channelSwitchCooldownTicks(UUID playerUuid, long currentTick) {
        Long lastSwitch = channelSwitchTicks.get(playerUuid);
        if (lastSwitch == null) {
            return 0;
        }
        long remaining = lastSwitch + TabletChannelRules.SWITCH_COOLDOWN_TICKS - currentTick;
        return (int) Math.max(0, Math.min(TabletChannelRules.SWITCH_COOLDOWN_TICKS, remaining));
    }

    public void recordChannelSwitch(UUID playerUuid, long currentTick) {
        channelSwitchTicks.put(playerUuid, currentTick);
    }

    /**
     * Last allowed set seen while the holder was alive. SparkTraits clears traits at death, so dead holders must
     * read this instead of re-deriving (a dead Impostor would otherwise lose both networks).
     * 持有者存活时最后一次的允许频道。SparkTraits 会在死亡时清除天赋，死亡持有者必须读取此值而非重新推导。
     */
    public @Nullable EnumSet<TabletChannel> frozenAllowedChannels(UUID playerUuid) {
        EnumSet<TabletChannel> frozen = frozenAllowedChannels.get(playerUuid);
        return frozen == null ? null : EnumSet.copyOf(frozen);
    }

    public void freezeAllowedChannels(UUID playerUuid, EnumSet<TabletChannel> allowed) {
        frozenAllowedChannels.put(playerUuid, EnumSet.copyOf(allowed));
    }

    /** Accepts at most one snapshot reply per interval per player. / 每名玩家每个间隔最多响应一次快照请求。 */
    public boolean tryAcceptSnapshotRequest(UUID playerUuid, long currentTick, int intervalTicks) {
        Long last = snapshotRequestTicks.get(playerUuid);
        if (last != null && currentTick >= last && currentTick - last < intervalTicks) {
            return false;
        }
        snapshotRequestTicks.put(playerUuid, currentTick);
        return true;
    }

    /**
     * Players that currently hold a non-empty client snapshot; the sync pass sends them an empty one on revoke.
     * 当前持有非空客户端快照的玩家；同步轮次会在其失去访问时发送空快照。
     */
    public Set<UUID> syncedViewers() {
        return Set.copyOf(syncedViewers);
    }

    public void markSyncedViewer(UUID playerUuid) {
        syncedViewers.add(playerUuid);
    }

    public void forgetSyncedViewer(UUID playerUuid) {
        syncedViewers.remove(playerUuid);
    }

    public Set<UUID> identityLinks(UUID player) {
        HashSet<UUID> links = identityLinks.get(player);
        return links == null ? Set.of() : Set.copyOf(links);
    }

    public boolean isLinked(UUID a, UUID b) {
        HashSet<UUID> links = identityLinks.get(a);
        return links != null && links.contains(b);
    }

    /** Symmetric and pairwise only; never transitive. / 仅成对且对称，绝不传递。 */
    public void link(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return;
        }
        identityLinks.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
        identityLinks.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
    }

    public @Nullable LinkRequest linkRequest(UUID from, UUID to) {
        return linkRequests.get(new LinkKey(from, to));
    }

    public void putLinkRequest(LinkRequest request) {
        if (request != null) {
            linkRequests.put(new LinkKey(request.from(), request.to()), request);
        }
    }

    public void removeLinkRequest(UUID from, UUID to) {
        linkRequests.remove(new LinkKey(from, to));
    }

    /** All stored requests, expired ones included; the caller prunes. / 所有已存请求（含已过期），由调用方清理。 */
    public List<LinkRequest> linkRequests() {
        return List.copyOf(linkRequests.values());
    }

    /**
     * Stores the actor's latest link gesture and reports whether it repeats the previous one (held use key).
     * 记录发起者最近一次互认操作，并返回其是否为上一次操作的重复触发（按住使用键）。
     */
    public boolean recordLinkGesture(UUID actor, UUID target, long now) {
        LastLinkGesture previous = lastLinkGestures.put(actor, new LastLinkGesture(target, now));
        return previous != null && TabletLinkRules.isHeldRepeat(previous.target(), previous.tick(), target, now);
    }

    public Set<UUID> suspects() {
        return Collections.unmodifiableSet(suspects);
    }

    public boolean isSuspect(UUID uuid) {
        return suspects.contains(uuid);
    }

    public boolean addSuspect(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return suspects.add(uuid);
    }

    public boolean removeSuspect(UUID uuid) {
        boolean removed = suspects.remove(uuid);
        removalApprovals.remove(uuid);
        return removed;
    }

    public Set<UUID> removalApprovals(UUID suspectUuid) {
        return Collections.unmodifiableSet(removalApprovals.getOrDefault(suspectUuid, new LinkedHashSet<>()));
    }

    public void setRemovalApproval(UUID suspectUuid, UUID voterUuid, boolean approved) {
        if (!suspects.contains(suspectUuid)) {
            return;
        }
        LinkedHashSet<UUID> approvals = removalApprovals.computeIfAbsent(suspectUuid, ignored -> new LinkedHashSet<>());
        if (approved) {
            approvals.add(voterUuid);
        } else {
            approvals.remove(voterUuid);
        }
        if (approvals.isEmpty()) {
            removalApprovals.remove(suspectUuid);
        }
    }

    public void pruneRemovalApprovals(Set<UUID> electorate) {
        removalApprovals.values().forEach(approvals -> approvals.removeIf(voter -> !electorate.contains(voter)));
        removalApprovals.entrySet().removeIf(entry -> entry.getValue().isEmpty() || !suspects.contains(entry.getKey()));
    }

    public boolean hasActiveMeeting() {
        return meeting != null;
    }

    public @Nullable Meeting meeting() {
        return meeting;
    }

    public int emergencyMeetingChances() {
        return emergencyMeetingChances;
    }

    public void setEmergencyMeetingChances(int chances) {
        emergencyMeetingChances = Math.max(0, chances);
    }

    public int meetingDurationTicks() {
        return meetingDurationTicks;
    }

    public void setMeetingDurationTicks(int ticks) {
        meetingDurationTicks = Math.max(1, ticks);
    }

    public int usedMeetingCalls(UUID playerUuid) {
        return meetingCalls.getOrDefault(playerUuid, 0);
    }

    public int remainingMeetingCalls(UUID playerUuid) {
        return Math.max(0, emergencyMeetingChances - usedMeetingCalls(playerUuid));
    }

    public boolean canCallMeeting(UUID playerUuid) {
        return remainingMeetingCalls(playerUuid) > 0;
    }

    public void recordMeetingCall(UUID playerUuid) {
        meetingCalls.merge(playerUuid, 1, Integer::sum);
    }

    public void startMeeting() {
        meeting = new Meeting(meetingDurationTicks);
    }

    public void clearMeeting() {
        meeting = null;
    }

    public void startMeetingCooldown(long currentTick) {
        meetingCooldownEndTick = currentTick + TabletRules.MEETING_COOLDOWN_TICKS;
    }

    public int meetingCooldownTicks(long currentTick) {
        return (int) Math.max(0, meetingCooldownEndTick - currentTick);
    }

    public void clearRoundState() {
        chatHistories.clear();
        suspects.clear();
        removalApprovals.clear();
        meetingCalls.clear();
        meeting = null;
        meetingCooldownEndTick = 0;
        selectedChannels.clear();
        channelSwitchTicks.clear();
        frozenAllowedChannels.clear();
        snapshotRequestTicks.clear();
        identityLinks.clear();
        linkRequests.clear();
        lastLinkGestures.clear();
        // syncedViewers is kept on purpose: the sync pass after a round clear must still revoke stale snapshots.
        // 有意保留 syncedViewers：清局后的同步轮次仍需向其撤销过期快照。
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return false;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ArrayDeque<ChatMessage> policeChat = chatHistories.get(TabletChannel.POLICE);
        if (policeChat != null && !policeChat.isEmpty()) {
            tag.put("ChatHistory", chatMessagesToNbt(policeChat));
        }
        NbtList channelChats = new NbtList();
        for (Map.Entry<TabletChannel, ArrayDeque<ChatMessage>> entry : chatHistories.entrySet()) {
            if (entry.getKey() == TabletChannel.POLICE || entry.getValue().isEmpty()) {
                continue;
            }
            NbtCompound channelTag = new NbtCompound();
            channelTag.putString("Channel", entry.getKey().id());
            channelTag.put("Messages", chatMessagesToNbt(entry.getValue()));
            channelChats.add(channelTag);
        }
        if (!channelChats.isEmpty()) {
            tag.put("ChannelChatHistory", channelChats);
        }

        if (!suspects.isEmpty()) {
            NbtList suspectTags = new NbtList();
            for (UUID suspect : suspects) {
                NbtCompound suspectTag = new NbtCompound();
                suspectTag.putUuid("Uuid", suspect);
                suspectTags.add(suspectTag);
            }
            tag.put("Suspects", suspectTags);
        }

        if (!removalApprovals.isEmpty()) {
            NbtList approvalsTags = new NbtList();
            for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : removalApprovals.entrySet()) {
                NbtCompound approvalTag = new NbtCompound();
                approvalTag.putUuid("Suspect", entry.getKey());
                NbtList voters = new NbtList();
                for (UUID voter : entry.getValue()) {
                    NbtCompound voterTag = new NbtCompound();
                    voterTag.putUuid("Uuid", voter);
                    voters.add(voterTag);
                }
                approvalTag.put("Voters", voters);
                approvalsTags.add(approvalTag);
            }
            tag.put("RemovalApprovals", approvalsTags);
        }

        if (meeting != null) {
            tag.put("Meeting", meeting.toNbt());
        }
        if (meetingCooldownEndTick > 0) {
            tag.putLong("MeetingCooldownEndTick", meetingCooldownEndTick);
        }
        tag.putInt("EmergencyMeetingChances", emergencyMeetingChances);
        tag.putInt("MeetingDurationTicks", meetingDurationTicks);

        if (!meetingCalls.isEmpty()) {
            NbtList callTags = new NbtList();
            for (Map.Entry<UUID, Integer> entry : meetingCalls.entrySet()) {
                NbtCompound callTag = new NbtCompound();
                callTag.putUuid("Uuid", entry.getKey());
                callTag.putInt("Count", entry.getValue());
                callTags.add(callTag);
            }
            tag.put("MeetingCalls", callTags);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        chatHistories.clear();
        // Legacy and current police chat share this key. / 旧版与当前的义警聊天共用此键。
        readChatMessages(tag.getList("ChatHistory", NbtElement.COMPOUND_TYPE), TabletChannel.POLICE);
        NbtList channelChats = tag.getList("ChannelChatHistory", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < channelChats.size(); i++) {
            NbtCompound channelTag = channelChats.getCompound(i);
            TabletChannel channel = TabletChannel.fromId(channelTag.getString("Channel"));
            // Police chat only ever comes from ChatHistory; unknown or missing channels are dropped.
            // 义警聊天只从 ChatHistory 读取；未知或缺失的频道直接丢弃。
            if (channel == null || channel == TabletChannel.POLICE) {
                continue;
            }
            readChatMessages(channelTag.getList("Messages", NbtElement.COMPOUND_TYPE), channel);
        }

        suspects.clear();
        NbtList suspectTags = tag.getList("Suspects", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < suspectTags.size(); i++) {
            NbtCompound suspectTag = suspectTags.getCompound(i);
            if (suspectTag.containsUuid("Uuid")) {
                suspects.add(suspectTag.getUuid("Uuid"));
            }
        }

        removalApprovals.clear();
        NbtList approvalsTags = tag.getList("RemovalApprovals", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < approvalsTags.size(); i++) {
            NbtCompound approvalTag = approvalsTags.getCompound(i);
            if (!approvalTag.containsUuid("Suspect")) {
                continue;
            }
            LinkedHashSet<UUID> voters = new LinkedHashSet<>();
            NbtList voterTags = approvalTag.getList("Voters", NbtElement.COMPOUND_TYPE);
            for (int voterIndex = 0; voterIndex < voterTags.size(); voterIndex++) {
                NbtCompound voterTag = voterTags.getCompound(voterIndex);
                if (voterTag.containsUuid("Uuid")) {
                    voters.add(voterTag.getUuid("Uuid"));
                }
            }
            if (!voters.isEmpty()) {
                removalApprovals.put(approvalTag.getUuid("Suspect"), voters);
            }
        }

        meeting = tag.contains("Meeting", NbtElement.COMPOUND_TYPE)
                ? Meeting.fromNbt(tag.getCompound("Meeting"))
                : null;
        meetingCooldownEndTick = tag.contains("MeetingCooldownEndTick", NbtElement.NUMBER_TYPE)
                ? tag.getLong("MeetingCooldownEndTick")
                : 0;
        emergencyMeetingChances = tag.contains("EmergencyMeetingChances", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("EmergencyMeetingChances"))
                : TabletRules.DEFAULT_EMERGENCY_MEETING_CHANCES;
        meetingDurationTicks = tag.contains("MeetingDurationTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(1, tag.getInt("MeetingDurationTicks"))
                : TabletRules.DEFAULT_MEETING_DURATION_TICKS;

        meetingCalls.clear();
        NbtList callTags = tag.getList("MeetingCalls", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < callTags.size(); i++) {
            NbtCompound callTag = callTags.getCompound(i);
            if (callTag.containsUuid("Uuid")) {
                meetingCalls.put(callTag.getUuid("Uuid"), Math.max(0, callTag.getInt("Count")));
            }
        }
    }

    private void readChatMessages(NbtList messages, TabletChannel channel) {
        for (int i = 0; i < messages.size(); i++) {
            NbtCompound messageTag = messages.getCompound(i);
            if (messageTag.containsUuid("Sender")) {
                addChatMessage(
                        channel,
                        messageTag.getUuid("Sender"),
                        messageTag.getString("SenderName"),
                        messageTag.getString("Message"),
                        messageTag.getLong("Time")
                );
            }
        }
    }

    private static NbtList chatMessagesToNbt(Collection<ChatMessage> history) {
        NbtList messages = new NbtList();
        for (ChatMessage message : history) {
            NbtCompound messageTag = new NbtCompound();
            messageTag.putUuid("Sender", message.senderUuid());
            messageTag.putString("SenderName", message.senderName());
            messageTag.putString("Message", message.message());
            messageTag.putLong("Time", message.timeMillis());
            messages.add(messageTag);
        }
        return messages;
    }

    private static void appendCapped(ArrayDeque<ChatMessage> history, ChatMessage message) {
        history.addLast(message);
        while (history.size() > TabletChannelRules.CHAT_HISTORY_LIMIT) {
            history.removeFirst();
        }
    }

    public record ChatMessage(UUID senderUuid, String senderName, String message, long timeMillis) {
    }

    /**
     * Pending one-way link request, keyed by (from, to).
     * 待处理的单向互认请求，按 (from, to) 存储。
     *
     * @param notifyTarget whether the target is shown the countdown prompt (only eligible targets are) / 是否向目标显示倒计时提示（仅合格目标）
     * @param fromName     requester name captured for that prompt / 为该提示记录的请求者名字
     */
    public record LinkRequest(UUID from, UUID to, long expiryTick, boolean notifyTarget, String fromName) {
    }

    private record LinkKey(UUID from, UUID to) {
    }

    private record LastLinkGesture(UUID target, long tick) {
    }

    public static final class Meeting {
        private int ticksRemaining;
        private final LinkedHashMap<UUID, UUID> votes = new LinkedHashMap<>();
        private final LinkedHashSet<UUID> abstentions = new LinkedHashSet<>();
        private final LinkedHashSet<UUID> confirmed = new LinkedHashSet<>();

        private Meeting(int ticksRemaining) {
            this.ticksRemaining = Math.max(0, ticksRemaining);
        }

        public int ticksRemaining() {
            return ticksRemaining;
        }

        public void tick() {
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        }

        public boolean isExpired() {
            return ticksRemaining <= 0;
        }

        public Map<UUID, UUID> votes() {
            return Collections.unmodifiableMap(votes);
        }

        public Set<UUID> abstentions() {
            return Collections.unmodifiableSet(abstentions);
        }

        public Set<UUID> confirmed() {
            return Collections.unmodifiableSet(confirmed);
        }

        public boolean isConfirmed(UUID voterUuid) {
            return confirmed.contains(voterUuid);
        }

        public boolean isAbstaining(UUID voterUuid) {
            return abstentions.contains(voterUuid);
        }

        public Optional<UUID> voteFor(UUID voterUuid) {
            return Optional.ofNullable(votes.get(voterUuid));
        }

        public void castVote(UUID voterUuid, @Nullable UUID targetUuid) {
            if (confirmed.contains(voterUuid)) {
                return;
            }
            votes.remove(voterUuid);
            abstentions.remove(voterUuid);
            if (targetUuid == null) {
                abstentions.add(voterUuid);
            } else {
                votes.put(voterUuid, targetUuid);
            }
        }

        public void confirm(UUID voterUuid) {
            confirmed.add(voterUuid);
        }

        public boolean allConfirmed(Set<UUID> currentParticipants) {
            return !currentParticipants.isEmpty() && confirmed.containsAll(currentParticipants);
        }

        public void pruneVotes(Set<UUID> currentParticipants) {
            votes.entrySet().removeIf(entry -> !currentParticipants.contains(entry.getKey()));
            abstentions.removeIf(voter -> !currentParticipants.contains(voter));
            confirmed.removeIf(voter -> !currentParticipants.contains(voter));
        }

        private NbtCompound toNbt() {
            NbtCompound tag = new NbtCompound();
            tag.putInt("TicksRemaining", ticksRemaining);

            NbtList voteTags = new NbtList();
            for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
                NbtCompound voteTag = new NbtCompound();
                voteTag.putUuid("Voter", entry.getKey());
                voteTag.putUuid("Target", entry.getValue());
                voteTags.add(voteTag);
            }
            tag.put("Votes", voteTags);

            tag.put("Abstentions", uuidList(abstentions));
            tag.put("Confirmed", uuidList(confirmed));
            return tag;
        }

        private static Meeting fromNbt(NbtCompound tag) {
            Meeting meeting = new Meeting(tag.getInt("TicksRemaining"));

            NbtList voteTags = tag.getList("Votes", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < voteTags.size(); i++) {
                NbtCompound voteTag = voteTags.getCompound(i);
                if (voteTag.containsUuid("Voter") && voteTag.containsUuid("Target")) {
                    meeting.votes.put(voteTag.getUuid("Voter"), voteTag.getUuid("Target"));
                }
            }

            readUuidList(tag.getList("Abstentions", NbtElement.COMPOUND_TYPE), meeting.abstentions);
            readUuidList(tag.getList("Confirmed", NbtElement.COMPOUND_TYPE), meeting.confirmed);
            return meeting;
        }

        private static NbtList uuidList(Set<UUID> uuids) {
            NbtList list = new NbtList();
            for (UUID uuid : uuids) {
                NbtCompound tag = new NbtCompound();
                tag.putUuid("Uuid", uuid);
                list.add(tag);
            }
            return list;
        }

        private static void readUuidList(NbtList tags, Set<UUID> uuids) {
            for (int i = 0; i < tags.size(); i++) {
                NbtCompound tag = tags.getCompound(i);
                if (tag.containsUuid("Uuid")) {
                    uuids.add(tag.getUuid("Uuid"));
                }
            }
        }
    }
}
