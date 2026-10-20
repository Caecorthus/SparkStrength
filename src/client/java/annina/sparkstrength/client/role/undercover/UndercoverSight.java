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
    private static final Map<UUID, Boolean> CACHE = new HashMap<>();
    private static long cacheTick = Long.MIN_VALUE;

    private UndercoverSight() {
    }

    public static boolean canSee(PlayerEntity viewer, PlayerEntity target) {
        World world = viewer.getWorld();
        long tick = world.getTime();
        if (tick != cacheTick) {
            CACHE.clear();
            cacheTick = tick;
        }
        return CACHE.computeIfAbsent(target.getUuid(), uuid -> compute(viewer, target));
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
