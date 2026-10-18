package annina.sparkstrength.mixin.sabotage;

import annina.sparkstrength.sabotage.BlackoutNightVision;
import annina.sparkstrength.sabotage.LightsSabotageService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a sabotage outage the darkness comes from the lamps and the client-side sky-light block, not from Blindness.
 * Only the Blindness call itself is skipped: Wathe has already asked {@code BlackoutEffect.BEFORE} by then, so the
 * listeners that grant Night Vision (Impostor, Wind Spirit, a Coroner's Veteran disguise ...) still run, and killers
 * and Veterans keep their Night Vision. The action bar shows the repair hint instead of Wathe's countdown.
 * 破坏造成的熄灯期间，黑暗来自熄灭的灯与客户端屏蔽的天空光照，而不是失明。只跳过施加失明这一步：此时 Wathe 已经询问过
 * BlackoutEffect.BEFORE，所以发放夜视的监听器（内鬼、风灵、验尸官伪装的老兵……）照常执行，杀手与老兵也照常获得夜视。
 * 动作栏显示维修提示，代替 Wathe 的倒计时。
 *
 * <p>The Undercover takes Wathe's killer branch in every blackout, sabotage or not (see {@link BlackoutNightVision}).
 * 卧底在任何熄灯中都走 Wathe 的杀手分支，不论是否由破坏引起（见 BlackoutNightVision）。</p>
 */
@Mixin(value = WorldBlackoutComponent.class, remap = false)
public abstract class SabotageBlackoutEffectsMixin {
    @WrapOperation(method = "applyBlackoutEffects", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z",
            remap = true))
    private boolean sparkstrength$skipSabotageBlindness(ServerPlayerEntity player, StatusEffectInstance effect,
                                                        Operation<Boolean> original,
                                                        @Local(argsOnly = true) ServerWorld world) {
        if (effect.getEffectType() == StatusEffects.BLINDNESS && LightsSabotageService.suppressesBlindness(world)) {
            return false;
        }
        return original.call(player, effect);
    }

    /*
     * The killer check right before is SparkFactionAPI's @Redirect, so this sits on the Veteran check that shares its
     * Night Vision branch; it only runs for players that check already turned down.
     * 前面的杀手判定已被 SparkFactionAPI 的 @Redirect 占用，所以挂在共用同一夜视分支的老兵判定上；只有杀手判定为否的玩家
     * 才会走到这里。
     */
    @ModifyExpressionValue(method = "applyBlackoutEffects", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isRole(Ljava/util/UUID;Ldev/doctor4t/wathe/api/Role;)Z"))
    private boolean sparkstrength$undercoverSeesLikeKiller(boolean veteran, @Local ServerPlayerEntity player) {
        return veteran || BlackoutNightVision.seesLikeKiller(player);
    }

    @Inject(method = "sendBlackoutCountdown", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$repairHint(ServerWorld world, CallbackInfo ci) {
        if (LightsSabotageService.replacesCountdown(world)) {
            ci.cancel();
        }
    }
}
