package annina.sparkstrength.role.partyanimal.nav;

/**
 * What the Partygoer's grid needs to know about one block, without Minecraft types so the grid is unit-testable.
 * 派对客网格需要知道的单个方块信息；不含 Minecraft 类型，便于单元测试。
 */
public interface NavBlocks {
    int FLAG_ROOM_DOOR = 1;
    int FLAG_STAIRS = 2;
    int FLAG_LADDER = 4;
    int FLAG_TRAIN_DOOR = 8;

    /**
     * Collision boxes of the block at (x, y, z) in block-local coordinates [0, 1], each {minX, minY, minZ, maxX, maxY,
     * maxZ}. Doors report none, train doors included (the Partygoer opens them all).
     * (x, y, z) 处方块的碰撞箱，方块局部坐标 [0, 1]，每个为 {minX, minY, minZ, maxX, maxY, maxZ}。门不报告碰撞，列车外门也一样
     * （派对客全都推得开）。
     */
    double[][] boxes(int x, int y, int z);

    /** Any of the {@code FLAG_} bits. / 任意 FLAG_ 标记位。 */
    int flags(int x, int y, int z);

    /**
     * Whether the column's answers can be cached: an unloaded chunk reads as solid for now but must be asked again
     * once it loads.
     * 该列的结果能否缓存：未加载的区块暂时视为实心，加载后必须重新询问。
     */
    default boolean isLoaded(int x, int z) {
        return true;
    }
}
