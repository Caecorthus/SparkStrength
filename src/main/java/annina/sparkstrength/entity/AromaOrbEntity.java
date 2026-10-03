package annina.sparkstrength.entity;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.role.perfumer.AromaService;
import annina.sparkstrength.role.perfumer.PerfumerKitService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Aroma Orb: acts only on a direct player hit and shatters on any collision; effects live in {@link AromaService}.
 * 香薰：只在直接砸中玩家时生效，任何碰撞都会碎裂；效果由 {@link AromaService} 处理。
 */
public final class AromaOrbEntity extends ThrownItemEntity {
    // Server-only round binding, never tracked or saved: an orb from an old round fails closed.
    // 仅服务端的回合绑定，不同步也不存档：旧回合的香薰一律失效。
    private @Nullable UUID roundId;

    public AromaOrbEntity(EntityType<? extends AromaOrbEntity> entityType, World world) {
        super(entityType, world);
    }

    public AromaOrbEntity(World world, LivingEntity owner, UUID roundId) {
        super(SparkStrengthEntities.aromaOrb(), owner, world);
        this.roundId = roundId;
    }

    @Override
    protected Item getDefaultItem() {
        return SparkStrengthItems.aromaOrb();
    }

    public @Nullable UUID roundId() {
        return roundId;
    }

    public boolean isThrower(Entity entity) {
        return isOwner(entity);
    }

    @Override
    public void tick() {
        if (!getWorld().isClient() && !PerfumerKitService.isCurrentRound(getWorld(), roundId)) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected boolean canHit(Entity entity) {
        // Vanilla lets a projectile hit its owner once it leaves their hitbox; the thrower is never a target.
        // 原版投掷物离开投掷者碰撞箱后可以击中投掷者；这里投掷者永远不是目标。
        return super.canHit(entity) && !isOwner(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!getWorld().isClient() && entityHitResult.getEntity() instanceof ServerPlayerEntity target) {
            AromaService.onPlayerHit(this, target);
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!getWorld().isClient() && !isRemoved()) {
            AromaService.shatter(this, hitResult);
            discard();
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        roundId = null;
    }
}
