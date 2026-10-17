package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.cca.TrainWorldComponent;
import dev.doctor4t.wathe.client.render.block_entity.WheelBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The wheels add the block-entity tickDelta, not {@code getTickDelta(true)}; the two differ under {@code /tick freeze},
 * so their train time is taken with the same tickDelta they add, or a stopped wheel would rock back and forth.
 * 车轮加的是方块实体的 tickDelta，不是 getTickDelta(true)；两者在 /tick freeze 下不同，所以按车轮自己加的那个 tickDelta 取列车
 * 时间，否则停下的车轮会来回摆。
 */
@Mixin(value = WheelBlockEntityRenderer.class, remap = false)
public abstract class EngineWheelTimeMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/TrainWorldComponent;getTime()F"))
    private float sparkstrength$sameTickDelta(TrainWorldComponent train, Operation<Float> original,
                                              @Local(argsOnly = true) float tickDelta) {
        float time = original.call(train);
        return EngineTrainMotion.engaged() ? EngineTrainMotion.renderTime(time, tickDelta) : time;
    }
}
