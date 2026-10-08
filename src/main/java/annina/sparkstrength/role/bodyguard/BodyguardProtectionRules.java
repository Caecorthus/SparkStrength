package annina.sparkstrength.role.bodyguard;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
    private static final Map<Identifier, Protection> TABLE = Map.ofEntries(
            Map.entry(Identifier.of("wathe", "knife_stab"), new Protection(true, BLOCK, Source.MELEE)),
            Map.entry(Identifier.of("wathe", "gun_shot"), PHYSICAL),
            Map.entry(Identifier.of("wathe", "bat_hit"), new Protection(true, BLOCK, Source.MELEE)),
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
            Map.entry(Identifier.of("sparkwitch", "mighty_force"), PHYSICAL)
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
