package annina.sparkstrength.entity;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.role.perfumer.CoolingOilService;
import annina.sparkstrength.role.perfumer.PerfumerKitService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Cooling Oil vial: shatters on any block or entity collision; all effects live in {@link CoolingOilService}.
 * 风油精药瓶：碰到任何方块或实体即碎裂；效果全部由 {@link CoolingOilService} 处理。
 */
public final class CoolingOilEntity extends ThrownItemEntity {
    // Server-only round binding, never tracked or saved: a vial from an old round fails closed.
    // 仅服务端的回合绑定，不同步也不存档：旧回合的药瓶一律失效。
    private @Nullable UUID roundId;

    public CoolingOilEntity(EntityType<? extends CoolingOilEntity> entityType, World world) {
        super(entityType, world);
    }

    public CoolingOilEntity(World world, LivingEntity owner, UUID roundId) {
        super(SparkStrengthEntities.coolingOil(), owner, world);
        this.roundId = roundId;
    }

    @Override
    protected Item getDefaultItem() {
        return SparkStrengthItems.coolingOil();
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
        // Vanilla lets a projectile hit its owner once it leaves their hitbox; ours pass through the thrower.
        // 原版投掷物离开投掷者碰撞箱后可以击中投掷者；这里始终穿过投掷者。
        return super.canHit(entity) && !isOwner(entity);
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!getWorld().isClient() && !isRemoved()) {
            CoolingOilService.shatter(this, hitResult);
            discard();
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        roundId = null;
    }
}
