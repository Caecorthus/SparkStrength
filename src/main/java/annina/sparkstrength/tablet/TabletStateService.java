package annina.sparkstrength.tablet;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.network.tablet.OpenTabletScreenS2CPacket;
import annina.sparkstrength.network.tablet.SyncTabletSnapshotS2CPacket;
import annina.sparkstrength.network.tablet.TabletChatNotifyS2CPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.record.AchievementRecords;
import annina.sparkstrength.role.attendant.DoorLogTabletSnapshots;
import annina.sparkstrength.role.bomber.drone.DroneTabletSnapshots;
import annina.sparkstrength.role.jester.JesterMomentService;
import annina.sparkstrength.role.pathogen.PathogenVisibility;
import annina.sparkstrength.sabotage.SabotageAccess;
import annina.sparkstrength.sabotage.SabotageTabletSnapshots;
import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Authoritative server actions for the tablet network.
 * 平板网络的服务端权威操作入口。
 *
 * <p>Every snapshot is redacted to the viewer's selected channel: connections and chat come from that channel only,
 * and meeting/suspect data (fields, action-bar broadcasts, meeting-driven syncs) reach police members only.
 * 每份快照都按观看者所选频道裁剪：成员与聊天只来自该频道，会议/嫌疑人数据（字段、动作栏广播、会议同步）只发给义警成员。</p>
 *
 * <p>Identities are additionally redacted per viewer by {@link TabletIdentityRules}: anonymous channels
 * ({@link TabletChannel#anonymousSenders()}, killer) by link state, task-gated channels
 * ({@link TabletChannel#revealsAfterTasks()}, police) by the viewer's completed tasks this round. A hidden sender's chat
 * row carries no UUID or name, and hidden members never enter the member list (tablets draw no member outlines, see
 * TabletClientHighlights). Membership and push fan-out stay identity-based.
 * 身份还会经 TabletIdentityRules 按观看者逐人裁剪：匿名频道（杀手）按互认状态，任务解锁频道（义警）按查看者本局已完成的
 * 任务数。被隐藏发送者的聊天行不含 UUID 与名字，被隐藏成员不会进入成员列表（平板不描边成员，见 TabletClientHighlights）。
 * 成员资格与推送范围仍由身份决定。</p>
 *
 * <p>Role-granted features ({@link TabletFeature}) ride the same snapshot: the door log is filled only for viewers
 * holding {@link TabletFeature#DOOR_LOG} and is delivered by the periodic sync and on tablet open.
 * 身份授予的功能随同一快照下发：房门记录只为拥有房门监控的观看者填充，并通过周期同步与打开平板时下发。</p>
 *
 * <p>Drone rows are filled only for viewers holding {@link TabletFeature#DRONE} and list only the viewer's own drones;
 * besides the periodic sync, {@code DronePilotService} pushes a snapshot when a pilot session starts or ends.
 * 无人机行只为拥有无人机功能的观看者填充，且只列出查看者自己的无人机；除周期同步外，DronePilotService 在驾驶会话开始或结束时推送快照。</p>
 *
 * <p>The server remembers the last snapshot each client holds: periodic and fan-out syncs skip a viewer whose
 * snapshot is unchanged, while direct single-player syncs ({@link #syncTo}, {@link #syncPair}, a client request) always
 * send. The memory is dropped on join, disconnect, world change, server stop, round clear and client request, so a
 * client that may have discarded its copy is always answered.
 * 服务端记住每个客户端当前持有的最近一份快照：周期同步与群发同步跳过快照未变化的观看者，而直接的单人同步（syncTo、syncPair、
 * 客户端请求）总会发送。加入、断线、切换世界、服务器停止、清局与客户端请求时清除该记录，因此可能已丢弃副本的客户端总能得到回应。</p>
 */
public final class TabletStateService {
    // What each client currently holds (server thread only). / 每个客户端当前持有的快照（仅服务端线程）。
    private static final Map<UUID, TabletSnapshot> SENT_SNAPSHOTS = new HashMap<>();
    private static boolean registered;

    private TabletStateService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, taskType) -> onTaskComplete(player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> forgetSentSnapshot(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetSentSnapshot(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> forgetSentSnapshot(player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT_SNAPSHOTS.clear());
    }

    /**
     * Wathe server-side task hook (REAL and FAKE mood roles alike): counts the task for any player with a round role.
     * The completion that crosses {@link TabletIdentityRules#POLICE_REVEAL_TASKS} tells a police-network player that
     * member identities are unlocked and resyncs their tablet; nobody else learns anything.
     * Wathe 服务端任务钩子（真实与虚假心情身份都会触发）：为任何拥有局内身份的玩家计数。跨过解锁门槛的那一次完成会通知
     * 义警网络玩家成员身份已解锁并重新同步其平板；其他人不会得到任何信息。
     */
    public static void onTaskComplete(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (!GameWorldComponent.KEY.get(world).hasAnyRole(player.getUuid())) {
            return;
        }
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        int previous = tablet.completedTasks(player.getUuid());
        int done = tablet.recordCompletedTask(player.getUuid());
        // Identity channels, not the viewed one: an Impostor viewing the killer channel still unlocks police identities.
        // 按身份频道而非当前查看的频道判断：正在查看杀手频道的内鬼同样会解锁义警身份。
        if (!TabletIdentityRules.crossesRevealThreshold(previous, done)
                || !TabletChannelResolver.identityChannels(player).contains(TabletChannel.POLICE)) {
            return;
        }
        player.sendMessage(Text.translatable(
                "message.sparkstrength.tablet.police_identities_unlocked",
                TabletIdentityRules.POLICE_REVEAL_TASKS
        ), false);
        if (TabletAccess.hasTabletInHotbar(player)) {
            syncTo(player);
        }
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
     * Full sync pass: resolve every holder once, send each a changed snapshot, then revoke viewers that lost the tablet.
     * 完整同步轮次：每个持有者只解析一次，快照有变化才发送，随后撤销已失去平板的观看者。
     */
    public static void syncToTabletHolders(ServerWorld world) {
        syncAll(prepare(world));
    }

    /**
     * Client request (the tablet screen opened); throttled per player. A throttled request is still answered by the
     * next periodic sync, because the remembered snapshot is dropped either way.
     * 客户端请求（打开平板界面）；按玩家限流。被限流的请求仍会由下一次周期同步回应，因为无论如何都会清除已记录的快照。
     */
    public static void requestSnapshot(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        forgetSentSnapshot(player);
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
            // Throttled: the snapshot carrying the new channel is already on its way, so a looping client must not
            // turn each stale packet into a full snapshot reply.
            // 限流：携带新频道的快照已在途中，循环发包的客户端不能把每个过期包都换成一份完整快照回复。
            resyncThrottled(sender);
            return;
        }
        String message = TabletRules.sanitizeChatMessage(rawMessage);
        if (message.isEmpty()) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.chat_empty"), true);
            return;
        }
        // Each message re-snapshots the whole channel (or every holder in range); see TabletRules#acceptAction.
        // 每条消息都会为整个频道（或范围内的每名持有者）重建快照；见 TabletRules#acceptAction。
        if (!acceptAction(sender)) {
            return;
        }
        ServerWorld world = sender.getServerWorld();
        if (channel.isProximity()) {
            sendPublicChat(sender, world, message);
            return;
        }
        TabletWorldComponent.KEY.get(world).addChatMessage(
                channel,
                sender.getUuid(),
                sender.getName().getString(),
                message,
                Instant.now().toEpochMilli()
        );
        SyncPass pass = prepare(world);
        syncChannelMembers(pass, channel);
        for (ServerPlayerEntity member : TabletAccess.channelMembers(pass.holders(), pass.roster(), channel)) {
            if (member != sender) {
                notifyMessage(member, channel);
            }
        }
    }

    /**
     * Public proximity chat: speaking out loud, so voice mutes apply (a T-Virus Pathogen, a Jester in its moment). Each
     * holder in range of the sender ({@link TabletPublicRules}) gets its own copy in its own inbox, with the sender
     * and the static decided by its distance right now; holders out of range never get it, not even later.
     * 公共近距离聊天：等同于开口说话，因此语音禁言同样适用（T病毒病原体、小丑时刻中的小丑）。发送者范围内的每名持有者
     * 各自收到一份副本存入自己的收件箱，发送者是否可见与噪声都由此刻的距离决定；范围外的持有者永远收不到。
     */
    private static void sendPublicChat(ServerPlayerEntity sender, ServerWorld world, String message) {
        if (PathogenVisibility.isMuted(sender) || JesterMomentService.isVoiceBlocked(sender)) {
            sender.sendMessage(Text.translatable("message.sparkstrength.tablet.public_muted"), true);
            return;
        }
        TabletWorldComponent tablet = TabletWorldComponent.KEY.get(world);
        SyncPass pass = prepare(world);
        long now = Instant.now().toEpochMilli();
        String senderName = sender.getName().getString();
        List<ServerPlayerEntity> receivers = new ArrayList<>();
        for (ServerPlayerEntity holder : pass.holders()) {
            TabletChannelResolver.Access access = pass.roster().get(holder.getUuid());
            if (access == null || !access.isMember(TabletChannel.PUBLIC)) {
                continue;
            }
            double distance = holder == sender ? 0.0 : sender.getPos().distanceTo(holder.getPos());
            if (!TabletPublicRules.receives(distance)) {
                continue;
            }
            tablet.deliverPublicMessage(holder.getUuid(), new TabletWorldComponent.PublicMessage(
                    sender.getUuid(),
                    senderName,
                    TabletPublicRules.garble(message, distance, ThreadLocalRandom.current()),
                    TabletPublicRules.signal(distance),
                    now
            ));
            receivers.add(holder);
        }
        for (ServerPlayerEntity receiver : receivers) {
            pushSnapshot(receiver, buildSnapshot(receiver, pass.roster()));
            if (receiver != sender) {
                notifyMessage(receiver, TabletChannel.PUBLIC);
            }
        }
    }

    /** Local buzz + unread screen for one receiver; old clients without the payload are skipped. / 单个接收者的本地振动与未读提示。 */
    private static void notifyMessage(ServerPlayerEntity receiver, TabletChannel channel) {
        if (ServerPlayNetworking.canSend(receiver, TabletChatNotifyS2CPacket.ID)) {
            ServerPlayNetworking.send(receiver, new TabletChatNotifyS2CPacket(channel.wire()));
        }
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
        TabletChannel lastFaction = tablet.lastFactionChannel(player.getUuid());
        TabletChannelRules.SwitchResult result = TabletChannelRules.canSwitch(
                access.allowed(),
                access.selected(),
                target,
                tablet.hasActiveMeeting(),
                cooldownTicks,
                lastFaction
        );
        switch (result) {
            case OK -> {
                // Public <-> faction toggles have no cooldown and each answers with a full snapshot: rate-gated.
                // 公共与阵营频道之间来回切换没有冷却，且每次都回复一份完整快照：受限流闸门约束。
                if (!acceptAction(player)) {
                    return;
                }
                TabletChannel selected = Objects.requireNonNull(target);
                // Only a hop to another faction network starts the cooldown; the public channel is free to enter/leave.
                // 只有换到另一个阵营网络才开始冷却；进出公共频道不受限制。
                boolean factionHop = TabletChannelRules.isFactionHop(access.selected(), selected, lastFaction);
                tablet.setLastFactionChannel(player.getUuid(), access.selected());
                tablet.setLastFactionChannel(player.getUuid(), selected);
                tablet.setSelectedChannel(player.getUuid(), selected);
                if (factionHop) {
                    tablet.recordChannelSwitch(player.getUuid(), world.getTime());
                }
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
        tablet.startMeeting(caller.getUuid());
        // Police members chatting on the public channel (with police underneath) are pulled back for the meeting, as
        // before the public channel existed; an Impostor tuned to the killer network stays where it is.
        // 正在公共频道聊天（其下为义警视图）的义警成员会被拉回义警频道参加会议，与公共频道出现前一致；接入杀手网络的内鬼保持不动。
        for (Map.Entry<UUID, TabletChannelResolver.Access> entry : prepare(world).roster().entrySet()) {
            TabletChannelResolver.Access holder = entry.getValue();
            if (holder.isPoliceElector()
                    && holder.selected() == TabletChannel.PUBLIC
                    && factionViewUnderPublic(holder, tablet.lastFactionChannel(entry.getKey())) == TabletChannel.POLICE) {
                tablet.setSelectedChannel(entry.getKey(), TabletChannel.POLICE);
            }
        }
        SyncPass pass = prepare(world);
        broadcastToChannelMembers(pass, TabletChannel.POLICE, Text.translatable("message.sparkstrength.tablet.meeting_started"));
        syncChannelMembers(pass, TabletChannel.POLICE);
        AchievementRecords.tabletMeeting(caller);
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
        if (meeting.hasBallot(voter.getUuid(), targetUuid)) {
            // Same ballot again (a double click): same reply, but nothing changed, so no police-wide rebuild.
            // 重复同一选票（双击）：照常回复，但没有任何变化，因此不重建全体义警的快照。
            voter.sendMessage(Text.translatable("message.sparkstrength.tablet.vote_recorded"), true);
            return;
        }
        if (!acceptAction(voter)) {
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
        if (meeting.isConfirmed(voter.getUuid())) {
            // Already confirmed (a double click): same reply, nothing changed, no rebuild; tick() still ends the
            // meeting once every elector has confirmed.
            // 已确认过（双击）：照常回复，没有任何变化，不重建；所有选民确认后 tick() 仍会结束会议。
            voter.sendMessage(Text.translatable("message.sparkstrength.tablet.vote_confirmed"), true);
            return;
        }
        if (!acceptAction(voter)) {
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
            // Stale view (the suspect was just removed and that push is on its way): throttled resync.
            // 过期视图（嫌疑人刚被移除，推送已在途中）：限流重新同步。
            resyncThrottled(voter);
            return;
        }
        // Not skipped when the approval is unchanged: a shrunken electorate can let a repeat reach two thirds.
        // 赞成状态未变也不跳过：选民减少后，重复提交也可能达到三分之二。
        if (!acceptAction(voter)) {
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
        SENT_SNAPSHOTS.clear();
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
        // Underneath the public channel the holder stays tuned to its faction network: member rows and police
        // meeting/suspect data keep coming (the client shows none of those pages there), so suspect outlines do not
        // switch off while chatting nearby. Meeting and suspect ACTIONS still need the police view itself.
        // 在公共频道之下，持有者仍保持接入其阵营网络：成员行与义警会议/嫌疑人数据照常下发（客户端在公共频道不显示这些页面），
        // 因此在附近聊天时嫌疑人描边不会关闭。会议与嫌疑人操作仍需真正处于义警视图。
        TabletChannel factionView = channel != null && channel.isProximity()
                ? factionViewUnderPublic(access, tablet.lastFactionChannel(viewerUuid))
                : channel;
        boolean meetingFeatures = factionView != null && factionView.hasMeetingFeatures();
        Set<UUID> electorate = meetingFeatures ? TabletAccess.policeElectorate(roster) : Set.of();
        Set<UUID> links = tablet.identityLinks(viewerUuid);
        // Dead viewers keep the count they reached while alive. / 死亡查看者沿用存活时达到的任务数。
        int tasksDone = tablet.completedTasks(viewerUuid);

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
                connectionRows(world, roster, factionView, viewerUuid, links, tasksDone),
                chatRows(tablet, channel, viewerUuid, links, tasksDone),
                meetingFeatures ? meetingSnapshot(world, tablet, viewer, electorate) : TabletSnapshot.Meeting.inactive(),
                meetingFeatures ? suspectRows(world, tablet, viewerUuid, electorate) : List.of(),
                access.featureMask(),
                access.hasFeature(TabletFeature.DOOR_LOG) ? DoorLogTabletSnapshots.rows(viewer, tablet) : List.of(),
                TabletIdentityRules.tasksRemaining(channel, tasksDone),
                access.hasFeature(TabletFeature.DRONE) ? DroneTabletSnapshots.rows(viewer) : List.of(),
                access.hasFeature(TabletFeature.SABOTAGE)
                        ? SabotageTabletSnapshots.rows(world, SabotageAccess.trackOf(viewer)) : List.of(),
                access.hasFeature(TabletFeature.SABOTAGE) ? SabotageTabletSnapshots.log(world, viewerUuid, links) : List.of(),
                TabletChannel.mask(TabletChannelRules.cooldownBlocked(
                        access.allowed(),
                        channel,
                        tablet.lastFactionChannel(viewerUuid),
                        tablet.channelSwitchCooldownTicks(viewerUuid, now)
                ))
        );
    }

    /**
     * The faction network a public-channel viewer stays tuned to: the one it last viewed, else its first faction
     * network; null for a holder with none (a civilian).
     * 公共频道查看者保持接入的阵营网络：上次查看的那个，否则为其第一个阵营网络；没有阵营网络的持有者（平民）为 null。
     */
    private static @Nullable TabletChannel factionViewUnderPublic(
            TabletChannelResolver.Access access,
            @Nullable TabletChannel lastFaction
    ) {
        if (lastFaction != null && access.isMember(lastFaction)) {
            return lastFaction;
        }
        for (TabletChannel candidate : TabletChannel.values()) {
            if (candidate.isFaction() && access.isMember(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<TabletSnapshot.ChatRow> chatRows(
            TabletWorldComponent tablet,
            @Nullable TabletChannel channel,
            UUID viewerUuid,
            Set<UUID> links,
            int tasksDone
    ) {
        if (channel == null) {
            return List.of();
        }
        if (channel.isProximity()) {
            // The viewer's own inbox: each copy already carries its sender visibility and static.
            // 查看者自己的收件箱：每份副本已带有发送者可见性与噪声。
            return tablet.publicInbox(viewerUuid).stream()
                    .map(message -> message.revealsSender()
                            ? new TabletSnapshot.ChatRow(message.senderUuid(), message.senderName(), message.message(),
                                    message.signal())
                            : TabletSnapshot.ChatRow.hidden(message.message(), message.signal()))
                    .toList();
        }
        // Stored history keeps real senders, so earlier messages reveal once a link forms or tasks unlock.
        // 存储的历史保留真实发送者，因此互认或任务解锁后之前的消息也会显示名字。
        return tablet.chatHistory(channel).stream()
                .map(message -> TabletIdentityRules.revealsIdentity(
                        channel, viewerUuid, message.senderUuid(), links, tasksDone)
                        ? new TabletSnapshot.ChatRow(message.senderUuid(), message.senderName(), message.message())
                        : TabletSnapshot.ChatRow.hidden(message.message()))
                .toList();
    }

    private static List<TabletSnapshot.PlayerRow> connectionRows(
            ServerWorld world,
            Map<UUID, TabletChannelResolver.Access> roster,
            @Nullable TabletChannel channel,
            UUID viewerUuid,
            Set<UUID> links,
            int tasksDone
    ) {
        if (channel == null || channel.isProximity()) {
            // No member list on the public channel: listing nearby holders would be a radar through walls.
            // 公共频道没有成员列表：列出附近的持有者等同于穿墙雷达。
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
                // Hidden members (unlinked, or police before the task unlock) are dropped before any player lookup,
                // so their UUID/name never reach the row; a locked police viewer is left with only their own row.
                // 被隐藏的成员（未互认，或任务解锁前的义警）在查找玩家之前就被剔除，其 UUID/名字不会进入行数据；
                // 未解锁的义警查看者只剩自己这一行。
                .filter(entry -> TabletIdentityRules.revealsIdentity(channel, viewerUuid, entry.getKey(), links, tasksDone))
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
        UUID caller = meeting.caller();
        tablet.clearMeeting();
        tablet.startMeetingCooldown(world.getTime());
        if (winner.isPresent()) {
            boolean added = tablet.addSuspect(winner.get());
            broadcastToChannelMembers(pass, TabletChannel.POLICE, Text.translatable(
                    "message.sparkstrength.tablet.suspect_added",
                    playerName(world, winner.get())
            ));
            if (added) {
                AchievementRecords.tabletSuspect(world, winner.get(), caller);
            }
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
            pushSnapshot(holder, buildSnapshot(holder, pass.roster()));
        }
        revokeStaleViewers(pass);
    }

    private static void syncChannelMembers(SyncPass pass, TabletChannel channel) {
        for (ServerPlayerEntity member : TabletAccess.channelMembers(pass.holders(), pass.roster(), channel)) {
            pushSnapshot(member, buildSnapshot(member, pass.roster()));
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

    /**
     * Security guard: per-player rate gate ({@link TabletRules#acceptAction}) for C2S packets that change tablet state
     * and fan snapshots out. Charged only once every validation (and, for votes and confirms, the repeat check) has
     * passed, so refused packets and repeated ballots cost nothing; an overdrawn packet is dropped silently (nothing
     * changed, so the client's view is still current).
     * 安全闸门：对改变平板状态并群发快照的 C2S 数据包按玩家限流（TabletRules#acceptAction）。只在所有校验（投票与确认还包括
     * 重复检查）通过后才扣除，被拒绝的数据包与重复的选票不消耗预算；预算透支的数据包被静默丢弃（状态未变，客户端视图仍是最新的）。
     */
    private static boolean acceptAction(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        return TabletWorldComponent.KEY.get(world).tryAcceptAction(player.getUuid(), world.getTime());
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
     * Fan-out send: skipped when the client already holds an equal snapshot (records compare deeply).
     * 群发：客户端已持有相同快照时跳过（record 逐层比较）。
     */
    private static void pushSnapshot(ServerPlayerEntity player, TabletSnapshot snapshot) {
        if (snapshot.equals(SENT_SNAPSHOTS.get(player.getUuid()))) {
            TabletWorldComponent.KEY.get(player.getServerWorld()).markSyncedViewer(player.getUuid());
            return;
        }
        sendSnapshot(player, snapshot);
    }

    private static void forgetSentSnapshot(@Nullable ServerPlayerEntity player) {
        if (player != null) {
            SENT_SNAPSHOTS.remove(player.getUuid());
        }
    }

    /**
     * Clients without the current (v9) payload are skipped instead of being disconnected by unknown bytes. Every
     * snapshot that goes out, empty ones included, becomes what that client holds.
     * 未注册当前（v9）负载的客户端直接跳过，避免因未知字节断线。每份发出的快照（包括空快照）都记为该客户端当前持有的快照。
     */
    private static boolean sendPacket(ServerPlayerEntity player, TabletSnapshot snapshot) {
        if (!ServerPlayNetworking.canSend(player, SyncTabletSnapshotS2CPacket.ID)) {
            return false;
        }
        ServerPlayNetworking.send(player, new SyncTabletSnapshotS2CPacket(snapshot));
        SENT_SNAPSHOTS.put(player.getUuid(), snapshot);
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
