package annina.sparkstrength.tablet;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.network.tablet.OpenTabletScreenS2CPacket;
import annina.sparkstrength.network.tablet.SyncTabletSnapshotS2CPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative server actions for the tablet network.
 * 平板网络的服务端权威操作入口。
 *
 * <p>Every snapshot is redacted to the viewer's selected channel: connections and chat come from that channel only,
 * and meeting/suspect data (fields, action-bar broadcasts, meeting-driven syncs) reach police members only.
 * 每份快照都按观看者所选频道裁剪：成员与聊天只来自该频道，会议/嫌疑人数据（字段、动作栏广播、会议同步）只发给义警成员。</p>
 *
 * <p>Anonymous channels ({@link TabletChannel#anonymousSenders()}) are additionally redacted per viewer by link state:
 * an unlinked sender's chat row carries no UUID or name, and unlinked members never enter the member list (anonymous
 * channels draw no outlines at all, see TabletClientHighlights). Membership and push fan-out stay identity-based.
 * 匿名频道还会按观看者的互认状态逐人裁剪：未互认发送者的聊天行不含 UUID 与名字，未互认成员不会进入成员列表
 * （匿名频道完全不绘制描边，见 TabletClientHighlights）。
 * 成员资格与推送范围仍由身份决定。</p>
 */
public final class TabletStateService {
    private TabletStateService() {
    }

    public static void openTablet(ServerPlayerEntity player) {
        if (!TabletAccess.hasTabletInHotbar(player)) {
            player.sendMessage(Text.translatable("message.sparkstrength.tablet.no_tablet"), true);
            return;
        }
        // Snapshot first so the screen never opens on stale (possibly other-channel) data.
        // 先发快照，避免界面以过期（可能属于其他频道）的数据打开。
        syncTo(player);
        ServerPlayNetworking.send(player, new OpenTabletScreenS2CPacket());
    }

    /**
     * Sends this player's current view; a player without a tablet receives an empty snapshot instead.
     * 发送该玩家的当前视图；未持有平板的玩家收到空快照。
     */
    public static void syncTo(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (!TabletAccess.hasTabletInHotbar(player)) {
            revoke(player);
            return;
        }
        SyncPass pass = prepare(player.getServerWorld());
        sendSnapshot(player, buildSnapshot(player, pass.roster()));
    }

    /**
     * Syncs both sides of a link change from one roster pass so each sees the other's name at once.
     * 在同一名册轮次中同步互认双方，使双方立即看到彼此的名字。
     */
    public static void syncPair(@Nullable ServerPlayerEntity a, @Nullable ServerPlayerEntity b) {
        if (a == null || b == null || a == b || a.getServerWorld() != b.getServerWorld()) {
            // A roster only covers its own world; never build one player's view from another world's pass.
            // 名册只覆盖本世界；绝不能用其他世界的轮次构建某名玩家的视图。
            syncTo(a);
            if (b != a) {
                syncTo(b);
            }
            return;
        }
        SyncPass pass = prepare(a.getServerWorld());
        for (ServerPlayerEntity player : List.of(a, b)) {
            if (TabletAccess.hasTabletInHotbar(player)) {
                sendSnapshot(player, buildSnapshot(player, pass.roster()));
            } else {
                revoke(player);
            }
        }
    }

    /**
     * Full sync pass: resolve every holder once, send each its snapshot, then revoke viewers that lost the tablet.
     * 完整同步轮次：每个持有者只解析一次并各自发送快照，随后撤销已失去平板的观看者。
     */
    public static void syncToTabletHolders(ServerWorld world) {
        syncAll(prepare(world));
    }

    /** Client polling entry; throttled per player. / 客户端轮询入口；按玩家限流。 */
    public static void requestSnapshot(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (acceptSnapshotRequest(player)) {
            syncTo(player);
        }
    }

    public static void sendChat(ServerPlayerEntity sender, int expectedChannelWire, String rawMessage) {
        if (!TabletAccess.hasTabletInHotbar(sender)) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.no_tablet"), true);
            return;
        }
        TabletChannelResolver.Access access = TabletChannelResolver.access(sender);
        TabletChannel channel = access.selected();
        if (channel == null) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.no_channel"), true);
            return;
        }
        if (!access.canSend()) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.chat_read_only"), true);
            return;
        }
        // The server selection is authoritative; a stale client view must never post into another network.
        // 以服务端选择为准；过期的客户端视图绝不能把消息发进其他网络。
        if (expectedChannelWire != channel.wire()) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.channel_changed"), true);
            syncTo(sender);
            return;
        }
        String message = TabletRules.sanitizeChatMessage(rawMessage);
        if (message.isEmpty()) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.chat_empty"), true);
            return;
        }
        ServerWorld world = sender.getServerWorld();
        TabletWorldComponent.KEY.get(world).addChatMessage(
                channel,
                sender.getUuid(),
                sender.getName().getString(),
                message,
                Instant.now().toEpochMilli()
        );
        syncChannelMembers(prepare(world), channel);
    }

    public static void selectChannel(ServerPlayerEntity player, int channelWire) {
        if (!TabletAccess.hasTabletInHotbar(player)) {
            player.sendMessage(Text.translatable("message.sparkstrength.tablet.no_tablet"), true);
            return;
        }
        ServerWorld world = player.getServerWorld();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        TabletChannelResolver.Access access = TabletChannelResolver.access(player);
        TabletChannel target = TabletChannel.fromWire(channelWire);
        int cooldownTicks = tablet.channelSwitchCooldownTicks(player.getUuid(), world.getTime());
        TabletChannelRules.SwitchResult result = TabletChannelRules.canSwitch(
                access.allowed(),
                access.selected(),
                target,
                tablet.hasActiveMeeting(),
                cooldownTicks
        );
        switch (result) {
            case OK -> {
                TabletChannel selected = Objects.requireNonNull(target);
                tablet.setSelectedChannel(player.getUuid(), selected);
                tablet.recordChannelSwitch(player.getUuid(), world.getTime());
                player.sendMessage(Text.translatable(
                        "message.sparkstrength.tablet.channel_switched",
                        Text.translatable(selected.translationKey())
                ), true);
                syncTo(player);
            }
            case UNCHANGED -> resyncThrottled(player);
            case DENIED -> {
                player.sendMessage(Text.translatable("message.sparkstrength.tablet.channel_denied"), true);
                resyncThrottled(player);
            }
            case COOLDOWN -> {
                player.sendMessage(Text.translatable(
                        "message.sparkstrength.tablet.channel_cooldown",
                        TabletRules.secondsCeil(cooldownTicks)
                ), true);
                resyncThrottled(player);
            }
            case MEETING_LOCKED -> {
                player.sendMessage(Text.translatable("message.sparkstrength.tablet.channel_meeting_locked"), true);
                resyncThrottled(player);
            }
        }
    }

    public static void callMeeting(ServerPlayerEntity caller) {
        TabletChannelResolver.Access access = meetingFeatureAccess(caller);
        if (access == null) {
            return;
        }
        ServerWorld world = caller.getServerWorld();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        if (!access.isPoliceElector()) {
            caller.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_denied"), true);
            return;
        }
        if (tablet.hasActiveMeeting()) {
            caller.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_active"), true);
            return;
        }
        int cooldownTicks = tablet.meetingCooldownTicks(world.getTime());
        if (cooldownTicks > 0) {
            caller.sendMessage(Text.translatable(
                    "message.sparkstrength.tablet.meeting_cooldown",
                    TabletRules.secondsCeil(cooldownTicks)
            ), true);
            return;
        }
        if (!tablet.canCallMeeting(caller.getUuid())) {
            caller.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_chances_empty"), true);
            return;
        }

        tablet.recordMeetingCall(caller.getUuid());
        tablet.startMeeting();
        SyncPass pass = prepare(world);
        broadcastToChannelMembers(pass, TabletChannel.POLICE, Text.translatable("message.sparkstrength.tablet.meeting_started"));
        syncChannelMembers(pass, TabletChannel.POLICE);
    }

    public static void castVote(ServerPlayerEntity voter, @Nullable UUID targetUuid) {
        TabletChannelResolver.Access access = meetingFeatureAccess(voter);
        if (access == null) {
            return;
        }
        ServerWorld world = voter.getServerWorld();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        TabletWorldComponent.Meeting meeting = tablet.meeting();
        if (!access.isPoliceElector() || meeting == null) {
            voter.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_denied"), true);
            return;
        }
        if (meeting.isConfirmed(voter.getUuid())) {
            voter.sendMessage(Text.translatable("message.sparkstrength.tablet.vote_locked"), true);
            return;
        }
        if (targetUuid != null && !isSelectableMeetingTarget(world, tablet, targetUuid)) {
            return;
        }

        meeting.castVote(voter.getUuid(), targetUuid);
        voter.sendMessage(Text.translatable("message.sparkstrength.tablet.vote_recorded"), true);
        syncChannelMembers(prepare(world), TabletChannel.POLICE);
    }

    public static void confirmVote(ServerPlayerEntity voter) {
        TabletChannelResolver.Access access = meetingFeatureAccess(voter);
        if (access == null) {
            return;
        }
        ServerWorld world = voter.getServerWorld();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        TabletWorldComponent.Meeting meeting = tablet.meeting();
        if (!access.isPoliceElector() || meeting == null) {
            voter.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_denied"), true);
            return;
        }

        meeting.confirm(voter.getUuid());
        voter.sendMessage(Text.translatable("message.sparkstrength.tablet.vote_confirmed"), true);
        SyncPass pass = prepare(world);
        if (meeting.allConfirmed(pass.electorate())) {
            finishMeeting(pass);
        } else {
            syncChannelMembers(pass, TabletChannel.POLICE);
        }
    }

    public static void setSuspectRemovalApproval(ServerPlayerEntity voter, UUID suspectUuid, boolean approved) {
        TabletChannelResolver.Access access = meetingFeatureAccess(voter);
        if (access == null) {
            return;
        }
        ServerWorld world = voter.getServerWorld();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        if (!access.isPoliceElector()) {
            voter.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_denied"), true);
            return;
        }
        if (!tablet.isSuspect(suspectUuid)) {
            syncTo(voter);
            return;
        }

        tablet.setRemovalApproval(suspectUuid, voter.getUuid(), approved);
        SyncPass pass = prepare(world);
        int approvals = tablet.removalApprovals(suspectUuid).size();
        if (TabletRules.meetsTwoThirds(approvals, pass.electorate().size())) {
            String suspectName = playerName(world, suspectUuid);
            tablet.removeSuspect(suspectUuid);
            broadcastToChannelMembers(pass, TabletChannel.POLICE,
                    Text.translatable("message.sparkstrength.tablet.suspect_removed", suspectName));
        }
        syncChannelMembers(pass, TabletChannel.POLICE);
    }

    public static void tick(ServerWorld world) {
        TabletLinkService.tick(world);
        boolean periodic = world.getTime() % TabletRules.SYNC_INTERVAL_TICKS == 0;
        TabletWorldComponent.Meeting meeting = TabletWorldComponent.KEY.get(world).meeting();
        if (meeting == null) {
            if (periodic) {
                syncToTabletHolders(world);
            }
            return;
        }

        // One roster per tick while a meeting runs: the electorate drives vote pruning and auto-finish.
        // 会议期间每 tick 只构建一次名册：选民集合决定投票清理与提前结束。
        SyncPass pass = prepare(world);
        if (periodic) {
            syncAll(pass);
        }
        Set<UUID> participants = pass.electorate();
        meeting.pruneVotes(participants);
        meeting.tick();
        if (meeting.isExpired() || meeting.allConfirmed(participants)) {
            finishMeeting(pass);
        } else if (meeting.ticksRemaining() % 20 == 0) {
            syncChannelMembers(pass, TabletChannel.POLICE);
        }
    }

    public static void clearRoundState(ServerWorld world) {
        TabletWorldComponent.KEY.get(world).clearRoundState();
        syncToTabletHolders(world);
    }

    /**
     * Pure per-viewer read of the redacted snapshot; {@code roster} must come from the same sync pass.
     * 纯读取：按观看者生成裁剪后的快照；roster 必须来自同一同步轮次。
     */
    public static TabletSnapshot buildSnapshot(ServerPlayerEntity viewer, Map<UUID, TabletChannelResolver.Access> roster) {
        TabletChannelResolver.Access access = roster.get(viewer.getUuid());
        if (access == null) {
            return TabletSnapshot.empty();
        }
        ServerWorld world = viewer.getServerWorld();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        UUID viewerUuid = viewer.getUuid();
        long now = world.getTime();
        TabletChannel channel = access.selected();
        boolean meetingFeatures = access.viewsMeetingFeatures();
        Set<UUID> electorate = meetingFeatures ? TabletAccess.policeElectorate(roster) : Set.of();
        Set<UUID> links = tablet.identityLinks(viewerUuid);

        return new TabletSnapshot(
                true,
                TabletChannel.wireOf(channel),
                access.allowedMask(),
                access.canSend(),
                TabletRules.secondsCeil(tablet.channelSwitchCooldownTicks(viewerUuid, now)),
                tablet.hasActiveMeeting() && channel == TabletChannel.POLICE && access.canSwitchChannels(),
                meetingFeatures && electorate.contains(viewerUuid),
                meetingFeatures ? TabletRules.secondsCeil(tablet.meetingCooldownTicks(now)) : 0,
                meetingFeatures ? tablet.remainingMeetingCalls(viewerUuid) : 0,
                connectionRows(world, roster, channel, viewerUuid, links),
                // Stored history keeps real senders, so earlier messages reveal once a link forms.
                // 存储的历史保留真实发送者，因此互认后之前的消息也会显示名字。
                tablet.chatHistory(channel).stream()
                        .map(message -> TabletLinkRules.revealsIdentity(channel, viewerUuid, message.senderUuid(), links)
                                ? new TabletSnapshot.ChatRow(message.senderUuid(), message.senderName(), message.message())
                                : TabletSnapshot.ChatRow.hidden(message.message()))
                        .toList(),
                meetingFeatures ? meetingSnapshot(world, tablet, viewer, electorate) : TabletSnapshot.Meeting.inactive(),
                meetingFeatures ? suspectRows(world, tablet, viewerUuid, electorate) : List.of()
        );
    }

    private static List<TabletSnapshot.PlayerRow> connectionRows(
            ServerWorld world,
            Map<UUID, TabletChannelResolver.Access> roster,
            @Nullable TabletChannel channel,
            UUID viewerUuid,
            Set<UUID> links
    ) {
        if (channel == null) {
            return List.of();
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Comparator<ServerPlayerEntity> byName =
                Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER);
        Comparator<ServerPlayerEntity> order = channel.anonymousSenders()
                ? Comparator.<ServerPlayerEntity, Boolean>comparing(player -> !player.getUuid().equals(viewerUuid))
                        .thenComparing(byName)
                : byName;
        return roster.entrySet().stream()
                .filter(entry -> entry.getValue().isMember(channel))
                // Unlinked members are dropped before any player lookup, so their UUID/name never reach the row.
                // 未互认成员在查找玩家之前就被剔除，其 UUID/名字不会进入行数据。
                .filter(entry -> TabletLinkRules.revealsIdentity(channel, viewerUuid, entry.getKey(), links))
                .map(entry -> world.getServer().getPlayerManager().getPlayer(entry.getKey()))
                .filter(Objects::nonNull)
                .sorted(order)
                .map(player -> new TabletSnapshot.PlayerRow(
                        player.getUuid(),
                        player.getName().getString(),
                        game.hasAnyRole(player.getUuid())
                ))
                .toList();
    }

    private static List<TabletSnapshot.SuspectRow> suspectRows(
            ServerWorld world,
            TabletWorldComponent tablet,
            UUID viewerUuid,
            Set<UUID> electorate
    ) {
        int required = TabletRules.requiredTwoThirds(electorate.size());
        return tablet.suspects().stream()
                .sorted(Comparator.comparing(uuid -> playerName(world, uuid), String.CASE_INSENSITIVE_ORDER))
                .map(uuid -> new TabletSnapshot.SuspectRow(
                        uuid,
                        playerName(world, uuid),
                        tablet.removalApprovals(uuid).size(),
                        required,
                        tablet.removalApprovals(uuid).contains(viewerUuid)
                ))
                .toList();
    }

    private static TabletSnapshot.Meeting meetingSnapshot(
            ServerWorld world,
            TabletWorldComponent tablet,
            ServerPlayerEntity viewer,
            Set<UUID> electorate
    ) {
        TabletWorldComponent.Meeting meeting = tablet.meeting();
        if (meeting == null) {
            return TabletSnapshot.Meeting.inactive();
        }

        Map<UUID, Integer> voteCounts = validVoteCounts(world, tablet, meeting, electorate);
        return new TabletSnapshot.Meeting(
                true,
                TabletRules.secondsCeil(meeting.ticksRemaining()),
                meeting.isConfirmed(viewer.getUuid()),
                meeting.isAbstaining(viewer.getUuid()),
                meeting.voteFor(viewer.getUuid()).orElse(null),
                GameWorldComponent.KEY.get(world).getAllPlayers().stream()
                        .sorted(Comparator.comparing(uuid -> playerName(world, uuid), String.CASE_INSENSITIVE_ORDER))
                        .map(uuid -> new TabletSnapshot.VoteTarget(
                                uuid,
                                playerName(world, uuid),
                                isSelectableMeetingTarget(world, tablet, uuid),
                                voteCounts.getOrDefault(uuid, 0)
                        ))
                        .toList()
        );
    }

    private static void finishMeeting(SyncPass pass) {
        ServerWorld world = pass.world();
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        TabletWorldComponent.Meeting meeting = tablet.meeting();
        if (meeting == null) {
            return;
        }

        Optional<UUID> winner = TabletRules.uniqueHighestVote(validVoteCounts(world, tablet, meeting, pass.electorate()));
        tablet.clearMeeting();
        tablet.startMeetingCooldown(world.getTime());
        if (winner.isPresent()) {
            tablet.addSuspect(winner.get());
            broadcastToChannelMembers(pass, TabletChannel.POLICE, Text.translatable(
                    "message.sparkstrength.tablet.suspect_added",
                    playerName(world, winner.get())
            ));
        } else {
            broadcastToChannelMembers(pass, TabletChannel.POLICE,
                    Text.translatable("message.sparkstrength.tablet.suspect_tie"));
        }
        syncChannelMembers(pass, TabletChannel.POLICE);
    }

    private static Map<UUID, Integer> validVoteCounts(
            ServerWorld world,
            TabletWorldComponent tablet,
            TabletWorldComponent.Meeting meeting,
            Set<UUID> electorate
    ) {
        LinkedHashMap<UUID, Integer> voteCounts = new LinkedHashMap<>();
        for (Map.Entry<UUID, UUID> entry : meeting.votes().entrySet()) {
            if (!electorate.contains(entry.getKey()) || !isSelectableMeetingTarget(world, tablet, entry.getValue())) {
                continue;
            }
            voteCounts.merge(entry.getValue(), 1, Integer::sum);
        }
        return voteCounts;
    }

    private static boolean isSelectableMeetingTarget(ServerWorld world, TabletWorldComponent tablet, UUID targetUuid) {
        return GameWorldComponent.KEY.get(world).hasAnyRole(targetUuid) && !tablet.isSuspect(targetUuid);
    }

    /**
     * Gate shared by every meeting/suspect action: tablet first, then the police view, before any meeting state is read.
     * 所有会议/嫌疑人操作的共同闸门：先校验平板，再校验义警视图，之后才读取任何会议状态。
     */
    private static @Nullable TabletChannelResolver.Access meetingFeatureAccess(ServerPlayerEntity player) {
        if (!TabletAccess.hasTabletInHotbar(player)) {
            player.sendMessage(Text.translatable("message.sparkstrength.tablet.meeting_denied"), true);
            return null;
        }
        TabletChannelResolver.Access access = TabletChannelResolver.access(player);
        if (!access.viewsMeetingFeatures()) {
            player.sendMessage(Text.translatable("message.sparkstrength.tablet.feature_unavailable"), true);
            return null;
        }
        return access;
    }

    /** Resolves the roster once and prunes approvals against the police electorate. / 解析一次名册并按义警选民清理赞成票。 */
    private static SyncPass prepare(ServerWorld world) {
        List<ServerPlayerEntity> holders = TabletAccess.tabletHolders(world);
        Map<UUID, TabletChannelResolver.Access> roster = TabletChannelResolver.roster(holders);
        Set<UUID> electorate = TabletAccess.policeElectorate(roster);
        TabletWorldComponent.KEY.get(world).pruneRemovalApprovals(electorate);
        return new SyncPass(world, holders, roster, electorate);
    }

    private static void syncAll(SyncPass pass) {
        for (ServerPlayerEntity holder : pass.holders()) {
            sendSnapshot(holder, buildSnapshot(holder, pass.roster()));
        }
        revokeStaleViewers(pass);
    }

    private static void syncChannelMembers(SyncPass pass, TabletChannel channel) {
        for (ServerPlayerEntity member : TabletAccess.channelMembers(pass.holders(), pass.roster(), channel)) {
            sendSnapshot(member, buildSnapshot(member, pass.roster()));
        }
    }

    private static void broadcastToChannelMembers(SyncPass pass, TabletChannel channel, Text message) {
        for (ServerPlayerEntity member : TabletAccess.channelMembers(pass.holders(), pass.roster(), channel)) {
            member.sendMessage(message, true);
        }
    }

    private static void resyncThrottled(ServerPlayerEntity player) {
        if (acceptSnapshotRequest(player)) {
            syncTo(player);
        }
    }

    private static boolean acceptSnapshotRequest(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return TabletWorldComponent.KEY.get(world).tryAcceptSnapshotRequest(
                player.getUuid(),
                world.getTime(),
                TabletRules.SNAPSHOT_REQUEST_INTERVAL_TICKS
        );
    }

    /**
     * Sends an empty snapshot to tracked viewers that no longer hold a tablet here; runs after the holder loop so a
     * round clear (which keeps the tracked set) still revokes every stale client view.
     * 向已不在此处持有平板的已跟踪观看者发送空快照；在持有者循环之后执行，确保清局（保留跟踪集合）后仍能撤销所有过期视图。
     */
    private static void revokeStaleViewers(SyncPass pass) {
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(pass.world());
        for (UUID uuid : tablet.syncedViewers()) {
            if (pass.roster().containsKey(uuid)) {
                continue;
            }
            tablet.forgetSyncedViewer(uuid);
            ServerPlayerEntity player = pass.world().getServer().getPlayerManager().getPlayer(uuid);
            // A holder in another world is synced by that world's pass. / 在其他世界持有平板的玩家由该世界的轮次同步。
            if (player != null && !TabletAccess.hasTabletInHotbar(player)) {
                sendPacket(player, TabletSnapshot.empty());
            }
        }
    }

    private static void revoke(ServerPlayerEntity player) {
        TabletWorldComponent.KEY.get(player.getServerWorld()).forgetSyncedViewer(player.getUuid());
        sendPacket(player, TabletSnapshot.empty());
    }

    private static void sendSnapshot(ServerPlayerEntity player, TabletSnapshot snapshot) {
        if (sendPacket(player, snapshot)) {
            TabletWorldComponent.KEY.get(player.getServerWorld()).markSyncedViewer(player.getUuid());
        }
    }

    /**
     * Clients without the v3 payload are skipped instead of being disconnected by unknown bytes.
     * 未注册 v3 负载的客户端直接跳过，避免因未知字节断线。
     */
    private static boolean sendPacket(ServerPlayerEntity player, TabletSnapshot snapshot) {
        if (!ServerPlayNetworking.canSend(player, SyncTabletSnapshotS2CPacket.ID)) {
            return false;
        }
        ServerPlayNetworking.send(player, new SyncTabletSnapshotS2CPacket(snapshot));
        return true;
    }

    private static String playerName(ServerWorld world, UUID uuid) {
        ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString();
        }
        GameProfile profile = GameWorldComponent.KEY.get(world).getGameProfiles().get(uuid);
        return profile == null ? uuid.toString() : profile.getName();
    }

    private record SyncPass(
            ServerWorld world,
            List<ServerPlayerEntity> holders,
            Map<UUID, TabletChannelResolver.Access> roster,
            Set<UUID> electorate
    ) {
    }
}
