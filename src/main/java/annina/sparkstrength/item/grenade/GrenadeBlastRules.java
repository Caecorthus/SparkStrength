package annina.sparkstrength.item.grenade;

/** Shared, game-independent blast range rules. / 与游戏环境无关的共用爆炸范围规则。 */
public final class GrenadeBlastRules {
    public static final double WATHE_BLAST_RADIUS = 3.0;

    private GrenadeBlastRules() {
    }

    public static boolean containsSphere(double dx, double dy, double dz, double radius) {
        return Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                && Double.isFinite(radius) && radius >= 0
                && dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
