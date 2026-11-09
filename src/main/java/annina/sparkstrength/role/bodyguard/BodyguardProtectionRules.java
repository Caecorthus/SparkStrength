package annina.sparkstrength.role.bodyguard;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Which kills the Bodyguard's vest and Democracy Shield stop (owner-confirmed table, 2026-10-07). Kills are told apart
 * by death reason; ids from other mods are value-compared here and never passed back to them.
 * 保镖防弹衣与民主盾牌能挡哪些击杀（所有者确认的表，2026-10-07）。按死因区分；其他模组的 id 在这里只做值比较，从不回传给它们。
 */
public final class BodyguardProtectionRules {
    /** Where the shield measures the attack from. / 盾判定攻击方向时取的来源位置。 */
    public enum Source {
        /** The killer's position. / 攻击者的位置。 */
        ATTACKER,
        /** The killer, who must also be within {@link BodyguardRules#MELEE_MAX_DISTANCE}. / 攻击者，且必须在近战距离内。 */
        MELEE,
        /** The blast centre from {@link AttackOriginScope}, else the killer. / 来自作用域的爆炸中心，没有则取攻击者。 */
        BLAST
    }

    /**
     * @param vest         the vest stops it / 防弹衣能挡
     * @param shieldPoints stamina points a shield block costs; 0 when the shield cannot block it / 盾格挡所需体力点数，0 表示盾挡不住
     * @param source       where the shield's front check measures from / 盾前方判定的来源
     */
    public record Protection(boolean vest, int shieldPoints, Source source) {
        public static final Protection NONE = new Protection(false, 0, Source.ATTACKER);

        public boolean shieldBlocks() {
            return shieldPoints > 0;
        }
    }

    private static final int BLOCK = BodyguardRules.SHIELD_BLOCK_POINTS;
    private static final Protection PHYSICAL = new Protection(true, BLOCK, Source.ATTACKER);
    private static final Identifier BOMB = Identifier.of("noellesroles", "bomb");
    private static final Identifier SAVIOR_PISTOL_KILL = Identifier.of("sparkwitch", "savior_pistol_kill");
    private static final Identifier SAVIOR_LIGHTSABER_KILL = Identifier.of("sparkwitch", "lightsaber_kill");
    /**
     * Kill reasons settled by SparkWitch's {@code SaviorRules.settleKill}, which retries {@code killPlayer} up to ten
     * times in one server tick so that each retry spends one protection layer. A shield block of such a hit stops the
     * whole hit: same-tick retries with the same victim, killer and reason are blocked again at no cost
     * ({@link BodyguardSettlementMemo}). Every other reason keeps one block per {@code killPlayer} call.
     * 由 SparkWitch 的 {@code SaviorRules.settleKill} 结算的死因：它在同一服务端刻内最多重试 10 次 {@code killPlayer}，
     * 每次重试消耗一层保护。盾挡下这样的一击就挡下整击：同一刻内受害者、击杀者与死因都相同的重试再次被免费挡下
     * （{@link BodyguardSettlementMemo}）。其他死因仍然每次 {@code killPlayer} 调用各自结算。
     */
    private static final Set<Identifier> SAME_TICK_SETTLEMENTS = Set.of(SAVIOR_PISTOL_KILL, SAVIOR_LIGHTSABER_KILL);
    private static final Map<Identifier, Protection> TABLE = Map.ofEntries(
            Map.entry(Identifier.of("wathe", "knife_stab"), new Protection(true, BLOCK, Source.MELEE)),
            Map.entry(Identifier.of("wathe", "gun_shot"), PHYSICAL),
            Map.entry(Identifier.of("wathe", "bat_hit"), new Protection(true, BLOCK, Source.MELEE)),
            // The stage-2 Undercover's tactical tomahawk: a melee kill like the knife. / 二阶段卧底的战术斧：与刀相同的近战击杀。
            Map.entry(Identifier.of("sparkstrength", "tomahawk"), new Protection(true, BLOCK, Source.MELEE)),
            Map.entry(Identifier.of("wathe", "grenade"), new Protection(false, BLOCK, Source.BLAST)),
            Map.entry(Identifier.of("noellesroles", "throwing_axe"), PHYSICAL),
            Map.entry(Identifier.of("noellesroles", "demon_hunter_shot"), PHYSICAL),
            Map.entry(Identifier.of("sparkwitch", "ceremonial_blade"),
                    new Protection(false, BodyguardRules.SHIELD_CEREMONIAL_SWORD_POINTS, Source.ATTACKER)),
            // Only the TR shell kills with potion_shell. / 只有 TR 弹会以 potion_shell 致死。
            Map.entry(Identifier.of("sparkwitch", "potion_shell"),
                    new Protection(false, BodyguardRules.SHIELD_TR_SHELL_POINTS, Source.ATTACKER)),
            Map.entry(Identifier.of("sparkwitch", "potion_backblast"), new Protection(false, BLOCK, Source.ATTACKER)),
            Map.entry(Identifier.of("sparkwitch", "ninja_knife_kill"), PHYSICAL),
            Map.entry(Identifier.of("sparkwitch", "ninja_shuriken_kill"), PHYSICAL),
            Map.entry(Identifier.of("sparkwitch", "swordfish_stab"), PHYSICAL),
            Map.entry(Identifier.of("sparkwitch", "mighty_force"), PHYSICAL),
            // SparkWitch Witch Maiden crimson wither skull: a gunshot for protections (owner 2026-10-20).
            // SparkWitch 巫女赤色凋灵之首：防护按枪击处理（所有者 2026-10-20）。
            Map.entry(Identifier.of("sparkwitch", "crimson_wither_skull"), PHYSICAL),
            // SparkWitch Savior pistol and lightsaber kills (SparkWitch PR #200): the shield blocks them, the vest does
            // not (owner 2026-11-09, "盾可以挡住它的击杀"). One hit is one settlement per tick, see SAME_TICK_SETTLEMENTS.
            // SparkWitch 救世主手枪与光剑击杀（SparkWitch PR #200）：盾能挡，防弹衣不能（所有者 2026-11-09，
            // “盾可以挡住它的击杀”）。一次命中在同一刻内只结算一次，见 SAME_TICK_SETTLEMENTS。
            Map.entry(SAVIOR_PISTOL_KILL, new Protection(false, BLOCK, Source.ATTACKER)),
            Map.entry(SAVIOR_LIGHTSABER_KILL, new Protection(false, BLOCK, Source.MELEE)),
            // Party Animal's Partygoer: a worn vest is one layer it chews through (one 5 s bite); a raised shield can't
            // stop a meal. / 派对狂的派对客：穿着的防弹衣算一层，被啃掉一口（5 秒）；举盾挡不住被吃。
            Map.entry(Identifier.of("sparkstrength", "partygoer_eaten"), new Protection(true, 0, Source.ATTACKER))
    );
    /** A Bomber drone's blast; the carried bomb has the same reason but no blast scope. / 炸弹客无人机的爆炸；随身炸弹死因相同但没有爆炸作用域。 */
    private static final Protection DRONE_BLAST = new Protection(false, BLOCK, Source.BLAST);

