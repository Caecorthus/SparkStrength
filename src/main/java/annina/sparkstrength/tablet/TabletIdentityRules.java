package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Pure per-viewer identity rules for every tablet channel; the server applies them before a snapshot is built.
 * 所有平板频道按查看者计算的纯身份可见规则；服务端在构建快照前应用。
 *
 * <ul>
 *   <li>Task-gated channels ({@link TabletChannel#revealsAfterTasks()}, police): the viewer always sees themselves and
 *   sees everyone once they have completed {@link #POLICE_REVEAL_TASKS} tasks this round. /
 *   任务解锁频道（义警）：查看者总能看到自己，本局完成指定数量任务后可看到所有人。</li>
 *   <li>Anonymous channels (killer): exactly {@link TabletLinkRules#revealsIdentity}, pairwise links only. /
 *   匿名频道（杀手）：完全沿用 TabletLinkRules 的两两互认规则。</li>
 *   <li>Every other channel (witch) and no channel: always revealed. / 其他频道（魔女）与无频道：始终显示。</li>
 * </ul>
 *
 * <p>Hidden subjects must be dropped (member rows) or stripped to a sender-less row (chat), never sent with a UUID:
 * a UUID alone resolves to a skin, a tab-list name and an outline on the client.
 * 被隐藏的对象必须被剔除（成员行）或去掉发送者（聊天行），绝不能带 UUID 下发：客户端仅凭 UUID 即可解析出皮肤、
 * 玩家列表名字与描边。</p>
 */
public final class TabletIdentityRules {
    /** Tasks a police-network viewer must complete in the round to see other members. / 义警网络查看者本局需完成的任务数。 */
    public static final int POLICE_REVEAL_TASKS = 2;

    private TabletIdentityRules() {
    }

    /**
     * Whether {@code viewer} may see {@code subject}'s identity in the {@code viewed} channel.
     * 查看者能否在该频道看到对象的身份。
     *
     * @param viewerLinks     the viewer's own identity links (anonymous channels only) / 查看者自己的互认集合（仅匿名频道使用）
     * @param viewerTasksDone tasks the viewer completed this round (task-gated channels only) / 查看者本局已完成的任务数（仅任务解锁频道使用）
     */
    public static boolean revealsIdentity(
            @Nullable TabletChannel viewed,
            UUID viewer,
            @Nullable UUID subject,
            @Nullable Set<UUID> viewerLinks,
            int viewerTasksDone
    ) {
        if (viewed != null && viewed.revealsAfterTasks()) {
            return viewerTasksDone >= POLICE_REVEAL_TASKS || subject != null && subject.equals(viewer);
        }
        return TabletLinkRules.revealsIdentity(viewed, viewer, subject, viewerLinks);
    }

    /**
     * Tasks the viewer still needs before {@code viewed} reveals identities; 0 when unlocked or not task-gated.
     * 查看者在该频道显示身份前仍需完成的任务数；已解锁或该频道不按任务解锁时为 0。
     */
    public static int tasksRemaining(@Nullable TabletChannel viewed, int viewerTasksDone) {
        if (viewed == null || !viewed.revealsAfterTasks()) {
            return 0;
        }
        return Math.max(0, POLICE_REVEAL_TASKS - Math.max(0, viewerTasksDone));
    }

    /**
     * True only for the completion that crosses the threshold, so the unlock notice is sent once per round.
     * 仅在跨过门槛的那一次完成时为真，保证解锁提示每局只发送一次。
     */
    public static boolean crossesRevealThreshold(int previousTasksDone, int tasksDone) {
        return previousTasksDone < POLICE_REVEAL_TASKS && tasksDone >= POLICE_REVEAL_TASKS;
    }
}
