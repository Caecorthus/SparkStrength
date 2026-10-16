package annina.sparkstrength.client.sabotage;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkNibbleArray;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side sky-light block during a lights sabotage: inside the train the client's sky light reads as 0, so with
 * the lamps off the interior is dark even on a daytime map. Done at the light source rather than in the lightmap so
 * Sodium meshing, entities and Iris shader packs (which read raw light levels, not the vanilla lightmap) all see it.
 * Only the client world's sky provider is affected; the integrated server's light engine is a different object.
 * 灯光破坏期间在客户端屏蔽天空光照：车厢内客户端的天空光照读数为 0，灯熄灭后即使是白天地图车厢里也是黑的。
 * 在光照源头而不是光照贴图里处理，所以 Sodium 网格、实体以及 Iris 光影包（它们读原始光照等级，不读原版光照贴图）都能看到。
 * 只影响客户端世界的天空光照提供者；内置服务端的光照引擎是另一个对象。
 *
 * <p>Whole-section reads (Sodium meshing) and per-position reads agree: a section fully inside reads all zero, a
 * boundary section keeps its real light outside the area.
 * 整段读取（Sodium 网格）与按位置读取结果一致：完全在区域内的区段全为零，边界区段在区域外保留真实光照。</p>
 */
public final class SabotageSkyLight {
    private record Area(Object skyView, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        boolean intersects(ChunkSectionPos pos) {
            return pos.getMaxX() >= minX && pos.getMinX() <= maxX
                    && pos.getMaxY() >= minY && pos.getMinY() <= maxY
                    && pos.getMaxZ() >= minZ && pos.getMinZ() <= maxZ;
        }

        boolean covers(ChunkSectionPos pos) {
            return pos.getMinX() >= minX && pos.getMaxX() <= maxX
                    && pos.getMinY() >= minY && pos.getMaxY() <= maxY
                    && pos.getMinZ() >= minZ && pos.getMaxZ() <= maxZ;
        }
    }

    private static volatile @Nullable Area area;

    private SabotageSkyLight() {
    }

    public static boolean isDark(Object provider, BlockPos pos) {
        Area current = area;
        return current != null && provider == current.skyView()
                && current.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * The section to hand out instead of {@code original}, or null to keep it. Never returns null for an affected
     * section: Sodium turns a null sky section into full sky light.
     * 用来代替 original 的区段；返回 null 表示保留原值。受影响的区段绝不返回 null：Sodium 会把 null 的天空区段当成满天光。
     */
    public static @Nullable ChunkNibbleArray darkSection(Object provider, ChunkSectionPos pos,
                                                         @Nullable ChunkNibbleArray original) {
        Area current = area;
        if (current == null || provider != current.skyView() || !current.intersects(pos)) {
            return null;
        }
        if (current.covers(pos)) {
            return new ChunkNibbleArray(0);
        }
        // A missing sky section means open sky (15), like Sodium assumes. / 缺失的天空区段表示露天（15），与 Sodium 的假设一致。
        ChunkNibbleArray partial = original == null ? new ChunkNibbleArray(15) : original.copy();
        int x0 = Math.max(current.minX(), pos.getMinX()), x1 = Math.min(current.maxX(), pos.getMaxX());
        int y0 = Math.max(current.minY(), pos.getMinY()), y1 = Math.min(current.maxY(), pos.getMaxY());
        int z0 = Math.max(current.minZ(), pos.getMinZ()), z1 = Math.min(current.maxZ(), pos.getMaxZ());
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    partial.set(x & 15, y & 15, z & 15, 0);
                }
            }
        }
        return partial;
    }

    public static boolean isActive() {
        return area != null;
    }

    /** Turns the block on over {@code box}, or off with null, rebuilding the affected sections. / 在 box 范围内开启屏蔽，传 null 关闭，并重建受影响的区段。 */
    static void set(MinecraftClient client, @Nullable Box box) {
        Area previous = area;
        Area next = null;
        if (box != null && client.world != null) {
            next = new Area(client.world.getLightingProvider().get(LightType.SKY),
                    (int) Math.floor(box.minX) - 1, (int) Math.floor(box.minY) - 1, (int) Math.floor(box.minZ) - 1,
                    (int) Math.floor(box.maxX) + 1, (int) Math.floor(box.maxY) + 1, (int) Math.floor(box.maxZ) + 1);
        }
        if (sameArea(previous, next)) {
            return;
        }
        area = next;
        rebuild(client, previous);
        rebuild(client, next);
    }

    private static boolean sameArea(@Nullable Area a, @Nullable Area b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.skyView() == b.skyView() && a.minX() == b.minX() && a.minY() == b.minY() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxY() == b.maxY() && a.maxZ() == b.maxZ();
    }

    /**
     * Calls (never injects into) WorldRenderer#scheduleBlockRenders, which Sodium overwrites; avoids reload(), which
     * rebuilds everything. One block of margin covers the smooth lighting of neighbours.
     * 调用（而不是注入）被 Sodium 覆写的 WorldRenderer#scheduleBlockRenders；不用 reload()，它会重建全部区段。
     * 多一格余量以覆盖相邻方块的平滑光照。
     */
    private static void rebuild(MinecraftClient client, @Nullable Area target) {
        if (target == null || client.worldRenderer == null) {
            return;
        }
        client.worldRenderer.scheduleBlockRenders(target.minX() - 1, target.minY() - 1, target.minZ() - 1,
                target.maxX() + 1, target.maxY() + 1, target.maxZ() + 1);
    }
}
