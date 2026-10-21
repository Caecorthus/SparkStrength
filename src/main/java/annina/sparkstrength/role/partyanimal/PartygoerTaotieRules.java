package annina.sparkstrength.role.partyanimal;

import java.util.function.BooleanSupplier;

/**
 * Pure, side-neutral rules for the Taotie swallowing a Partygoer (owner rule 2026-10-21: the Taotie can swallow the
 * Partygoer, then gets Slowness IV and is kicked to death 5 s later, and the Partygoer is let out). Precedent: SparkWitch's
 * Seeker car swallow. The server validation order is fixed here so the service only supplies facts; later checks are
 * asked only after every earlier one passed. The client claim encodes "whatever the vanilla crosshair is on gets
 * swallowed": vanilla's crosshair also hits players, so a Partygoer under it is nearer than any player on that ray, and
 * NoellesRoles (which only ever aims at players) must not swallow someone standing behind it.
 * 饕餮吞派对客的纯规则，两端通用（主人规则 2026-10-21：饕餮可以吞下派对客，随后获得缓慢 IV，5 秒后被一脚踢死，派对客被放出）。
 * 先例：SparkWitch 搜寻者小车被吞。服务端校验顺序固定在此处，服务层只提供事实；前序检查全部通过后才会询问后续检查。客户端认领
 * 编码了“原版准星对着什么就吞什么”：原版准星也会命中玩家，所以准星上的派对客比该射线上的任何玩家都近，NoellesRoles（只瞄准玩家）
 * 不能吞掉站在它身后的人。
 */
public final class PartygoerTaotieRules {
    /**
     * NoellesRoles' client swallow reach, eye to crosshair hit point ({@code crosshairTargetDistance <= 3.0}).
     * NoellesRoles 客户端吞噬距离，从眼睛量到准星命中点。
     */
    public static final double CLIENT_REACH = 3.0;

    /** {@code claimPress}: leave the press to NoellesRoles. / 把按键留给 NoellesRoles。 */
    public static final int NO_CLAIM = -1;
    /** {@code claimPress}: suppress the press but send nothing (out of feet reach). / 压制按键但不发送（超出脚到脚距离）。 */
    public static final int CLAIM_WITHOUT_SWALLOW = -2;

    /** Why a swallow request was refused; every refusal is silent. / 吞噬请求被拒绝的原因；所有拒绝都不提示。 */
    public enum Denial {
        NONE,
        NOT_RUNNING,
        NOT_TAOTIE,
        NOT_ALIVE,
        SWALLOWED,
        CARRIED,
        DAZED,
        STUNNED,
        SKILL_BLOCKED,
        ALREADY_DIGESTING,
        COOLDOWN,
        NO_PARTYGOER,
        OUT_OF_REACH,
        NO_LINE_OF_SIGHT;

        public boolean allowed() {
            return this == NONE;
        }
    }

    /**
     * Facts about one swallow request, supplied lazily by the server service (or a test fake).
     * 一次吞噬请求的事实，由服务端服务（或测试替身）按需提供。
     */
    public interface SwallowChecks {
        /** The round is ACTIVE. / 对局进行中。 */
        boolean running();

        /** Exactly NoellesRoles' Taotie. / 恰为 NoellesRoles 的饕餮。 */
        boolean exactTaotie();

        /** Playing, alive, survival mode, not an active Wraith. / 参与中、存活、生存模式、非激活冤魂。 */
        boolean aliveParticipant();

        /** Inside another Taotie. / 在（另一只）饕餮肚里。 */
        boolean swallowed();

        /** In a Partygoer's arms (no skills while carried). / 被派对客扛着（被扛时不能用技能）。 */
        boolean carried();

        /** Bonked by a Taotie head. / 被饕餮头颅砸晕。 */
        boolean dazed();

        /** Engineer stun or SparkWitch Control Expert stun. / 工程师定身或 SparkWitch 控场专家眩晕。 */
        boolean stunned();

        /** SparkTraits role-skill block or a pending Last Stand. / SparkTraits 职业技能封锁或背水一战待定。 */
        boolean roleSkillBlocked();

        /** Already has a Partygoer kicking inside. / 肚子里已经有一只派对客在踢。 */
        boolean digesting();

        int swallowCooldownTicks();

        /** The id resolves to a live, killable Partygoer in the Taotie's world. / id 对应饕餮所在世界中在场、可被打死的派对客。 */
        boolean livePartygoer();

        double squaredDistanceToPartygoer();

        int reachSquared();

        boolean canSeePartygoer();
    }

    private PartygoerTaotieRules() {
    }

