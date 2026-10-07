package annina.sparkstrength.role.pathogen;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Pure rules for the Pathogen buff (owner decisions 2026-10-06): task money, the Virus / T-Virus shop, carriers,
 * the antidote cure, revives and outline colours. Inputs in, values out, no world access.
 * 病原体增强的纯规则（所有者 2026-10-06 决定）：任务金币、病毒 / T病毒商店、带毒者、解毒剂治疗、复活与描边颜色。
 * 只接收输入、返回结果，不访问世界。
 */
public final class PathogenRules {
    public static final Identifier PATHOGEN_ID = Identifier.of("noellesroles", "pathogen");

    /** +50 coins per completed task. / 每完成一个任务 +50 金币。 */
    public static final int TASK_MONEY_REWARD = 50;

    public static final String VIRUS_ENTRY_ID = "sparkstrength_virus";
    public static final String T_VIRUS_ENTRY_ID = "sparkstrength_t_virus";
    public static final int VIRUS_PRICE = 50;
    public static final int T_VIRUS_PRICE = 150;
    /** Q4: one T-Virus per round. / Q4：每局限购 1 支 T病毒。 */
    public static final int T_VIRUS_STOCK = 1;

    /** Virus reach: eye to the target's hitbox, like vanilla's 3-block entity reach. / 病毒距离：眼睛到目标碰撞箱，同原版 3 格实体交互距离。 */
    public static final double VIRUS_RANGE = 3.0;
    /** Carrier spread radius, centre to centre like NoellesRoles' own infection. / 带毒者传播半径，与 NoellesRoles 感染一样按中心距离。 */
    public static final double SPREAD_RANGE = 3.0;
    public static final int SPREAD_INTERVAL_TICKS = 30 * 20;
    /** Carriers re-sync this often so a Toxicologist or Pathogen who appears mid-round learns the state. / 带毒状态的重同步间隔。 */
    public static final int CARRIER_RESYNC_INTERVAL_TICKS = 40;

    /** NoellesRoles' own sneeze: {@code ENTITY_PANDA_SNEEZE} at volume 2.0, pitch 0.8. / 与 NoellesRoles 相同的喷嚏音效参数。 */
    public static final float SNEEZE_VOLUME = 2.0F;
    public static final float SNEEZE_PITCH = 0.8F;

    /** Q10 colours. / Q10 颜色。 */
    public static final int CARRIER_COLOR_FOR_PATHOGEN = 0x1E8C1E;
    public static final int TEAMMATE_COLOR = 0x00E5A0;
    public static final int CARRIER_COLOR_FOR_TOXICOLOGIST = 0x3CC850;
    public static final int POISONED_CARRIER_COLOR = 0x9B4DFF;

    private PathogenRules() {
    }

    public static boolean isPathogen(@Nullable Role role) {
        return role != null && PATHOGEN_ID.equals(role.identifier());
    }

    /**
     * Task pay: an active, living, participating Pathogen that is neither spectating nor in creative.
     * 任务收入：处于 ACTIVE、存活且参与对局、非旁观/创造的病原体。
     */
    public static boolean earnsTaskMoney(boolean active, boolean playingAndAlive, @Nullable Role role,
                                         boolean spectator, boolean creative) {
        return active && playingAndAlive && isPathogen(role) && !spectator && !creative;
    }

    /**
     * Money visibility for the exact Pathogen role: {@code TRUE} (ALLOW) while not dead, {@code FALSE} (DENY) once
     * dead, {@code null} (no answer) for every other role. Deliberately no running-state check: SparkTraits offers Task
     * Master to FAKE-mood roles during STARTING only when this answers ALLOW, and a dead Pathogen must not keep the
     * counter through Wathe's {@code canAccessShop} fallback.
     * 精确病原体身份的金币可见性：未死亡为 {@code TRUE}（ALLOW），死亡后为 {@code FALSE}（DENY），其他身份为 {@code null}
     * （不作答）。刻意不检查对局运行状态：SparkTraits 只在此处于 STARTING 阶段答 ALLOW 时才为假理智职业提供任务大师；
     * 死亡的病原体也不能经由 Wathe 的 {@code canAccessShop} 兜底继续看到金币。
     */
    public static @Nullable Boolean moneyVisible(@Nullable Role role, boolean dead) {
        if (!isPathogen(role)) {
            return null;
        }
        return !dead;
    }

    /** Shop order: Virus, then T-Virus for an original (not converted) Pathogen. / 商店顺序：病毒；原生病原体再加 T病毒。 */
    public static List<String> shopEntryIds(boolean converted) {
        return converted ? List.of(VIRUS_ENTRY_ID) : List.of(VIRUS_ENTRY_ID, T_VIRUS_ENTRY_ID);
    }

