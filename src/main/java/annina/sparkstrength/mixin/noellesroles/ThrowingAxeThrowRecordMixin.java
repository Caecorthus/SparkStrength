package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.record.AchievementRecords;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.agmas.noellesroles.entity.ThrowingAxeEntity;
import org.agmas.noellesroles.item.ThrowingAxeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Achievement record (S13): a Bandit throwing axe throw, with the spawned axe's uuid as the throw id that every hit of
 * the same axe repeats (ThrowingAxeHitRecordMixin). Wraps the one spawnEntity call in NoellesRoles' server branch of
 * {@code onStoppedUsing}; the spawn and its result are unchanged.
 * 成就记录（S13）：强盗飞斧的一次投掷，以生成的飞斧实体 UUID 作为投掷编号，同一把飞斧的每次命中都带同一编号
 * （见 ThrowingAxeHitRecordMixin）。包装 NoellesRoles 在 onStoppedUsing 服务端分支中唯一的 spawnEntity 调用；生成与其返回值不变。
 */
@Mixin(ThrowingAxeItem.class)
public abstract class ThrowingAxeThrowRecordMixin {
    @WrapOperation(
            method = "onStoppedUsing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"),
            require = 1,
            allow = 1
    )
    private boolean sparkstrength$recordAxeThrow(World world, Entity entity, Operation<Boolean> original) {
        boolean spawned = original.call(world, entity);
        if (spawned && entity instanceof ThrowingAxeEntity axe && axe.getOwner() instanceof ServerPlayerEntity thrower) {
            AchievementRecords.throwingAxeThrow(thrower, axe.getUuid());
        }
        return spawned;
    }
}
