package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.item.grenade.GrenadeBlastRules;
import annina.sparkstrength.item.grenade.GrenadeBlastService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(GrenadeEntity.class)
public abstract class GrenadeExplosionMixin {
    // Filter before Wathe's original kill call; preserve Traits' BombManiac redirect.
    // 在 Wathe 原始击杀调用前筛选候选，保留 Traits 的 BombManiac 重定向。
    @ModifyExpressionValue(
            method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1,
            allow = 1
    )
    private List<ServerPlayerEntity> sparkstrength$filterBlastTargets(List<ServerPlayerEntity> original) {
        GrenadeEntity grenade = (GrenadeEntity) (Object) this;
        return GrenadeBlastService.filterVictims((ServerWorld) grenade.getWorld(), grenade, original,
                GrenadeBlastRules.WATHE_BLAST_RADIUS);
    }
}