    /**
     * Server validation in order: (1) running, exact Taotie, alive, not swallowed, not carried, not dazed, not stunned,
     * not skill-blocked, not already digesting one; (2) NoellesRoles' swallow cooldown; (3) a live Partygoer; (4)
     * NoellesRoles' feet-to-feet reach and line of sight, exactly like its player swallow.
     * 按顺序的服务端校验：(1) 对局进行中、恰为饕餮、存活、未被吞、未被扛着、未被砸晕、未被定身/眩晕、技能未被封锁、肚里没有另一只；
     * (2) NoellesRoles 吞噬冷却；(3) 在场的派对客；(4) 与其吞人完全一致的脚到脚距离与视线。
     */
    public static Denial swallowDenial(SwallowChecks checks) {
        if (!checks.running()) {
            return Denial.NOT_RUNNING;
        }
        if (!checks.exactTaotie()) {
            return Denial.NOT_TAOTIE;
        }
        if (!checks.aliveParticipant()) {
            return Denial.NOT_ALIVE;
        }
        if (checks.swallowed()) {
            return Denial.SWALLOWED;
        }
        if (checks.carried()) {
            return Denial.CARRIED;
        }
        if (checks.dazed()) {
            return Denial.DAZED;
        }
        if (checks.stunned()) {
            return Denial.STUNNED;
        }
        if (checks.roleSkillBlocked()) {
            return Denial.SKILL_BLOCKED;
        }
        if (checks.digesting()) {
            return Denial.ALREADY_DIGESTING;
        }
        if (!isCooldownReady(checks.swallowCooldownTicks())) {
            return Denial.COOLDOWN;
        }
        if (!checks.livePartygoer()) {
            return Denial.NO_PARTYGOER;
        }
        if (!withinReach(checks.squaredDistanceToPartygoer(), checks.reachSquared())) {
            return Denial.OUT_OF_REACH;
        }
        if (!checks.canSeePartygoer()) {
            return Denial.NO_LINE_OF_SIGHT;
        }
        return Denial.NONE;
    }

    public static boolean isCooldownReady(int remainingTicks) {
        return remainingTicks <= 0;
    }

    /** Inclusive, NaN-safe reach check. / 含边界且对 NaN 安全的距离判定。 */
    public static boolean withinReach(double squaredDistance, int reachSquared) {
        return squaredDistance >= 0.0 && reachSquared >= 0 && squaredDistance <= reachSquared;
    }

    /**
     * Cost of a Partygoer swallow: NoellesRoles' dynamic cooldown (the same as a player swallow), or its fixed fallback
     * while the dynamic value is unset. / 吞派对客的代价：NoellesRoles 的动态冷却（与吞人相同）；动态值未设置时用其固定回退值。
     */
    public static int swallowCostTicks(int calculatedTicks, int fallbackTicks) {
        return calculatedTicks > 0 ? calculatedTicks : Math.max(0, fallbackTicks);
    }

    // ---- Kicking inside / 肚里狂踢 ----

    /** One server tick off the countdown, never below zero. / 倒计时减一刻，不低于零。 */
    public static int afterTick(int ticksLeft) {
        return Math.max(0, ticksLeft - 1);
    }

    /** The Taotie is kicked to death once the countdown runs out. / 倒计时归零时饕餮被一脚踢死。 */
    public static boolean kicksToDeath(int ticksLeft) {
        return ticksLeft <= 0;
    }

    // ---- Client claim / 客户端认领 ----

    /**
     * Whether a Partygoer under the vanilla crosshair shields the press: within NoellesRoles' own eye-to-hit reach (3.0),
     * so anything NoellesRoles could target behind it is farther away. A shielded press is always claimed, even when the
     * server's feet-to-feet reach would refuse, so a player behind the Partygoer is never swallowed through it.
     * 原版准星上的派对客是否遮挡本次按键：处于 NoellesRoles 自己的眼到命中点距离（3.0）内，其后的任何目标都更远。被遮挡的按键
     * 总会被认领，即使服务端脚到脚距离会拒绝，派对客身后的玩家因此不会被隔着吞掉。
     */
    public static boolean shieldsPress(double eyeToHitDistance) {
        return eyeToHitDistance >= 0.0 && eyeToHitDistance <= CLIENT_REACH;
    }

    /**
     * Local Taotie readiness, mirroring the server's actor checks the client can see (stuns cancel the key at HEAD, so
     * they never reach here). / 本地饕餮是否就绪，对应客户端可见的服务端施放者检查（定身/眩晕在 HEAD 处取消按键，不会到这里）。
     */
    public static boolean taotieReady(boolean exactTaotie, boolean aliveSurvival, boolean swallowed, boolean carried,
                                      boolean dazed, int swallowCooldownTicks) {
        return exactTaotie && aliveSurvival && !swallowed && !carried && !dazed
                && isCooldownReady(swallowCooldownTicks);
    }

    /**
     * Client claim of one shared-key press. Cheap gates first: only a real press of NoellesRoles' ability key on a server
     * that accepts {@code partygoer_swallow}; then the local Taotie must be ready and the vanilla crosshair must be on a
     * Partygoer that {@linkplain #shieldsPress shields} the press. Claimed means NoellesRoles sees no press this call.
     * Only our own entity is ever claimed, so SparkWitch's Search Car claim on the same key never competes with it.
     * 客户端对一次共享键按下的认领。先做廉价判定：只有在能接收 {@code partygoer_swallow} 的服务器上真实按下 NoellesRoles 技能键；
     * 之后本地饕餮必须就绪，且原版准星落在遮挡本次按键的派对客上。认领后 NoellesRoles 在这次调用中看不到按键。只认领我们自己的
     * 实体，因此与同一按键上 SparkWitch 搜寻小车的认领互不冲突。
     */
    public static boolean claimsPress(boolean pressed, boolean abilityKey, boolean connectionReady,
                                      BooleanSupplier taotieReady, BooleanSupplier partygoerInReach) {
        return pressed
                && abilityKey
                && connectionReady
                && taotieReady.getAsBoolean()
                && partygoerInReach.getAsBoolean();
    }
}
