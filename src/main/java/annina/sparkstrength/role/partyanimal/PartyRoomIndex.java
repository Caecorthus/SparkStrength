package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.role.partyanimal.nav.NavBlocks;
import annina.sparkstrength.role.partyanimal.nav.TrainNavGrid;
import annina.sparkstrength.role.partyanimal.nav.TrainPathfinder;
import annina.sparkstrength.role.partyanimal.nav.WorldNavBlocks;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * The rooms the Party Animal can pick, per world and round. Wathe maps only name rooms through their doors (a room door's
 * key name is the room name), so rooms are found by scanning the play area for keyed small doors and flooding each door's
 * sides on the train grid: the side that closes off small is the cabin.
 * 派对狂可选的房间，按世界与对局缓存。Wathe 地图只通过门来命名房间（房门的钥匙名就是房间名），因此扫描游玩区内带钥匙名的小门，
 * 并在列车网格上从门的两侧泛洪：先封闭且较小的一侧就是客房。
 */
public final class PartyRoomIndex {
    private static final Map<ServerWorld, Cached> CACHE = new IdentityHashMap<>();
    /** Longer key names are cut so the picker packet stays valid. / 更长的钥匙名会被截断，保证选房数据包有效。 */
    public static final int MAX_NAME_LENGTH = 64;

    private PartyRoomIndex() {
    }

    /** A pickable room: its name and a standable centre cell. / 可选房间：名称与可站立的中心格。 */
    public record PartyRoom(String name, long anchorCell, Vec3d anchor) {
    }

    /** Rooms for this round, sorted naturally; empty when the map has none. / 本局的房间，自然排序；地图没有房间时为空。 */
    public static List<PartyRoom> rooms(ServerWorld world, @Nullable UUID roundId) {
        Cached cached = CACHE.get(world);
        if (cached != null && roundId != null && roundId.equals(cached.roundId)) {
            return cached.rooms;
        }
        boolean[] complete = {true};
        List<PartyRoom> rooms = scan(world, newGrid(world), complete);
        // A scan that skipped unloaded chunks is retried on the next request. / 跳过了未加载区块的扫描下次请求时重试。
        if (complete[0]) {
            CACHE.put(world, new Cached(roundId, rooms));
        }
        return rooms;
    }

    public static void clear(ServerWorld world) {
        CACHE.remove(world);
    }

    public static void clearAll() {
        CACHE.clear();
    }

    /** A fresh grid over the play area. / 覆盖游玩区的新网格。 */
    public static TrainNavGrid newGrid(ServerWorld world) {
        Box area = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        return new TrainNavGrid(new WorldNavBlocks(world),
                (int) Math.floor(area.minX), (int) Math.floor(area.minY), (int) Math.floor(area.minZ),
                (int) Math.floor(area.maxX), (int) Math.floor(area.maxY), (int) Math.floor(area.maxZ));
    }

    private static List<PartyRoom> scan(ServerWorld world, TrainNavGrid grid, boolean[] complete) {
        Box area = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        Map<String, List<BlockPos>> doorsByName = new TreeMap<>(PartyRoomRules.NATURAL_ORDER);
        int minChunkX = ChunkSectionPos.getSectionCoord(area.minX);
        int maxChunkX = ChunkSectionPos.getSectionCoord(area.maxX);
        int minChunkZ = ChunkSectionPos.getSectionCoord(area.minZ);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(area.maxZ);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) {
                    complete[0] = false;
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SmallDoorBlockEntity door) || door.getKeyName().isEmpty()) {
                        continue;
                    }
                    BlockPos pos = door.getPos();
                    BlockState state = world.getBlockState(pos);
                    if (!(state.getBlock() instanceof SmallDoorBlock) || state.getBlock() instanceof TrainDoorBlock
                            || state.get(SmallDoorBlock.HALF) != DoubleBlockHalf.LOWER
                            || !area.contains(pos.toCenterPos())) {
                        continue;
                    }
                    String name = door.getKeyName().length() > MAX_NAME_LENGTH
                            ? door.getKeyName().substring(0, MAX_NAME_LENGTH) : door.getKeyName();
                    doorsByName.computeIfAbsent(name, key -> new ArrayList<>()).add(pos.toImmutable());
                }
            }
        }
        List<PartyRoom> rooms = new ArrayList<>();
        for (Map.Entry<String, List<BlockPos>> entry : doorsByName.entrySet()) {
            PartyRoom room = locate(grid, entry.getKey(), entry.getValue());
            if (room != null) {
                rooms.add(room);
            }
        }
        return List.copyOf(rooms);
    }

    private static @Nullable PartyRoom locate(TrainNavGrid grid, String name, List<BlockPos> doors) {
        LongPredicate wall = cell -> isDoorCell(grid, cell);
        List<Set<Long>> sides = new ArrayList<>();
        for (BlockPos door : doors) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos side = door.offset(direction);
                long seed = grid.snap(side.getX() + 0.5, side.getY(), side.getZ() + 0.5);
                if (seed == Long.MIN_VALUE || wall.test(seed)) {
                    continue;
                }
                sides.add(TrainPathfinder.flood(grid, seed, wall, PartyRoomRules.ROOM_CELL_CAP).keySet());
            }
        }
        Set<Long> interior = PartyRoomRules.pickInterior(sides);
        if (interior == null) {
            return null;
        }
        Map<Long, int[]> cells = new LinkedHashMap<>();
        for (long cell : interior) {
            cells.put(cell, new int[]{TrainNavGrid.x(cell), TrainNavGrid.z(cell)});
        }
        long anchor = PartyRoomRules.centreCell(cells);
        if (anchor == Long.MIN_VALUE) {
            return null;
        }
        return new PartyRoom(name, anchor, cellCentre(grid, anchor));
    }

    /** Feet or head inside a room door. / 脚或头在房门方块里。 */
    static boolean isDoorCell(TrainNavGrid grid, long cell) {
        int x = TrainNavGrid.x(cell);
        int y = TrainNavGrid.y(cell);
        int z = TrainNavGrid.z(cell);
        return (grid.flags(x, y, z) & NavBlocks.FLAG_ROOM_DOOR) != 0
                || (grid.flags(x, y + 1, z) & NavBlocks.FLAG_ROOM_DOOR) != 0;
    }

    public static Vec3d cellCentre(TrainNavGrid grid, long cell) {
        return new Vec3d(TrainNavGrid.x(cell) + 0.5, grid.floor(cell), TrainNavGrid.z(cell) + 0.5);
    }

    private record Cached(@Nullable UUID roundId, List<PartyRoom> rooms) {
    }
}
