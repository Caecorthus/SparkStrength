package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Pure identity-link rules for anonymous tablet channels (currently KILLER).
 * 匿名平板频道（目前为杀手频道）的纯身份互认规则。
 *
 * <p>No-oracle invariant: the initiator's feedback must be identical for every gesture that does not complete a link
 * (ineligible target, fresh request, still-pending request), so Shift+right-click can never be used to detect channel
 * membership. Only {@link Decision#COMPLETE} and {@link Decision#ALREADY_LINKED} reveal anything, and both require a
 * mutual gesture or an existing link.
 * 无预言机不变量：对任何未完成互认的操作（目标不合格、新请求、请求仍在等待），发起者得到的反馈必须完全一致，
 * 因此 Shift+右键 永远不能用来探测频道成员身份。只有 COMPLETE 与 ALREADY_LINKED 会透露信息，且二者都需要
 * 双向操作或已存在的互认。</p>
 */
public final class TabletLinkRules {
    /** 5 s for the target to answer. / 目标回应的 5 秒窗口。 */
    public static final int LINK_WINDOW_TICKS = 100;
    /** Held use re-fires every 4 ticks; 2 ticks of slack for packet jitter. / 按住使用键每 4 tick 重复触发；另留 2 tick 容忍网络抖动。 */
    public static final int HELD_REPEAT_TICKS = 6;

    private TabletLinkRules() {
    }

    public static boolean hasAnonymousChannel(@Nullable Set<TabletChannel> channels) {
        if (channels == null) {
            return false;
        }
        for (TabletChannel channel : channels) {
            if (channel != null && channel.anonymousSenders()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Both sides must share at least one anonymous channel; a shared real-name channel never qualifies.
     * 双方必须至少共享一个匿名频道；仅共享实名频道不满足条件。
     */
    public static boolean canLink(@Nullable Set<TabletChannel> actor, @Nullable Set<TabletChannel> target) {
        if (actor == null || target == null) {
            return false;
        }
        for (TabletChannel channel : actor) {
            if (channel != null && channel.anonymousSenders() && target.contains(channel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code viewer} may see {@code subject}'s identity in the {@code viewed} channel. Links are pairwise and
     * never transitive: only the viewer's own link set is consulted.
     * 查看者能否在该频道看到对象身份。互认是两两之间的，不可传递：只查询查看者自己的互认集合。
     */
    public static boolean revealsIdentity(
            @Nullable TabletChannel viewed,
            UUID viewer,
            @Nullable UUID subject,
            Set<UUID> viewerLinks
    ) {
        if (viewed == null || !viewed.anonymousSenders()) {
            return true;
        }
        return subject != null && (subject.equals(viewer) || viewerLinks != null && viewerLinks.contains(subject));
    }

    /**
     * Whether a gesture on {@code target} at {@code now} is the held-key repeat of the previous one. It depends only on
     * the actor's own input timing, so suppressing feedback for repeats reveals nothing about the target.
     * 本次对目标的操作是否为按住使用键产生的重复触发。只依赖发起者自身的输入时序，因此对重复操作不再提示不会泄露目标信息。
     */
    public static boolean isHeldRepeat(@Nullable UUID previousTarget, long previousTick, @Nullable UUID target, long now) {
        return previousTarget != null && previousTarget.equals(target)
                && now >= previousTick && now - previousTick <= HELD_REPEAT_TICKS;
    }

    public static boolean isLive(long expiryTick, long now) {
        return now < expiryTick;
    }

    public static int remainingSeconds(long expiryTick, long now) {
        long ticks = expiryTick - now;
        return ticks <= 0 ? 0 : (int) Math.ceil(ticks / 20.0);
    }

    public enum Decision {
        ALREADY_LINKED,
        COMPLETE,
        IGNORE_PENDING,
        REQUEST
    }

    /**
     * Outcome of actor Shift+right-clicking target. Completion needs the target's live request to the actor AND the
     * target still eligible; an ineligible reverse request falls through so it cannot be distinguished.
     * 发起者 Shift+右键 目标的结果。完成互认需要目标对发起者的请求仍有效且目标仍合格；不合格的反向请求会继续向下
     * 判定，使其无法被区分。
     *
     * @param linked         actor and target are already linked / 双方已互认
     * @param reverseLive    target → actor request is live / 目标对发起者的请求仍有效
     * @param targetEligible target is an eligible link partner / 目标是合格的互认对象
     * @param forwardLive    actor → target request is live / 发起者对目标的请求仍有效
     */
    public static Decision decide(boolean linked, boolean reverseLive, boolean targetEligible, boolean forwardLive) {
        if (linked) {
            return Decision.ALREADY_LINKED;
        }
        if (reverseLive && targetEligible) {
            return Decision.COMPLETE;
        }
        if (forwardLive) {
            return Decision.IGNORE_PENDING;
        }
        return Decision.REQUEST;
    }
}
