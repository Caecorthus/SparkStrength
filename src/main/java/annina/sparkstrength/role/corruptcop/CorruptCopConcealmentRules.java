package annina.sparkstrength.role.corruptcop;

/**
 * Pure close-range instinct concealment rules for a Corrupt Cop whose "Arrogant ASF" toggle is on.
 * The concealment is pairwise: it only applies between the cop and another player, never between two
 * bystanders who both stand near the cop.
 * 黑警开启“展示豪度”时的近距离本能屏蔽纯规则。屏蔽只作用于黑警与他人之间，不影响同在圈内的其他两人。
 */
public final class CorruptCopConcealmentRules {
    public static final double RADIUS = 8.0D;
    public static final double RADIUS_SQUARED = RADIUS * RADIUS;

    private CorruptCopConcealmentRules() {
    }

    /**
     * Whether the local viewer's instinct highlight of the target must be vetoed.
     * Final Moment, spectators and non-playing targets are exempt; an Insider and a concealing cop keep seeing
     * each other; a concealing cop in its Moment regains sight of nearby players, but they still cannot see the cop.
     * 是否必须否决本地观察者对目标的本能高亮。终局时刻、旁观者与非对局目标不受影响；内应与黑警仍互相可见；
     * 黑警时刻中的黑警重新看得见附近玩家，但附近玩家仍看不见黑警。
     */
    public static boolean shouldConceal(
            boolean finalMomentActive,
            boolean viewerPlayingAndAlive,
            boolean viewerSpectatingOrCreative,
            boolean samePlayer,
            boolean targetPlayingAndAlive,
            double distanceSquared,
            boolean viewerConcealingCop,
            boolean viewerMomentActive,
            boolean viewerInsider,
            boolean targetConcealingCop,
            boolean targetInsider
    ) {
        if ((!viewerConcealingCop && !targetConcealingCop)
                || finalMomentActive
                || !viewerPlayingAndAlive
                || viewerSpectatingOrCreative
                || samePlayer
                || !targetPlayingAndAlive
                || !(distanceSquared <= RADIUS_SQUARED)) {
            return false;
        }

        boolean viewerBlinded = viewerConcealingCop && !viewerMomentActive && !targetInsider;
        boolean targetHidden = targetConcealingCop && !viewerInsider;
        return viewerBlinded || targetHidden;
    }
}
