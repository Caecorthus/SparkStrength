package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.item.grenade.GrenadeBlastRules;
import annina.sparkstrength.item.grenade.GrenadeBlastService;
import annina.sparkstrength.role.bodyguard.AttackOriginScope;
import annina.sparkstrength.role.bomber.drone.DroneCombatService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    // Server only: the whole collision (every mod's injections included) runs as one of our own blasts, so SparkWitch's
    // HEAD SeekerDeviceHits.onBlast call does not break drones before Wathe's kills; our TAIL hook below does it after.
    // 仅服务端：整个碰撞流程（含所有模组的注入）作为我们的自有爆炸执行，使 SparkWitch 在 HEAD 调用的 SeekerDeviceHits.onBlast
    // 不会在 Wathe 击杀之前击毁无人机；改由下方 TAIL 钩子在之后处理。
    @WrapMethod(method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V")
    private void sparkstrength$guardOwnBlast(HitResult hitResult, Operation<Void> original) {
        GrenadeEntity grenade = (GrenadeEntity) (Object) this;
        if (grenade.getWorld() instanceof ServerWorld) {
            // The blast scope lets a Bodyguard's shield face the blast itself. / 爆炸作用域让保镖的盾按爆炸点判定方向。
            DroneCombatService.runOwnBlast(() -> AttackOriginScope.runBlast(grenade.getPos(), () -> original.call(hitResult)));
        } else {
            original.call(hitResult);
        }
    }

    // After Wathe's own kills and discard (TAIL, server only): Bomber drones in the blast break, attributed to the
    // thrower; bomb drones chain-detonate. A grenade that hits a drone directly explodes on it.
    // 在 Wathe 自身击杀与移除之后（TAIL，仅服务端）：爆炸范围内的炸弹客无人机被击毁并归属投掷者，炸弹无人机连锁引爆。
    // 直接命中无人机的手雷就地爆炸。
    @Inject(method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V", at = @At("TAIL"))
    private void sparkstrength$breakDronesInBlast(HitResult hitResult, CallbackInfo ci) {
        GrenadeEntity grenade = (GrenadeEntity) (Object) this;
        if (grenade.getWorld() instanceof ServerWorld world) {
            DroneCombatService.breakDronesInBlast(world, grenade, GrenadeBlastRules.WATHE_BLAST_RADIUS,
                    grenade.getOwner() instanceof ServerPlayerEntity thrower ? thrower : null);
        }
    }
}
