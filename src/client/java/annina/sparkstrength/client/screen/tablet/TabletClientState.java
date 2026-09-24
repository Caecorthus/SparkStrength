package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletChannel;

/**
 * Client mirror of the server-authoritative tablet snapshot plus the local chat draft.
 * 服务端权威平板快照的客户端镜像，以及本地聊天草稿。
 *
 * <p>The draft is bound to the channel it was typed for; a snapshot on a different channel drops it so a police
 * draft can never be sent into the killer or witch network (the server also rejects stale channel wires).
 * 草稿绑定到输入时的频道；收到不同频道的快照即丢弃，避免义警草稿被发进杀手或魔女网络（服务端也会拒绝过期频道）。</p>
 */
public final class TabletClientState {
    private static TabletSnapshot snapshot = TabletSnapshot.empty();
    private static String chatDraft = "";
    private static int chatDraftChannelWire = TabletChannel.NO_CHANNEL_WIRE;

    private TabletClientState() {
    }

    public static TabletSnapshot snapshot() {
        return snapshot;
    }

    public static void apply(TabletSnapshot nextSnapshot) {
        snapshot = nextSnapshot == null ? TabletSnapshot.empty() : nextSnapshot;
        int channelWire = snapshot.channelWire();
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
     * Drops every trace of the previous server/session; called on disconnect.
     * 清除上一个服务器/会话的全部状态；在断开连接时调用。
     */
    public static void reset() {
        snapshot = TabletSnapshot.empty();
        chatDraft = "";
        chatDraftChannelWire = TabletChannel.NO_CHANNEL_WIRE;
    }
}
