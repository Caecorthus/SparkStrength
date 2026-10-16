package annina.sparkstrength.block;

import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rotates boxes authored for a north-facing wall block (front toward -Z, back against z = 16) to every horizontal
 * facing, matching blockstate rotations y = 0 / 90 / 180 / 270.
 * 把按「正面朝北（-Z）、背面贴在 z = 16」编写的壁挂方块碰撞箱旋转到各个水平朝向，与方块状态的 y = 0 / 90 / 180 / 270 一致。
 */
final class WallMountedShapes {
    private WallMountedShapes() {
    }

    /** Each box is {minX, minY, minZ, maxX, maxY, maxZ} in pixels for the north facing. / 每个盒子为朝北时的像素坐标。 */
    static Map<Direction, VoxelShape> build(double[]... northBoxes) {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            VoxelShape shape = VoxelShapes.empty();
            for (double[] box : northBoxes) {
                shape = VoxelShapes.union(shape, rotate(box, facing));
            }
            shapes.put(facing, shape.simplify());
        }
        return shapes;
    }

    private static VoxelShape rotate(double[] box, Direction facing) {
        double x1 = box[0], y1 = box[1], z1 = box[2], x2 = box[3], y2 = box[4], z2 = box[5];
        return switch (facing) {
            case EAST -> cuboid(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> cuboid(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> cuboid(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> cuboid(x1, y1, z1, x2, y2, z2);
        };
    }

    private static VoxelShape cuboid(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.createCuboidShape(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }
}
