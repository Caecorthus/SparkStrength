package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.doctor4t.wathe.cca.TrainWorldComponent;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client's train time follows {@link EngineTrainMotion}, so the scenery (vanilla and Sodium) and the wheels slow
 * down, stop and start again smoothly while Wathe's real speed stays untouched. Only the three render readers call
 * this getter; the server world's instance is never changed.
 * 客户端的列车时间跟随 EngineTrainMotion，因此风景（原版与 Sodium）和车轮会平滑地减速、停下、再起步，而 Wathe 的真实速度
 * 保持不变。只有三处渲染代码会调用这个 getter；服务端世界的实例从不改变。
 */
@Mixin(value = TrainWorldComponent.class, remap = false)
public abstract class EngineTrainTimeMixin {
    @Shadow
    @Final
    private World world;

    @ModifyReturnValue(method = "getTime", at = @At("RETURN"))
    private float sparkstrength$followEngineMotion(float time) {
        return world != null && world.isClient() ? EngineTrainMotion.renderTime(time) : time;
    }
}
