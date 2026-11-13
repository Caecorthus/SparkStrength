package annina.sparkstrength.role.timekeeper;

/**
 * 计时员怀表的所有可调数值集中定义在这里。
 * 这样后续调整金币价格、怀表自身冷却或死亡奖励时，不需要在能力逻辑中搜索散落的字面量。
 */
public final class TimekeeperConstants {
    public static final int ITEM_REFRESH_COST_COINS = 100;
    public static final int ABILITY_REFRESH_COST_COINS = 75;
    public static final int ITEM_REFRESH_COOLDOWN_TICKS = 40 * 20;
    public static final int ABILITY_REFRESH_COOLDOWN_TICKS = 30 * 20;
    public static final int DEATH_REWARD_COINS = 25;
    /** Spark 版 NoellesRoles 的计时员职业颜色：RGB(0,38,255)。 */
    public static final int ROLE_COLOR = 0x0026FF;

    private TimekeeperConstants() {
    }
}
