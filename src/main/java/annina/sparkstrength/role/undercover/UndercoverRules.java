package annina.sparkstrength.role.undercover;

/**
 * Pure rules of the reworked NoellesRoles Undercover (owner 2026-10-19). Stage 1 is the Undercover as assigned; the
 * first time the CQCM mask goes on, stage 2 begins and never ends this round, even if the mask comes off again.
 * 重做后 NoellesRoles 卧底的纯规则（负责人 2026-10-19）。第一阶段即分配到的卧底；第一次戴上 CQCM 面具即进入第二阶段，
 * 本局内永不退出，即使之后摘下面具。
 *
 * <p>Only {@code java.*} may be used here so the standalone test compiles this file with plain javac.
 * 这里只能依赖 java.*，独立测试可以直接用 javac 编译。</p>
 */
public final class UndercoverRules {
    /** CQCM mask in the Undercover's shop. / 卧底商店里的 CQCM 面具。 */
    public static final int MASK_PRICE = 150;
    /** Paid when an Undercover completes a junction box hack (task money is RoleEconomyRules'). / 卧底完成接线箱入侵的奖励。 */
    public static final int HACK_REWARD = 100;

    /** A stage-2 Undercover in view shows in the role's own colour. / 视野内的二阶段卧底显示为职业色。 */
    public static final int HIGHLIGHT_COLOR = 0xC0C0C0;
    /**
     * Thermal's fallback outline where the T-7 heat image can't run (an Iris shader pack, or a failed post chain).
     * 热成像图无法运行时（Iris 光影包，或后处理加载失败）使用的退回描边颜色。
     */
    public static final int THERMAL_COLOR = 0xFFFFFF;
    /** How far the line-of-sight highlight and thermal reach, in blocks. / 视线高亮与热成像的距离（格）。 */
    public static final double SIGHT_RANGE = 48.0;

    /** Tomahawk: attack speed 1 means a full swing charge every second. / 战术斧：攻速 1，即每秒蓄满一次。 */
    public static final double TOMAHAWK_ATTACK_SPEED = 1.0;
    /** Wathe melee kill range (the knife's 3 blocks). / Wathe 近战击杀距离（与刀同为 3 格）。 */
    public static final double TOMAHAWK_RANGE = 3.0;
    public static final int TOMAHAWK_KILL_COOLDOWN_TICKS = 20 * 20;
    /**
     * In a blackout a kill cools down 3 s, and a longer kill cooldown still running when the lights go out is cut to
     * this (owner 2026-10-20). Door breach cooldowns are not affected.
     * 熄灯时击杀只冷却 3 秒；灯灭时仍在跑的更长击杀冷却也会缩到这么长（负责人 2026-10-20）。拆门冷却不受影响。
     */
    public static final int TOMAHAWK_BLACKOUT_KILL_COOLDOWN_TICKS = 3 * 20;
    /** Speed I while the mask is worn in a blackout (owner 2026-10-20). / 熄灯时戴着面具获得速度 I（负责人 2026-10-20）。 */
    public static final int BLACKOUT_SPEED_AMPLIFIER = 0;
    /**
     * Knockback resistance of the mask and of each blackout leather piece: netherite's 0.1 per piece, so mask plus
     * outfit resist 40% like a full netherite set (owner 2026-10-20).
     * 面具与每件熄灯皮衣的击退抗性：与下界合金每件 0.1 相同，面具加整套皮衣共 40%，等同整套下界合金（负责人 2026-10-20）。
     */
    public static final float KNOCKBACK_RESISTANCE_PER_PIECE = 0.1F;

    private UndercoverRules() {
    }

    /** Kill cooldown chosen at the moment of the kill. / 击杀那一刻决定的冷却。 */
    public static int killCooldownTicks(boolean blackout) {
        return blackout ? TOMAHAWK_BLACKOUT_KILL_COOLDOWN_TICKS : TOMAHAWK_KILL_COOLDOWN_TICKS;
    }

    /**
     * Whether a kill cooldown with this many ticks left gets cut to the blackout length. Only the kill cooldown is
     * tracked, so a breach cooldown never reaches this.
     * 剩余这么多 tick 的击杀冷却是否要缩到熄灯时长。只追踪击杀冷却，所以拆门冷却不会走到这里。
     */
    public static boolean blackoutCutsKillCooldown(boolean blackout, long killCooldownTicksLeft) {
        return blackout && killCooldownTicksLeft > TOMAHAWK_BLACKOUT_KILL_COOLDOWN_TICKS;
    }

