package annina.sparkstrength.role.partyanimal.nav;

import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.List;

/**
 * {@link NavBlocks} over a live server world. Room doors (every Wathe small door that is not a train door, and vanilla
 * doors) report no collision; train doors are solid; unloaded chunks are solid so the grid never forces a chunk load.
 * 基于服务器世界的 NavBlocks。房门（所有非列车外门的 Wathe 小门，以及原版门）不报告碰撞；列车外门为实心；未加载区块视为实心，网格绝不强制加载区块。
 */
public final class WorldNavBlocks implements NavBlocks {
    private static final double[][] NONE = new double[0][];
    private static final double[][] FULL = {{0.0, 0.0, 0.0, 1.0, 1.0, 1.0}};

    private final ServerWorld world;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    public WorldNavBlocks(ServerWorld world) {
        this.world = world;
    }

    @Override
    public double[][] boxes(int x, int y, int z) {
        if (!loaded(x, z)) {
            return FULL;
        }
        BlockState state = world.getBlockState(cursor.set(x, y, z));
        if (isRoomDoor(state)) {
            return NONE;
        }
        if (state.getBlock() instanceof TrainDoorBlock) {
            return FULL;
        }
        VoxelShape shape = state.getCollisionShape(world, cursor);
        if (shape.isEmpty()) {
            return NONE;
        }
        List<Box> boxes = shape.getBoundingBoxes();
        double[][] out = new double[boxes.size()][];
        for (int i = 0; i < boxes.size(); i++) {
            Box box = boxes.get(i);
            out[i] = new double[]{box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
        }
        return out;
    }

    @Override
    public int flags(int x, int y, int z) {
        if (!loaded(x, z)) {
            return 0;
        }
        BlockState state = world.getBlockState(cursor.set(x, y, z));
        int flags = 0;
        if (isRoomDoor(state)) {
            flags |= FLAG_ROOM_DOOR;
        }
        if (state.getBlock() instanceof StairsBlock) {
            flags |= FLAG_STAIRS;
        }
        return flags;
    }

    /** A door the Partygoer walks through: Wathe small doors except train doors, and vanilla doors. / 派对客可穿过的门。 */
    public static boolean isRoomDoor(BlockState state) {
        return state.getBlock() instanceof DoorPartBlock && !(state.getBlock() instanceof TrainDoorBlock)
                || state.getBlock() instanceof DoorBlock;
    }

    private boolean loaded(int x, int z) {
        return world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
    }
}
