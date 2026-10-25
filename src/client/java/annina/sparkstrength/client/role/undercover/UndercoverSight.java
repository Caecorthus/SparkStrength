package annina.sparkstrength.client.role.undercover;

import dev.doctor4t.wathe.index.WatheBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client line of sight for the stage-2 highlight and thermal: three rays (head, chest, knees) with the camera shape,
 * so plain glass and panes never block, and a Wathe small glass door is looked through as well (it keeps a door's
 * collision shape). Results are cached per client tick, because outlines ask every frame for every player.
 * 二阶段高亮与热成像的客户端视线判定：三条射线（头、胸、膝），使用镜头形状，普通玻璃与玻璃板不挡；Wathe 小玻璃门也能看穿
 * （它保留了门的碰撞形状）。结果按客户端 tick 缓存，因为描边每帧都会询问每个玩家。
 */
public final class UndercoverSight {
    private static final int MAX_GLASS_DOORS = 4;
    private static final double STEP_PAST_GLASS = 0.05;
    /**
     * Client thread only. MinecraftClient#hasOutline, and so this check, is also asked from other threads (Entity
     * Culling's CullThread asks it for every rendered entity), and a plain HashMap shared with them threw
     * ConcurrentModificationException in computeIfAbsent (T-0017).
     * 仅限客户端线程。MinecraftClient#hasOutline（因而本判定）也会在其他线程被询问（Entity Culling 的 CullThread 会对每个
     * 渲染实体询问），与它们共用普通 HashMap 会在 computeIfAbsent 中抛出 ConcurrentModificationException（T-0017）。
     */
    private static final Map<UUID, Boolean> CACHE = new HashMap<>();
    private static long cacheTick = Long.MIN_VALUE;

    private UndercoverSight() {
    }

    public static boolean canSee(PlayerEntity viewer, PlayerEntity target) {
        if (!MinecraftClient.getInstance().isOnThread()) {
            // Another thread only asks (a culler deciding whether to skip its occlusion test); the client thread still
            // decides every outline it draws. Fail closed without rays or the cache: not in sight, so no outline.
            // 其他线程只是询问（剔除线程决定是否跳过遮挡测试）；实际绘制的描边仍由客户端线程决定。不发射线、不碰缓存，
            // 失败即关闭：视为不在视线内，因此不描边。
            return false;
        }
        World world = viewer.getWorld();
        long tick = world.getTime();
        if (tick != cacheTick) {
            CACHE.clear();
            cacheTick = tick;
        }
        // get, compute, put: unlike computeIfAbsent, a nested canSee reached from inside the rays can't throw here.
        // 先查、再算、后存：与 computeIfAbsent 不同，射线内部嵌套到达的 canSee 不会在这里抛异常。
        UUID key = target.getUuid();
        Boolean cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean visible = compute(viewer, target);
        CACHE.put(key, visible);
        return visible;
    }

    private static boolean compute(PlayerEntity viewer, PlayerEntity target) {
        Vec3d eye = viewer.getCameraPosVec(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
        double height = target.getHeight();
        Vec3d base = target.getPos();
        double[] samples = {height * 0.9, height * 0.6, height * 0.25};
        for (double y : samples) {
            if (clear(viewer, eye, base.add(0.0, y, 0.0))) {
                return true;
            }
        }
        return false;
    }

    private static boolean clear(PlayerEntity viewer, Vec3d from, Vec3d to) {
        Vec3d start = from;
        Vec3d direction = to.subtract(from).normalize();
        for (int pass = 0; pass <= MAX_GLASS_DOORS; pass++) {
            BlockHitResult hit = viewer.getWorld().raycast(new RaycastContext(start, to,
                    RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, viewer));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            BlockState state = viewer.getWorld().getBlockState(hit.getBlockPos());
            if (!state.isOf(WatheBlocks.SMALL_GLASS_DOOR)) {
                return false;
            }
            start = hit.getPos().add(direction.multiply(STEP_PAST_GLASS));
            if (start.squaredDistanceTo(from) >= to.squaredDistanceTo(from)) {
                return true;
            }
        }
        return false;
    }
}
