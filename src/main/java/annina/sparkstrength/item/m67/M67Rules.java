package annina.sparkstrength.item.m67;

public final class M67Rules {
    public static final int OPENING_TICKS = 1800;
    public static final int CHARGE_TICKS = 20;
    public static final int EQUIP_COOLDOWN_TICKS = 20;
    public static final int CANCEL_COOLDOWN_TICKS = 40;
    public static final int THROW_COOLDOWN_TICKS = 900;
    public static final int BOMBER_COOLDOWN_TICKS = 600;
    public static final int FUSE_TICKS = 140;
    public static final double BLAST_HALF_EXTENT = 3.5;
    public static final double WARNING_HALF_EXTENT = 7.0;

    private M67Rules() {
    }

    // Shared position-based inclusive cube, not hitbox overlap. / 共用坐标闭区间立方体，不检测碰撞箱重叠。
    public static boolean containsCube(double dx, double dy, double dz, double halfExtent) {
        return Math.abs(dx) <= halfExtent
                && Math.abs(dy) <= halfExtent
                && Math.abs(dz) <= halfExtent;
    }

    public static int warningColor(double dx, double dy, double dz) {
        if (containsCube(dx, dy, dz, BLAST_HALF_EXTENT)) {
            return 0xFF0000;
        }
        return containsCube(dx, dy, dz, WARNING_HALF_EXTENT) ? 0xFFFF00 : -1;
    }
}
