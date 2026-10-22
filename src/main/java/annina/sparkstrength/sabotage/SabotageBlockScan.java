package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.function.BiConsumer;

/**
 * Finds junction boxes, circuit breakers and engine units in a box of loaded chunks. Sections whose palette holds none
 * of them are skipped whole, so a full play area costs a few hundred palette checks instead of ~900k block reads. Works
 * on both sides; unloaded chunks are skipped, never loaded.
 * 在已加载区块的一个范围内查找接线箱、电闸与动力装置。调色板里没有这些方块的区段整段跳过，所以整个 play area 只需几百次
 * 调色板检查，而不是约 90 万次方块读取。两端通用；未加载的区块直接跳过，绝不加载。
 */
public final class SabotageBlockScan {
    private SabotageBlockScan() {
    }

    public static void scan(World world, Box area, BiConsumer<BlockPos, BlockState> visitor) {
        if (world == null || area == null) {
            return;
        }
        int minX = (int) Math.floor(area.minX);
        int minY = Math.max(world.getBottomY(), (int) Math.floor(area.minY));
        int minZ = (int) Math.floor(area.minZ);
        int maxX = (int) Math.floor(area.maxX);
        int maxY = Math.min(world.getTopY() - 1, (int) Math.floor(area.maxY));
        int maxZ = (int) Math.floor(area.maxZ);
        if (minY > maxY) {
            return;
        }
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int cx = ChunkSectionPos.getSectionCoord(minX); cx <= ChunkSectionPos.getSectionCoord(maxX); cx++) {
            for (int cz = ChunkSectionPos.getSectionCoord(minZ); cz <= ChunkSectionPos.getSectionCoord(maxZ); cz++) {
                Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (int sy = ChunkSectionPos.getSectionCoord(minY); sy <= ChunkSectionPos.getSectionCoord(maxY); sy++) {
                    int index = chunk.sectionCoordToIndex(sy);
                    if (index < 0 || index >= chunk.getSectionArray().length) {
                        continue;
                    }
                    scanSection(chunk.getSection(index), cursor,
                            Math.max(minX, cx << 4), Math.min(maxX, (cx << 4) + 15),
                            Math.max(minY, sy << 4), Math.min(maxY, (sy << 4) + 15),
                            Math.max(minZ, cz << 4), Math.min(maxZ, (cz << 4) + 15), visitor);
                }
            }
        }
    }

    /**
     * Every fixture in one chunk, all heights (the client indexes chunks as they load).
     * 一个区块内所有高度的全部固定装置（客户端在区块加载时建立索引）。
     */
    public static void scanChunk(Chunk chunk, BiConsumer<BlockPos, BlockState> visitor) {
        if (chunk == null) {
            return;
        }
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int x0 = chunk.getPos().getStartX();
        int z0 = chunk.getPos().getStartZ();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int index = 0; index < sections.length; index++) {
            int y0 = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(index));
            scanSection(sections[index], cursor, x0, x0 + 15, y0, y0 + 15, z0, z0 + 15, visitor);
        }
    }

    /**
     * Whether {@link #scan} over {@code area} would visit {@code pos} (its chunk loaded).
     * 对 {@code area} 执行 {@link #scan} 时是否会访问 {@code pos}（其区块已加载时）。
     */
    public static boolean covers(World world, Box area, BlockPos pos) {
        int y = pos.getY();
        return pos.getX() >= (int) Math.floor(area.minX) && pos.getX() <= (int) Math.floor(area.maxX)
                && pos.getZ() >= (int) Math.floor(area.minZ) && pos.getZ() <= (int) Math.floor(area.maxZ)
                && y >= Math.max(world.getBottomY(), (int) Math.floor(area.minY))
                && y <= Math.min(world.getTopY() - 1, (int) Math.floor(area.maxY));
    }

    private static void scanSection(ChunkSection section, BlockPos.Mutable cursor, int x0, int x1, int y0, int y1,
                                    int z0, int z1, BiConsumer<BlockPos, BlockState> visitor) {
        if (section == null || section.isEmpty() || !section.hasAny(SparkStrengthBlocks::isSabotageFixture)) {
            return;
        }
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    if (SparkStrengthBlocks.isSabotageFixture(state)) {
                        visitor.accept(cursor.set(x, y, z).toImmutable(), state);
                    }
                }
            }
        }
    }
}
