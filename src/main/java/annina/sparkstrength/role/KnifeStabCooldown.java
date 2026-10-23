package annina.sparkstrength.role;

/**
 * Every real knife stab cools down a flat 30 s (owner 2026-10-23). Wathe scales its own by player count (60 s, 5 s less
 * per player above killer-team size × killer dividend, never below 10 s), which left most stabs at the 10 s floor.
 * Wathe's receiver ({@code KnifeStabCooldownMixin}) and our own stab paths (Silencer quiet stab, drone and Partygoer
 * stabs) all pay this instead.
 * 每次真实刀刺固定冷却 30 秒：Wathe 原本按人数动态缩短（60 秒，超出“杀手阵营人数 × 杀手比例”的每名玩家减 5 秒，最低 10 秒），
 * 结果大多停在 10 秒保底。Wathe 刺杀接收器与我们自己的刺杀路径（静语者静音刺、无人机与派对客刺击）统一使用该值。
 */
public final class KnifeStabCooldown {
    public static final int TICKS = 30 * 20;

    private KnifeStabCooldown() {
    }
}
