package annina.sparkstrength.item.m67;

import annina.sparkstrength.item.grenade.GrenadeBlastRules;

public final class M67Rules {
    public static final int OPENING_TICKS = 1800;
    public static final int CHARGE_TICKS = 20;
    public static final int EQUIP_COOLDOWN_TICKS = 20;
    public static final int CANCEL_COOLDOWN_TICKS = 40;
    public static final int THROW_COOLDOWN_TICKS = 300;
    public static final int BOMBER_COOLDOWN_TICKS = 200;
    public static final int FUSE_TICKS = 140;
    public static final double BLAST_RADIUS = 3.5;
    public static final double WARNING_RADIUS = 7.0;

    private M67Rules() {
    }

    // Distance-only warning; cover and final damage are server decisions.
    // 仅按距离提示；掩体和最终伤害由服务端判定。
    public static int warningColor(double dx, double dy, double dz) {
        if (GrenadeBlastRules.containsSphere(dx, dy, dz, BLAST_RADIUS)) {
            return 0xFF0000;
        }
        return GrenadeBlastRules.containsSphere(dx, dy, dz, WARNING_RADIUS) ? 0xFFFF00 : -1;
    }
}