    /**
     * Hard cap behind {@code stock(1)}: stock is cached only for roles held at round start.
     * stock(1) 之外的硬上限：库存只为开局时的身份缓存。
     */
    public static boolean canBuyTVirus(boolean converted, int purchasedThisRound) {
        return !converted && purchasedThisRound < T_VIRUS_STOCK;
    }

    public enum VirusVerdict {
        OK,
        UNAVAILABLE,
        PATHOGEN_TARGET,
        ALREADY_CARRIER,
        OUT_OF_RANGE
    }

    /**
     * Virus use on a player. The user must be a living Pathogen in an active round and not swallowed; the target a
     * living participant. Pathogens and existing carriers are refused (Q9: not consumed). An infected non-carrier is
     * upgraded (Q9). Range is eye-to-hitbox with line of sight.
     * 对玩家使用病毒。使用者须为对局中存活且未被吞下的病原体；目标须为存活参与者。病原体与已带毒者被拒绝（Q9：不消耗）。
     * 已感染但未带毒的玩家可被升级（Q9）。距离按眼睛到碰撞箱计算，并需要视线。
     */
    public static VirusVerdict virusVerdict(boolean userIsLivingPathogen, boolean userSwallowed,
                                            boolean targetIsLivingParticipant, boolean targetSwallowed,
                                            boolean targetIsPathogen, boolean targetIsCarrier,
                                            double eyeToHitboxSquared, boolean lineOfSight,
                                            boolean factionAllows) {
        if (!userIsLivingPathogen || userSwallowed || !targetIsLivingParticipant || targetSwallowed || !factionAllows) {
            return VirusVerdict.UNAVAILABLE;
        }
        if (targetIsPathogen) {
            return VirusVerdict.PATHOGEN_TARGET;
        }
        if (targetIsCarrier) {
            return VirusVerdict.ALREADY_CARRIER;
        }
        if (eyeToHitboxSquared > VIRUS_RANGE * VIRUS_RANGE || !lineOfSight) {
            return VirusVerdict.OUT_OF_RANGE;
        }
        return VirusVerdict.OK;
    }

    /** One player the carrier could infect this check. / 本次判定中带毒者可能感染的一名玩家。 */
    public record SpreadCandidate(UUID id, double distanceSquared, boolean eligible, boolean visible) {
    }

    /**
     * Q2: the nearest eligible (living, uninfected, non-Pathogen, not swallowed) player in line of sight within
     * {@link #SPREAD_RANGE}, strictly closer like NoellesRoles' own infect; {@code null} when none (the check misses).
     * Ties keep the first candidate.
     * Q2：{@link #SPREAD_RANGE} 内、视线中最近的一名合格玩家（存活、未感染、非病原体、未被吞下），与 NoellesRoles 感染一样
     * 使用严格小于；没有时返回 {@code null}（本次落空）。距离相同时保留先出现者。
     */
    public static @Nullable UUID pickSpreadTarget(List<SpreadCandidate> candidates) {
        double best = SPREAD_RANGE * SPREAD_RANGE;
        UUID chosen = null;
        for (SpreadCandidate candidate : candidates) {
            if (!candidate.eligible() || !candidate.visible()) {
                continue;
            }
            if (candidate.distanceSquared() < best) {
                best = candidate.distanceSquared();
                chosen = candidate.id();
            }
        }
        return chosen;
    }

    /** Countdown after one tick; at 0 the carrier checks and restarts at the full interval. / 每刻倒计时；归零时判定并重置。 */
    public static int nextSpreadCountdown(int countdown) {
        return countdown <= 1 ? SPREAD_INTERVAL_TICKS : countdown - 1;
    }

    public static boolean spreadsThisTick(int countdown) {
        return countdown <= 1;
    }

    public enum ReviveVerdict {
        OK,
        UNAVAILABLE,
        INVALID_BODY
    }

