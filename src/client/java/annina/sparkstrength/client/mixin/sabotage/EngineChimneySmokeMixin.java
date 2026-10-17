package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import dev.doctor4t.wathe.client.particle.BlackSmokeParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Chimney smoke streams back at up to 1 block per tick because the train is moving. Its top speed follows the train,
 * which also slows the puffs already in the air: a stopped train's smoke rises straight up.
 * 烟囱黑烟因为列车行驶而以最高每 tick 1 格的速度向后飘。它的最高速度跟随列车，已经在空中的烟也会一起变慢：停下的列车，
 * 烟会直直往上冒。
 */
@Mixin(value = BlackSmokeParticle.class, remap = false)
public abstract class EngineChimneySmokeMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/MathHelper;clamp(DDD)D", remap = true), index = 2, remap = true)
    private double sparkstrength$followEngineMotion(double max) {
        return max * EngineTrainMotion.factor();
    }
}
