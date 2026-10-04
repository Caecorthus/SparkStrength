package annina.sparkstrength.role.vulture;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable tuning and role check for the Vulture's skateboard.
 * 秃鹫滑板的稳定数值与职业判定。
 */
public final class VultureSkateboardRules {
    /** Matched by stable id so NoellesRoles' mutable static field is never read early. / 按稳定 ID 匹配，避免过早读取 NoellesRoles 的可变静态字段。 */
    public static final Identifier VULTURE_ID = Identifier.of("noellesroles", "vulture");

    /** Round-start lock: 60 s. / 开局锁：60 秒。 */
    public static final int OPENING_TICKS = GameConstants.getInTicks(1, 0);
    /** One ride: 10 s. / 单次滑行：10 秒。 */
    public static final int RIDE_TICKS = GameConstants.getInTicks(0, 10);
    /** Cooldown after a ride ends: 10 s. / 滑行结束后的冷却：10 秒。 */
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(0, 10);
    /**
     * The item cooldown starts with the ride and covers ride + cooldown, so one sweep shows when the board is ready.
     * 物品冷却从上板开始，覆盖滑行与冷却，一次冷却动画即显示何时可再用。
     */
    public static final int USE_COOLDOWN_TICKS = RIDE_TICKS + COOLDOWN_TICKS;

    /** Movement speed x3 while riding. / 滑行时移动速度 x3。 */
    public static final double SPEED_MULTIPLIER = 3.0D;
    /**
     * ADD_MULTIPLIED_TOTAL amount for {@link #SPEED_MULTIPLIER}. Wathe rescales player speed by the attribute's
     * modifiers, so this multiplies its walk and sprint speeds alike.
     * {@link #SPEED_MULTIPLIER} 对应的 ADD_MULTIPLIED_TOTAL 数值。Wathe 按属性修饰比例缩放玩家速度，因此行走与疾跑同样乘算。
     */
    public static final double SPEED_MODIFIER_AMOUNT = SPEED_MULTIPLIER - 1.0D;

    private VultureSkateboardRules() {
    }

    public static boolean isVulture(@Nullable Role role) {
        return role != null && VULTURE_ID.equals(role.identifier());
    }

    /** Remaining round-start lock at {@code now}, 0 once free. / {@code now} 时剩余的开局锁刻数，解锁后为 0。 */
    public static int openingRemaining(long roundStartTick, long now) {
        long elapsed = Math.max(0L, now - roundStartTick);
        return (int) Math.max(0L, OPENING_TICKS - elapsed);
    }
}
