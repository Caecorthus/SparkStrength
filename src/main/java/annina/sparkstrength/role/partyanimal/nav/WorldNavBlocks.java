package annina.sparkstrength.role.partyanimal.nav;

import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.List;

/**
 * {@link NavBlocks} over a live server world. Doors (every Wathe door, train doors included, and vanilla doors) report no
 * collision because the Partygoer opens them all; unloaded chunks are solid so the grid never forces a chunk load.
 * 基于服务器世界的 NavBlocks。门（所有 Wathe 门，含列车外门，以及原版门）不报告碰撞，因为派对客全都推得开；未加载区块视为实心，
 * 网格绝不强制加载区块。
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
        if (isDoor(state)) {
            return NONE;
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
        if (state.getBlock() instanceof LadderBlock) {
            flags |= FLAG_LADDER;
        }
        if (state.getBlock() instanceof TrainDoorBlock) {
            flags |= FLAG_TRAIN_DOOR;
        }
        return flags;
    }

    /** Any door: Wathe doors (train doors included) and vanilla doors. / 任何门：Wathe 门（含列车外门）与原版门。 */
    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorPartBlock || state.getBlock() instanceof DoorBlock;
    }

    /** A door inside the train: any door but a train door. / 车内的门：列车外门以外的任何门。 */
    public static boolean isRoomDoor(BlockState state) {
        return isDoor(state) && !(state.getBlock() instanceof TrainDoorBlock);
    }

    @Override
    public boolean isLoaded(int x, int z) {
        return loaded(x, z);
    }

    private boolean loaded(int x, int z) {
        return world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
    }
}
