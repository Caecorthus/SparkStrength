package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.doctor4t.wathe.cca.MapEnhancementsWorldComponent;
import dev.doctor4t.wathe.config.datapack.MapEnhancementsConfiguration;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wathe's camera shake fades with the train's motion and switches off once it has stopped.
 * Wathe 的镜头晃动随列车运动减弱，停稳后关闭。
 */
@Mixin(value = MapEnhancementsWorldComponent.class, remap = false)
public abstract class EngineCameraShakeMixin {
    @Shadow
    @Final
    private World world;

    @ModifyReturnValue(method = "getCameraShakeConfig", at = @At("RETURN"))
    private MapEnhancementsConfiguration.CameraShakeConfig sparkstrength$followEngineMotion(
            MapEnhancementsConfiguration.CameraShakeConfig config) {
        return world != null && world.isClient() ? EngineTrainMotion.shake(config) : config;
    }
}
