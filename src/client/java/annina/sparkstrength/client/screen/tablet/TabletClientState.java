package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletFeature;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Objects;

/**
 * Client mirror of the server-authoritative tablet snapshot plus the local chat draft.
 * 服务端权威平板快照的客户端镜像，以及本地聊天草稿。
 *
 * <p>The draft is bound to the channel it was typed for; a snapshot on a different channel drops it so a police
 * draft can never be sent into the killer or witch network (the server also rejects stale channel wires).
 * 草稿绑定到输入时的频道；收到不同频道的快照即丢弃，避免义警草稿被发进杀手或魔女网络（服务端也会拒绝过期频道）。</p>
 *
 * <p>Door-log unread state is the highest entry id the player has seen in the tablet screen; it drives only the red
 * dot on the Door Monitor tab (no HUD).
 * 房门记录的未读状态是玩家在平板界面中看过的最大条目 id；只用于房门监控标签上的红点（无 HUD 提示）。</p>
 */
public final class TabletClientState {
    private static TabletSnapshot snapshot = TabletSnapshot.empty();
    private static String chatDraft = "";
    private static int chatDraftChannelWire = TabletChannel.NO_CHANNEL_WIRE;
    private static long chatSeenSignature = 0L;
    private static int lastLiveChannelWire = TabletChannel.NO_CHANNEL_WIRE;
    private static int doorLogSeenId;
    private static long snapshotAppliedAtMs = Util.getMeasuringTimeMs();

    private TabletClientState() {
    }

    public static TabletSnapshot snapshot() {
        return snapshot;
    }

    public static void apply(TabletSnapshot nextSnapshot) {
        snapshot = nextSnapshot == null ? TabletSnapshot.empty() : nextSnapshot;
        snapshotAppliedAtMs = Util.getMeasuringTimeMs();
        int newestDoorLogId = newestDoorLogId(snapshot);
        // Server entry ids only grow within a session (round resets keep counting), so a smaller newest id means a
        // new server session: start over and treat its entries as unread. Empty/revoked snapshots never reset.
        // 服务端条目 id 在一次会话内只增不减（回合重置也继续计数），newest 变小说明服务端会话已更换：重新开始，
        // 其条目均视为未读。空快照或撤销快照不重置。
        if (newestDoorLogId > 0 && newestDoorLogId < doorLogSeenId) {
            doorLogSeenId = 0;
        }
        int channelWire = snapshot.channelWire();
        // A different channel's backlog is history, not unread: re-baseline only when a new live channel appears.
        // Revoked (wire 0) snapshots do not re-baseline, so returning to the same channel keeps unseen messages unread.
        // 切换到新的有效频道时，历史消息不算未读，因此重置已读签名；撤销快照（wire 0）不重置，
        // 回到同一频道时未看过的消息仍保持未读。
        if (channelWire != TabletChannel.NO_CHANNEL_WIRE && channelWire != lastLiveChannelWire) {
            chatSeenSignature = chatSignature(snapshot.chat());
            lastLiveChannelWire = channelWire;
        }
        // A revoked/empty snapshot (wire 0) keeps the draft; only a different live channel invalidates it.
        // 撤销或空快照（wire 0）保留草稿；只有切换到另一个有效频道才作废草稿。
        if (channelWire != TabletChannel.NO_CHANNEL_WIRE && channelWire != chatDraftChannelWire) {
            chatDraft = "";
            chatDraftChannelWire = channelWire;
        }
    }

    public static String chatDraft() {
        return chatDraft;
    }

    public static void setChatDraft(String draft) {
        chatDraft = draft == null ? "" : draft;
        if (snapshot.channelWire() != TabletChannel.NO_CHANNEL_WIRE) {
            chatDraftChannelWire = snapshot.channelWire();
        }
    }

    public static void clearChatDraft() {
        chatDraft = "";
    }

    /**
     * Client-only fingerprint of a chat backlog (size + all message texts; sender-agnostic by design); 0 for an
     * empty list. Hashing the whole backlog keeps it changing at the history cap, where the size stays constant.
     * 仅客户端使用的聊天记录指纹（条数 + 全部消息文本；刻意不含发送者）；空列表为 0。
     * 对整个记录取哈希，保证在条数达到上限不再变化时指纹仍会改变。
     * Identity reveals do not count as unread (they change senders, never message texts).
     * 身份揭示不计为未读（只改变发送者，不改变消息文本）。
     */
    public static long chatSignature(List<TabletSnapshot.ChatRow> chat) {
        if (chat == null || chat.isEmpty()) {
            return 0L;
        }
        int h = 1;
        for (TabletSnapshot.ChatRow row : chat) {
            h = 31 * h + Objects.hashCode(row.message());
        }
        return ((long) chat.size() << 32) ^ (h & 0xFFFFFFFFL);
    }

    public static void markChatSeen(long signature) {
        chatSeenSignature = signature;
    }

    public static boolean hasUnreadChat(TabletSnapshot current) {
        if (current == null || current.chat().isEmpty()) {
            return false;
        }
        return chatSignature(current.chat()) != chatSeenSignature;
    }

    /** Whole seconds since the current snapshot arrived; extrapolates server-computed ages between polls. 当前快照到达后经过的整秒数。 */
    public static int secondsSinceSnapshot() {
        return (int) Math.max(0L, (Util.getMeasuringTimeMs() - snapshotAppliedAtMs) / 1000L);
    }

    public static int newestDoorLogId(TabletSnapshot current) {
        int newest = 0;
        if (current != null && current.hasFeature(TabletFeature.DOOR_LOG)) {
            for (TabletSnapshot.DoorLogRow row : current.doorLog()) {
                // Rows of a kind this client cannot show never raise the dot. 本客户端无法显示的类型不触发红点。
                if (row.kind() != null) {
                    newest = Math.max(newest, row.id());
                }
            }
        }
        return newest;
    }

    public static boolean hasUnreadDoorLog(TabletSnapshot current) {
        return newestDoorLogId(current) > doorLogSeenId;
    }

    /** Called while the Door Monitor section is on screen. 房门监控分区显示期间调用。 */
    public static void markDoorLogSeen(TabletSnapshot current) {
        doorLogSeenId = Math.max(doorLogSeenId, newestDoorLogId(current));
    }

    /**
     * Drops every trace of the previous server/session; called on disconnect.
     * 清除上一个服务器/会话的全部状态；在断开连接时调用。
     */
    public static void reset() {
        snapshot = TabletSnapshot.empty();
        chatDraft = "";
        chatDraftChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        chatSeenSignature = 0L;
        lastLiveChannelWire = TabletChannel.NO_CHANNEL_WIRE;
        doorLogSeenId = 0;
    }
}
