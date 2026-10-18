package annina.sparkstrength.sabotage;

/**
 * Who the instinct key may still reveal during a blackout. Pure, free of Minecraft types so local tests compile it
 * alone.
 * 熄灯期间本能键还能透视到谁。纯规则，不依赖 Minecraft 类型，便于本地测试单独编译。
 *
 * <p>In the dark only teammates who already know each other stay visible: killers see only the players they linked
 * with on the tablet, the witch faction (Grand Witch and Accomplices) sees itself, and the Corrupt Cop and the
 * Insider see each other. Civilians and lone neutrals see nobody, so "who is not outlined" never names a killer.
 * Only the key is affected: outlines that show without it (targets, marks, Moments, Final Moment) stay. Dead viewers,
 * spectators and SparkWitch Wraiths are exempt.
 * 黑暗中只有彼此已经认识的队友仍然可见：杀手只能看到在平板上互认过的玩家，魔女阵营（大魔女与共犯）彼此可见，
 * 黑警与内应彼此可见。好人与单独行动的中立谁都看不到，因此无法靠「谁没被描边」反推出杀手。只影响本能键：不用按键
 * 就显示的描边（目标、标记、各类时刻、终局时刻）照常。死亡观察者、旁观者与 SparkWitch 冤魂不受影响。</p>
 */
public final class BlackoutSightRules {
    public enum Team {
        /** Effective killers, Impostor included: tablet links only. / 有效杀手（含内鬼）：只看平板互认对象。 */
        KILLER,
        /** SparkWitch witch faction: Grand Witch and Accomplices. / SparkWitch 魔女阵营：大魔女与共犯。 */
        WITCH,
        /** Corrupt Cop and Insider. / 黑警与内应。 */
        CORRUPT_COP,
        /** Civilians and lone neutrals. / 好人与单独行动的中立。 */
        ALONE
    }

    private BlackoutSightRules() {
    }

    /**
     * Killer wins over everything (an Impostor civilian is a killer); a target's traits are not visible on other
     * clients, so callers pass the target's role alone.
     * 杀手优先（内鬼好人算杀手）；其他客户端看不到目标的词条，因此目标只按身份传入。
     */
    public static Team team(boolean effectiveKiller, boolean corruptCopOrInsider, boolean witchFaction) {
        if (effectiveKiller) {
            return Team.KILLER;
        }
        if (corruptCopOrInsider) {
            return Team.CORRUPT_COP;
        }
        if (witchFaction) {
            return Team.WITCH;
        }
        return Team.ALONE;
    }

    /**
     * Whether a key-gated outline of another player may still show.
     * 另一名玩家的按键描边是否仍可显示。
     *
     * @param blackout     a blackout is on / 正在熄灯
     * @param viewerExempt dead, spectating or an active Wraith / 已死亡、旁观中或为激活中的冤魂
     * @param linked       viewer and target linked identities on the tablet / 双方已在平板上互认
     */
    public static boolean keyMayReveal(boolean blackout, boolean viewerExempt, Team viewer, Team target,
                                       boolean linked) {
        if (!blackout || viewerExempt) {
            return true;
        }
        return switch (viewer) {
            case KILLER -> linked;
            case WITCH, CORRUPT_COP -> target == viewer;
            case ALONE -> false;
        };
    }
}
