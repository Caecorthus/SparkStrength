package annina.sparkstrength.role.partyanimal;

import java.util.List;

/**
 * Pure rules for the Party Balloon (派对气球), owner rules 2026-10-21:
 * 派对气球的纯规则，主人 2026-10-21 定下的规则：
 * <ul>
 *   <li>25 coins in the Party Animal's shop, right after Wathe's firecracker; stacks to 16; hidden while held.
 *   / 派对狂商店 25 金币，紧跟在 Wathe 鞭炮之后；最多叠 16 个；手持时别人看不见。</li>
 *   <li>Right-click a player within 3 blocks (eye to hitbox) in sight: the balloon is consumed and the target is
 *   marked for the rest of the round, without being told. / 右键 3 格内（眼睛到碰撞箱）且在视线中的玩家：消耗一个气球，
 *   目标本局被标记，本人不会得知。</li>
 *   <li>Any faction, killer teammates and Undercovers at any stage included (owner 2026-10-25), and a balloon makes
 *   anyone Partygoer prey ({@link PartyAnimalRules#isValidPrey}). / 任何阵营都能给，包括杀手队友与任何阶段的卧底
 *   （主人 2026-10-25）；被塞气球的人一律是派对客的猎物（见 PartyAnimalRules#isValidPrey）。</li>
 *   <li>A refusal keeps the balloon. / 被拒绝时不消耗气球。</li>
 *   <li>The Party Animal sees marked players in an always-on yellow outline. / 派对狂常驻看到被标记玩家的黄色描边。</li>
 * </ul>
 */
public final class PartyBalloonRules {
    public static final String ENTRY_ID = "sparkstrength_party_balloon";
    /** Wathe's killer-shop firecracker; the balloon goes right after it. / Wathe 杀手商店的鞭炮；气球紧随其后。 */
    public static final String AFTER_ENTRY_ID = "firecracker";
    public static final int PRICE = 25;
    public static final int MAX_STACK = 16;
    /** Same reach as NoellesRoles' helium mark ({@code squaredDistanceTo <= 9.0}). / 与 NoellesRoles 氦气标记距离一致。 */
    public static final double RANGE = 3.0D;

    public static final int HIGHLIGHT_COLOR = 0xFFD84A;
    /**
     * Above NoellesRoles' helium pink (its Party Animal branch answers {@code always(color)}, priority 0), so the
     * yellow wins on a player who is both. Below SparkWitch's Black Raven teammate colour (+50), every explicit
     * {@code skip()} (+100, e.g. NoellesRoles' Jester Moment) and the Professor's invisibility skip (300).
     * 高于 NoellesRoles 氦气粉色（其派对狂分支返回 {@code always(color)}，优先级 0），同时受两者影响的玩家显示黄色。
     * 低于 SparkWitch 黑羽鸦队友色（+50）、所有显式 {@code skip()}（+100，如 NoellesRoles 小丑时刻）与教授隐身跳过（300）。
     */
    public static final int HIGHLIGHT_PRIORITY = 45;

    public enum Verdict {
        OK,
        UNAVAILABLE,
        ALREADY_MARKED,
        OUT_OF_RANGE
    }

    private PartyBalloonRules() {
    }

    /**
     * Giving a balloon. The user must be the living Party Animal in an active round, not swallowed; the target a living
     * participant, not swallowed, allowed by SparkFactionAPI. Then: already marked, reach and sight. The target's
     * faction never matters.
     * 塞气球。使用者须为进行中对局里存活且未被吞下的派对狂；目标须为存活参与者、未被吞下且 SparkFactionAPI 允许。
     * 之后依次检查：已被标记、距离与视线。目标阵营不影响结果。
     */
    public static Verdict verdict(boolean userIsLivingPartyAnimal, boolean userSwallowed,
                                  boolean targetIsLivingParticipant, boolean targetSwallowed, boolean factionAllows,
                                  boolean targetMarked, double eyeToHitboxSquared, boolean lineOfSight) {
        if (!userIsLivingPartyAnimal || userSwallowed || !targetIsLivingParticipant || targetSwallowed
                || !factionAllows) {
            return Verdict.UNAVAILABLE;
        }
        if (targetMarked) {
            return Verdict.ALREADY_MARKED;
        }
        if (eyeToHitboxSquared > RANGE * RANGE || !lineOfSight) {
            return Verdict.OUT_OF_RANGE;
        }
        return Verdict.OK;
    }

    /**
     * Shop slot: right after the first firecracker, or at the end when another provider removed it.
     * 商店位置：紧跟第一个鞭炮之后；若鞭炮已被其他提供方移除则放在末尾。
     */
    public static int shopInsertIndex(List<String> entryIds) {
        int firecracker = entryIds.indexOf(AFTER_ENTRY_ID);
        return firecracker >= 0 ? firecracker + 1 : entryIds.size();
    }

    /**
     * The yellow outline on the Party Animal's client: a living Party Animal, a living marked target, and not a Survival
     * Master hidden behind an obstacle (NoellesRoles' {@code hiddenSurvivalMaster}, also honoured by its helium pink).
     * 派对狂客户端上的黄色描边：存活的派对狂、存活且被标记的目标，且目标不是被障碍物遮挡的生存大师（NoellesRoles 的
     * {@code hiddenSurvivalMaster}，其氦气粉色同样遵守）。
     */
    public static boolean showsHighlight(boolean viewerIsLivingPartyAnimal, boolean targetAlive, boolean targetMarked,
                                         boolean hiddenSurvivalMaster) {
        return viewerIsLivingPartyAnimal && targetAlive && targetMarked && !hiddenSurvivalMaster;
    }
}
