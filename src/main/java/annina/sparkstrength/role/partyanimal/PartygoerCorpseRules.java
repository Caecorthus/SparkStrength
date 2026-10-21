package annina.sparkstrength.role.partyanimal;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pure rules for the Partygoer corpse (派对客尸体). Owner rules, 2026-10-21:
 * 派对客尸体的纯规则。主人 2026-10-21 定下的规则：
 * <ul>
 *   <li>Everyone sees it as the Partygoer: its skin, and the name plus death reason when looking at it.
 *   / 所有人看到的都是派对客：派对客皮肤，注视时显示名字与死因。</li>
 *   <li>The Coroner's bag and proximity coins, the Detective's crime scenes and the Morphling's reagent ignore it.
 *   / 验尸官的尸袋与靠近金币、侦探的案发现场、变形者的试剂都忽略它。</li>
 *   <li>A Vulture that eats it is poisoned for 40 s by the summoner; it is removed but never counts toward the win,
 *   gives no speed or highlight, and the 5 s ability cooldown still applies.
 *   / 秃鹫吃掉它会被召唤者下毒 40 秒；尸体被移除但不计入胜利，不给速度与透视，5 秒技能冷却照常。</li>
 * </ul>
 */
public final class PartygoerCorpseRules {
    /** 40 s, like the NoellesRoles poison needle. / 40 秒，与 NoellesRoles 毒针相同。 */
    public static final int VULTURE_POISON_TICKS = 40 * 20;
    /** NoellesRoles' Vulture eat cooldown, GameConstants.getInTicks(0, 5). / NoellesRoles 秃鹫进食冷却。 */
    public static final int VULTURE_COOLDOWN_TICKS = 5 * 20;

    /** Wathe's body hover reach: 8 blocks with spectator information, else 2. / Wathe 尸体注视距离：旁观信息 8 格，否则 2 格。 */
    public static final double LABEL_RANGE = 2.0;
    public static final double LABEL_RANGE_SPECTATOR = 8.0;

    private PartygoerCorpseRules() {
    }

    /**
     * NoellesRoles' Vulture receiver eats {@code found.getFirst()}; the Partygoer path takes over only when that one is a
     * Partygoer corpse.
     * NoellesRoles 秃鹫接收器吃掉 {@code found.getFirst()}；只有它是派对客尸体时才改走派对客分支。
     */
    public static <T> boolean vultureMealIsPartygoer(List<T> found, Predicate<? super T> isPartygoerCorpse) {
        return !found.isEmpty() && isPartygoerCorpse.test(found.getFirst());
    }

    public static double labelRange(boolean spectatorInformation) {
        return spectatorInformation ? LABEL_RANGE_SPECTATOR : LABEL_RANGE;
    }

    /** Whole seconds since death, as Wathe's body HUD counts them. / 死亡至今的整秒数，与 Wathe 尸体 HUD 算法一致。 */
    public static int elapsedSeconds(long worldTime, int deathGameTime) {
        return Math.max(0, (int) worldTime - deathGameTime) / 20;
    }
}
