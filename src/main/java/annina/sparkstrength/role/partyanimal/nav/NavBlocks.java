package annina.sparkstrength.role.partyanimal.nav;

/**
 * What the Partygoer's grid needs to know about one block, without Minecraft types so the grid is unit-testable.
 * 派对客网格需要知道的单个方块信息；不含 Minecraft 类型，便于单元测试。
 */
public interface NavBlocks {
    int FLAG_ROOM_DOOR = 1;
    int FLAG_STAIRS = 2;

    /**
     * Collision boxes of the block at (x, y, z) in block-local coordinates [0, 1], each {minX, minY, minZ, maxX, maxY,
     * maxZ}. Room doors report none (the Partygoer opens them); train doors report a full cube.
     * (x, y, z) 处方块的碰撞箱，方块局部坐标 [0, 1]，每个为 {minX, minY, minZ, maxX, maxY, maxZ}。房门不报告碰撞（派对客会推开）；
     * 列车外门报告为整格。
     */
    double[][] boxes(int x, int y, int z);

    /** {@link #FLAG_ROOM_DOOR} and/or {@link #FLAG_STAIRS}. / 房门与楼梯标记。 */
    int flags(int x, int y, int z);
}
