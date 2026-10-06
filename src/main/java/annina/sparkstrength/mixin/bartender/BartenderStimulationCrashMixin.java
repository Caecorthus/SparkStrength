package annina.sparkstrength.mixin.bartender;

import annina.sparkstrength.role.bartender.BartenderRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.effect.StimulationEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lightens vodka's crash: stamina still drops to 0 with exhaustion, but the Slowness II it used to add is gone.
 * removeStimulation's only status effect is that slowness.
 * 减轻伏特加副作用：体力仍归零并疲劳，但不再附加缓慢 II。removeStimulation 里唯一的状态效果就是该缓慢。
 */
@Mixin(value = StimulationEffect.class, remap = false)
public abstract class BartenderStimulationCrashMixin {
    @WrapOperation(
            method = "removeStimulation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z",
                    remap = true
            )
    )
    private static boolean sparkstrength$dropCrashSlowness(
            ServerPlayerEntity player, StatusEffectInstance slowness, Operation<Boolean> original
    ) {
        return BartenderRules.VODKA_CRASH_SLOWNESS && original.call(player, slowness);
    }
}