    /**
     * Speed I needs all three: a living stage-2 Undercover, a blackout, and the mask on the head. Unlike the rest of
     * stage 2 it does not stay when the mask comes off (owner 2026-10-20).
     * 速度 I 三者缺一不可：存活的二阶段卧底、熄灯、面具戴在头上。与第二阶段其他效果不同，摘下面具就没有（负责人 2026-10-20）。
     */
    public static boolean blackoutSpeed(boolean livingStage2, boolean blackout, boolean maskWorn) {
        return livingStage2 && blackout && maskWorn;
    }

    /** Door materials the tomahawk can chop down. / 战术斧能拆的门的材质。 */
    public enum DoorKind {
        /** Wathe small glass door. / Wathe 小玻璃门。 */
        GLASS(5 * 20, 10 * 20),
        /** Wathe small wood door. / Wathe 小木门。 */
        WOOD(10 * 20, 20 * 20),
        /** Wathe's five steel train doors (the owner's "iron door"). / Wathe 五种钢制列车门（负责人说的「铁门」）。 */
        STEEL(30 * 20, 60 * 20);

        private final int breachTicks;
        private final int cooldownTicks;

        DoorKind(int breachTicks, int cooldownTicks) {
            this.breachTicks = breachTicks;
            this.cooldownTicks = cooldownTicks;
        }

        public int breachTicks() {
            return breachTicks;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }
    }

    /** Stage 1 may pry and hack junction boxes; stage 2 has its own track and no longer touches boxes. / 第一阶段可撬可入侵；第二阶段走独立轨道，不再碰接线箱。 */
    public static boolean mayPryOrHack(boolean undercover, boolean stage2) {
        return !undercover || !stage2;
    }

    /** An Undercover may only trigger a sabotage in stage 2. / 卧底只有在第二阶段才能触发破坏。 */
    public static boolean mayTrigger(boolean undercover, boolean stage2) {
        return !undercover || stage2;
    }

    /**
     * Whether a viewer can plainly see a living stage-2 Undercover: not invisible, in range and in line of sight.
     * Only then may anything show on them. / 观察者能否直接看见存活的二阶段卧底：未隐身、在范围内、视线可达。只有此时才可能显示任何东西。
     */
    public static boolean stage2InSight(boolean targetInvisible, double distance, boolean lineOfSight) {
        return !targetInvisible && distance <= SIGHT_RANGE && lineOfSight;
    }

    /**
     * What any viewer's client draws on a living stage-2 Undercover (owner 2026-10-20: no faction sees them through
     * walls, they are highlighted in view):
     * <ul>
     *   <li>out of sight: nothing at all, for every viewer and every ability;</li>
     *   <li>in sight, living effective killers: silver, no instinct key needed;</li>
     *   <li>in sight, anyone else: silver if something would have revealed them ({@code original} is a colour),
     *   nothing if not.</li>
     * </ul>
     * 任何观察者客户端对存活二阶段卧底的描边（负责人 2026-10-20：任何阵营都不能透视，视野内高亮）：
     * 视线外：对所有观察者、所有能力都什么也没有；视线内的存活有效杀手：银色，不用按本能键；
     * 视线内的其他人：原本会被揭示（original 为颜色）则为银色，否则什么也没有。
     *
     * @param original what the viewer would have seen without this rule, -1 for nothing / 没有本规则时观察者会看到的颜色，-1 为无
     * @return the colour, or -1 for none / 颜色，-1 表示无
     */
    public static int viewOfStage2(boolean viewerLivingKiller, boolean inSight, int original) {
        if (!inSight) {
            return -1;
        }
        return viewerLivingKiller || original != -1 ? HIGHLIGHT_COLOR : -1;
    }

    /**
     * Thermal fallback outline (only when the heat image can't run): living players it can see are white, invisible
     * ones included. Anything else keeps whatever it would have had.
     * 热成像退回描边（仅在热图无法运行时）：视线内的活人（含隐身者）显示为白色；其余保持原样。
     *
     * @return the colour, or {@code null} when this rule does not apply / 颜色；null 表示本规则不适用
     */
    public static Integer thermalView(boolean thermalOn, boolean targetLivingPlayer, double distance,
                                      boolean lineOfSight) {
        if (!thermalOn || !targetLivingPlayer) {
            return null;
        }
        return distance <= SIGHT_RANGE && lineOfSight ? THERMAL_COLOR : null;
    }

    /**
     * The Undercover's own sabotage card, read from its personal track: armed the moment stage 2 begins and then
     * cooling down after each of its own sabotages ends. 0 for "not set".
     * 卧底自己的破坏卡片读个人轨道：进入第二阶段那一刻开始待命，此后每次自己的破坏结束后冷却。0 表示未设置。
     */
    public static boolean personalTrackUnlocked(long readyTick) {
        return readyTick > 0L;
    }
}
