package annina.sparkstrength.role.spiritualist;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules of the Spiritualist's Wraith possession (灵界行者附身冤魂, role skill 2): numbers, reach and cooldown math.
 * Possession rides on NoellesRoles' spirit projection: the view is gray and anonymous, the body is defenseless and any
 * disturbance pulls the soul back, while the camera follows the possessed Wraith at any distance.
 * 灵界行者附身冤魂（职业技能 2）的纯规则：数值、距离与冷却计算。附身建立在 NoellesRoles 的灵魂出窍之上：画面灰暗且认不出人，
 * 肉身毫无防备，任何惊扰都会把灵魂拉回；镜头则跟随被附身的冤魂，不受距离限制。
 */
public final class SpiritPossessionRules {
    public static final Identifier SPIRITUALIST_ID = Identifier.of("noellesroles", "spiritualist");
    public static final Identifier POSSESS_PAYLOAD_ID = Identifier.of("sparkstrength", "spirit_possess");
    /** Leaving must always get through, so payload blockers never see it. / 退出必须始终送达，因此不进入任何拦截名单。 */
    public static final Identifier EXIT_PAYLOAD_ID = Identifier.of("sparkstrength", "spirit_possess_exit");
    public static final Identifier STATE_PAYLOAD_ID = Identifier.of("sparkstrength", "spirit_possess_state");
    /** SparkFactionAPI forced-cooldown store id. / SparkFactionAPI 强制冷却存储 id。 */
    public static final Identifier COOLDOWN_STORE_ID = Identifier.of("sparkstrength", "spirit_possession");

    public static final int TICKS_PER_SECOND = 20;
    /** The longest possession: 20 s. / 单次附身最长 20 秒。 */
    public static final int MAX_POSSESSION_TICKS = 20 * TICKS_PER_SECOND;
    /** Cooldown after any possession ends: 45 s. / 任意方式结束附身后的冷却：45 秒。 */
    public static final int COOLDOWN_TICKS = 45 * TICKS_PER_SECOND;
    /** Every Spiritualist starts the round on this cooldown: 60 s. / 灵界行者开局冷却：60 秒。 */
    public static final int OPENING_COOLDOWN_TICKS = 60 * TICKS_PER_SECOND;
    /** How far the crosshair reaches for a Wraith, from the body's or the spirit's eyes. / 准星可选中冤魂的距离（从肉身或灵魂的视点算起）。 */
    public static final double AIM_RANGE = 16.0D;
    /** NoellesRoles' spirit radius around the body ({@code SpiritCamera.MAX_RADIUS}). / NoellesRoles 灵魂离开肉身的半径。 */
    public static final double SPIRIT_RADIUS = 30.0D;
    /** Server slack for interpolation and hitbox size. / 服务端容差（插值与碰撞箱大小）。 */
    public static final double REACH_SLACK = 2.0D;
    /** Client ticks without the possessed entity before the view gives up. / 客户端找不到被附身实体多少刻后放弃画面。 */
    public static final int LINK_GRACE_TICKS = 40;
    /** Client ticks to wait for the server to confirm an exit request. / 客户端等待服务端确认退出请求的刻数。 */
    public static final int EXIT_CONFIRM_TICKS = 40;
    /** Minimum server ticks between two processed start requests. / 两次被处理的开始请求之间的最少服务器刻数。 */
    public static final int START_SPAM_TICKS = 10;

    private SpiritPossessionRules() {
    }

    public static boolean isSpiritualist(@Nullable Role role) {
        return role != null && SPIRITUALIST_ID.equals(role.identifier());
    }

    /**
     * Server reach check. While projecting, the server only knows the body, and the spirit may be up to
     * {@link #SPIRIT_RADIUS} away from it; otherwise the distance is measured from the body's eyes.
     * 服务端距离校验。灵魂出窍时服务端只知道肉身位置，而灵魂最远可离开肉身 SPIRIT_RADIUS；否则从肉身视点测距。
     */
    public static boolean withinReach(boolean projecting, double distanceSquared) {
        double reach = (projecting ? SPIRIT_RADIUS + AIM_RANGE : AIM_RANGE) + REACH_SLACK;
        return distanceSquared >= 0.0D && distanceSquared <= reach * reach;
    }

    /** A cooldown already longer (a forced penalty) is kept. / 已有更长的冷却（强制惩罚）时保留。 */
    public static int cooldownAfterSession(int currentCooldownTicks) {
        return Math.max(currentCooldownTicks, COOLDOWN_TICKS);
    }

    /** Whole seconds shown on the HUD, rounded up. / HUD 显示的整秒数，向上取整。 */
    public static int displaySeconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** Sneak leaves only on a fresh press, never on a key already held at the start. / 只有新按下潜行才会退出，开始时已按住的不算。 */
    public static boolean isFreshPress(boolean wasDown, boolean down) {
        return down && !wasDown;
    }
}
