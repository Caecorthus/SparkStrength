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
 */
public final class SabotageSkyLight {
    private record Area(Object skyView, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        boolean intersectsSection(ChunkSectionPos pos) {
            return pos.getMaxX() >= minX && pos.getMinX() <= maxX
                    && pos.getMaxY() >= minY && pos.getMinY() <= maxY
                    && pos.getMaxZ() >= minZ && pos.getMinZ() <= maxZ;
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

    /** An all-zero section, never null: Sodium turns a null sky section into full sky light. / 全零区段，绝不返回 null：Sodium 会把 null 的天空区段当成满天光。 */
    public static @Nullable ChunkNibbleArray darkSection(Object provider, ChunkSectionPos pos) {
        Area current = area;
        if (current == null || provider != current.skyView() || !current.intersectsSection(pos)) {
            return null;
        }
        return new ChunkNibbleArray(0);
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
     * rebuilds everything.
     * 调用（而不是注入）被 Sodium 覆写的 WorldRenderer#scheduleBlockRenders；不用 reload()，它会重建全部区段。
     */
    private static void rebuild(MinecraftClient client, @Nullable Area target) {
        if (target == null || client.worldRenderer == null) {
            return;
        }
        client.worldRenderer.scheduleBlockRenders(target.minX(), target.minY(), target.minZ(),
                target.maxX(), target.maxY(), target.maxZ());
    }
}
