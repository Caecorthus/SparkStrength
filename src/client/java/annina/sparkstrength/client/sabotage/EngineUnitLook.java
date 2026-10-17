package annina.sparkstrength.client.sabotage;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Where an engine unit's exhaust and gauge panel are, from its model (authored facing north), for particles.
 * 动力装置排气管与仪表面板的位置，取自模型（按朝北编写），用于粒子。
 */
final class EngineUnitLook {
    /** An intact unit sparkles about every 4 s. / 完好的装置大约每 4 秒闪一下。 */
    static final int INTACT_SPARKLE_INTERVAL = 80;
    /** Exhaust outlet, in model pixels for a north-facing unit. / 排气管口，朝北时的模型像素坐标。 */
    private static final double[] EXHAUST = {11.5, 16.25, 11.5};
    /** Around the tachometer, just in front of the panel. / 转速表附近、略在面板前方。 */
    private static final double[] PANEL = {8.0, 7.0, 0.25};

    private EngineUnitLook() {
    }

    static Vec3d exhaust(BlockPos pos, Direction facing) {
        return place(pos, facing, EXHAUST);
    }

    static Vec3d panel(BlockPos pos, Direction facing) {
        return place(pos, facing, PANEL);
    }

    /** Rotates a north-authored pixel point like the blockstate y rotation. / 按方块状态的 y 旋转，转换朝北编写的像素点。 */
    private static Vec3d place(BlockPos pos, Direction facing, double[] p) {
        double x = p[0] / 16.0;
        double y = p[1] / 16.0;
        double z = p[2] / 16.0;
        double rx;
        double rz;
        switch (facing) {
            case EAST -> {
                rx = 1.0 - z;
                rz = x;
            }
            case SOUTH -> {
                rx = 1.0 - x;
                rz = 1.0 - z;
            }
            case WEST -> {
                rx = z;
                rz = 1.0 - x;
            }
            default -> {
                rx = x;
                rz = z;
            }
        }
        return new Vec3d(pos.getX() + rx, pos.getY() + y, pos.getZ() + rz);
    }
}
