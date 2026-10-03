package annina.sparkstrength.role.perfumer;

import annina.sparkstrength.item.m67.M67RoundService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared server plumbing for the Perfumer kit: the use gate, the instant throw, round binding and the
 * impact point of a shattering vial.
 * 调香师道具的共用服务端逻辑：使用资格、即时投掷、回合绑定以及药瓶碎裂点。
 */
public final class PerfumerKitService {
    private static final String NOT_PERFUMER_KEY = "message.sparkstrength.perfumer.not_perfumer";
    /** Same tolerance ProjectileUtil.getCollision(Entity, Predicate) uses for entity hits. / 与原版投掷物实体判定容差一致。 */
    private static final double ENTITY_HIT_MARGIN = 0.3D;
    private static final double IMPACT_PULL_BACK = 0.2D;
    private static final double FLOOR_CLEARANCE = 0.2D;

    private PerfumerKitService() {
    }

    /**
     * Living, in-game, survival player whose REAL role is Perfumer. Every input is synced to clients, so
     * the client may run the same check for its swing prediction; the server result is authoritative.
     * 真实身份为调香师、仍在局内存活且处于生存模式的玩家。所有输入都会同步到客户端，
     * 客户端可用同一判定预测挥手动画；以服务端结果为准。
     */
    public static boolean canUse(PlayerEntity user) {
        return PerfumerRules.isPerfumer(GameWorldComponent.KEY.get(user.getWorld()).getRole(user))
                && GameFunctions.isPlayerPlayingAndAlive(user)
                && GameFunctions.isPlayerAliveAndSurvival(user);
    }

    public static TypedActionResult<ItemStack> deny(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient()) {
            user.sendMessage(Text.translatable(NOT_PERFUMER_KEY).formatted(Formatting.GRAY), true);
        }
        return TypedActionResult.fail(stack);
    }

    /** Null when no Wathe round is live (e.g. STOPPING); throws are refused then. / 无进行中的回合时为 null，此时拒绝投掷。 */
    public static @Nullable UUID currentRoundId(ServerWorld world) {
        return M67RoundService.currentRoundId(world);
    }

    /**
     * Read-only reuse of the M67 round identity, so vials thrown in one round never act in the next.
     * 只读复用 M67 的回合标识，上一局投出的药瓶不会在下一局生效。
     */
    public static boolean isCurrentRound(World world, @Nullable UUID roundId) {
        return roundId != null && world instanceof ServerWorld serverWorld
                && M67RoundService.isCurrentRound(serverWorld, roundId);
    }

    /**
     * Spawns an already-constructed vial like a splash potion: no cooldown, one item consumed on success.
     * 像喷溅药水一样投出已构造好的药瓶：无冷却，成功后消耗一个。
     */
    public static boolean launch(ServerPlayerEntity player, ItemStack stack, ThrownItemEntity vial, Identifier itemId) {
        ServerWorld world = player.getServerWorld();
        vial.setItem(stack.copyWithCount(1));
        vial.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F,
                PerfumerRules.THROW_SPEED, PerfumerRules.THROW_DIVERGENCE);
        if (!world.spawnEntity(vial)) {
            vial.discard();
            return false;
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SPLASH_POTION_THROW,
                SoundCategory.PLAYERS, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        GameRecordManager.recordItemUse(player, itemId, null, null);
        return true;
    }

    /**
     * Where the vial actually breaks. ThrownEntity reports a hit one tick early (before it moves), up to a
     * full throw-speed short of the surface, so step to the real impact and then back along the ray that
     * was just proven clear: the burst origin then never sits inside or on the struck block, which would
     * make the grenade cover check reject every victim.
     * 药瓶真正碎裂的位置。原版投掷物在移动前就报告命中，可能离表面还差一整格速度；
     * 因此先取真实撞击点，再沿刚判定为无阻挡的射线后退一点，保证爆点不在被撞方块内部或表面，
     * 否则手雷遮挡判定会排除所有目标。
     */
    public static Vec3d impactPoint(ProjectileEntity vial, HitResult hit) {
        Vec3d start = vial.getPos();
        Vec3d impact;
        if (hit instanceof EntityHitResult entityHit) {
            Vec3d end = start.add(vial.getVelocity());
            impact = entityHit.getEntity().getBoundingBox().expand(ENTITY_HIT_MARGIN).raycast(start, end).orElse(start);
        } else {
            impact = hit.getPos();
        }
        Vec3d back = start.subtract(impact);
        double length = back.length();
        return length <= IMPACT_PULL_BACK ? start : impact.add(back.multiply(IMPACT_PULL_BACK / length));
    }

    /**
     * Lets the splash settle like a liquid: drops the origin straight down onto the floor beneath it, at most
     * {@code maxDrop}, along a segment a collider raycast has just proven clear. The shared grenade judgment
     * measures its radius from players' feet and grenades always come to rest on the floor, so a vial that
     * broke on a wall or ceiling would otherwise reach almost nobody. With no floor in reach the point is kept.
     * 让溅射像液体一样落下：沿碰撞射线刚确认无阻挡的竖直线段，把爆点下移到下方地面（最多 maxDrop）。
     * 共用手雷判定按玩家脚底计算半径，而手雷总会停在地面上；若不下移，撞到墙或天花板的药瓶几乎溅不到人。
     * 够不到地面时保留原位置。
     */
    public static Vec3d settleToFloor(World world, ProjectileEntity vial, Vec3d origin, double maxDrop) {
        BlockHitResult floor = world.raycast(new RaycastContext(origin, origin.add(0.0D, -maxDrop, 0.0D),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, vial));
        if (floor.getType() != HitResult.Type.BLOCK) {
            return origin;
        }
        double settledY = floor.getPos().y + FLOOR_CLEARANCE;
        return settledY < origin.y ? new Vec3d(origin.x, settledY, origin.z) : origin;
    }

    /** Moves the vial so its box centre (the blast origin) is {@code center}. / 移动药瓶，使碰撞箱中心（爆点）位于 center。 */
    public static void centerOn(ProjectileEntity vial, Vec3d center) {
        vial.setPosition(center.x, center.y - vial.getHeight() / 2.0D, center.z);
    }
}