    /**
     * T-Virus on a corpse (Q5). The user must be a living Pathogen in an active round. The body must be a real, visible,
     * newest corpse of an online player who is in this round, dead, spectating (an active Wraith is dead but not
     * spectating) and not the user, and no Last Stand may own the death.
     * 对尸体使用 T病毒（Q5）。使用者须为对局中存活的病原体。尸体须为本局在线玩家的真实、可见、最新的尸体；该玩家已死亡、
     * 处于旁观（活跃冤魂虽已死亡但不在旁观）、不是使用者本人，且其死亡未被背水一战接管。
     */
    public static ReviveVerdict reviveVerdict(boolean userIsLivingPathogen, boolean userSwallowed,
                                              boolean ownerOnline, boolean ownerInRound, boolean ownerDead,
                                              boolean ownerSpectating, boolean ownerIsUser, boolean newestBody,
                                              boolean fakeBody, boolean hiddenBody, boolean lastStandOwnsDeath) {
        if (!userIsLivingPathogen || userSwallowed) {
            return ReviveVerdict.UNAVAILABLE;
        }
        if (!ownerOnline || !ownerInRound || !ownerDead || !ownerSpectating || ownerIsUser || !newestBody
                || fakeBody || hiddenBody || lastStandOwnsDeath) {
            return ReviveVerdict.INVALID_BODY;
        }
        return ReviveVerdict.OK;
    }

    /**
     * Colour a living Pathogen sees on a target, or {@code null} for no answer: a fellow Pathogen through walls at any
     * distance; a carrier in dark green with line of sight (plain infected keep NoellesRoles' lime).
     * 存活病原体看到目标的颜色，{@code null} 表示不作答：病原体同伴无视距离穿墙；带毒者需视线、深绿（普通感染者沿用
     * NoellesRoles 的亮绿）。
     */
    public static @Nullable Integer pathogenViewColor(boolean targetIsPathogen, boolean targetIsCarrier,
                                                      boolean lineOfSight) {
        if (targetIsPathogen) {
            return TEAMMATE_COLOR;
        }
        if (targetIsCarrier && lineOfSight) {
            return CARRIER_COLOR_FOR_PATHOGEN;
        }
        return null;
    }

    /**
     * Colour a living Toxicologist sees on a carrier with line of sight: purple when also poisoned, else green;
     * {@code null} otherwise (plain poison keeps NoellesRoles' Toxicologist colour).
     * 存活毒理学家在视线内看到带毒者的颜色：同时中毒为紫色，否则为绿色；其余情况为 {@code null}（单纯中毒沿用
     * NoellesRoles 毒理学家颜色）。
     */
    public static @Nullable Integer toxicologistViewColor(boolean targetIsCarrier, boolean targetPoisoned,
                                                          boolean lineOfSight) {
        if (!targetIsCarrier || !lineOfSight) {
            return null;
        }
        return targetPoisoned ? POISONED_CARRIER_COLOR : CARRIER_COLOR_FOR_TOXICOLOGIST;
    }

    /**
     * 2026-10-07: a living converted Pathogen (revived by a T-Virus) cannot speak, by voice or by text. The original
     * Pathogen is never muted, and a converted one that dies again talks with the dead as usual.
     * 2026-10-07：存活的转化病原体（被 T病毒复活者）不能说话（语音与文字）。原生病原体从不被禁言，转化病原体再次死亡后
     * 照常与死者交流。
     */
    public static boolean isMuted(boolean pathogenRole, boolean converted, boolean playingAndAlive) {
        return pathogenRole && converted && playingAndAlive;
    }

    /**
     * 2026-10-07: a living converted Pathogen sees every other non-Pathogen (players and corpses) as a gray Steve.
     * 2026-10-07：存活的转化病原体眼中，除病原体以外的其他人（玩家与尸体）都显示为灰色史蒂夫。
     */
    public static boolean seesAsGrayCrowd(boolean viewerIsLivingConvertedPathogen, boolean targetIsViewer,
                                          boolean targetIsPathogen) {
        return viewerIsLivingConvertedPathogen && !targetIsViewer && !targetIsPathogen;
    }

    /**
     * One ABGR pixel (NativeImage order) turned gray by luminance; alpha is kept, so the overlay layer stays sparse.
     * 将一个 ABGR 像素（NativeImage 顺序）按亮度转为灰色；保留透明度，外层贴图仍保持镂空。
     */
    public static int grayPixel(int abgr) {
        int alpha = (abgr >>> 24) & 0xFF;
        int blue = (abgr >>> 16) & 0xFF;
        int green = (abgr >>> 8) & 0xFF;
        int red = abgr & 0xFF;
        int luminance = Math.min(255, (int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue));
        return (alpha << 24) | (luminance << 16) | (luminance << 8) | luminance;
    }

    /**
     * The antidote may start on a target that is poisoned or carries the virus (Q3).
     * 解毒剂可以对中毒或带毒的目标使用（Q3）。
     */
    public static boolean antidoteCanTreat(boolean poisoned, boolean carrier) {
        return poisoned || carrier;
    }
}