    private BodyguardProtectionRules() {
    }

    /**
     * Stamina points a shield block of this kill costs, given the round of the SparkWitch shield-piercing shot being
     * settled (null when there is none): the AXMC never pierces the shield, but a blocked FMJ round costs
     * {@link BodyguardRules#SHIELD_AXMC_FMJ_POINTS} and an AP round {@link BodyguardRules#SHIELD_AXMC_AP_POINTS}
     * (owner 2026-10-07). Null or any other id keeps the table's cost, so revolvers and every other weapon are unchanged.
     * 本次击杀被盾挡下时消耗的体力点数，取决于正在结算的 SparkWitch 穿盾射击的弹种（没有则为 null）：AXMC 从不击穿盾牌，但 FMJ
     * 子弹消耗 {@link BodyguardRules#SHIELD_AXMC_FMJ_POINTS} 点，AP 子弹消耗 {@link BodyguardRules#SHIELD_AXMC_AP_POINTS} 点
     * （所有者 2026-10-07）。null 或其他 id 保持表中的消耗，因此左轮与其他所有武器不变。
     */
    public static int shieldPoints(Protection protection, @Nullable String piercingRoundId) {
        if (BodyguardRules.AXMC_FMJ_ROUND_ID.equals(piercingRoundId)) {
            return BodyguardRules.SHIELD_AXMC_FMJ_POINTS;
        }
        if (BodyguardRules.AXMC_AP_ROUND_ID.equals(piercingRoundId)) {
            return BodyguardRules.SHIELD_AXMC_AP_POINTS;
        }
        return protection.shieldPoints();
    }

    /**
     * Whether a shield block of this kill also covers same-tick retries of the same hit (see SAME_TICK_SETTLEMENTS).
     * 盾挡下这次击杀后，是否同时覆盖同一刻内对同一击的重试（见 SAME_TICK_SETTLEMENTS）。
     */
    public static boolean settlesOncePerTick(@Nullable Identifier deathReason) {
        return deathReason != null && SAME_TICK_SETTLEMENTS.contains(deathReason);
    }

    /**
     * @param inBlastScope whether the kill runs inside an {@link AttackOriginScope} blast (grenade, M67, drone)
     *                     / 击杀是否发生在爆炸作用域内（手雷、M67、无人机）
     */
    public static Protection forReason(@Nullable Identifier deathReason, boolean inBlastScope) {
        if (deathReason == null) {
            return Protection.NONE;
        }
        if (BOMB.equals(deathReason)) {
            return inBlastScope ? DRONE_BLAST : Protection.NONE;
        }
        return TABLE.getOrDefault(deathReason, Protection.NONE);
    }
}
