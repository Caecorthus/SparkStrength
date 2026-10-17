package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.Wathe;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wathe's roof wind sway (only read by its camera shake) stops below half speed; it does not scale with the shake
 * amplitude, so it needs its own cut-off.
 * Wathe 的车顶风吹晃动（只有镜头晃动会读取）在低于半速时停止；它不随晃动振幅缩放，所以需要单独截断。
 */
@Mixin(value = Wathe.class, remap = false)
public abstract class EngineWindExposureMixin {
    @ModifyReturnValue(method = "isExposedToWind", at = @At("RETURN"))
    private static boolean sparkstrength$calmWhenSlow(boolean exposed, @Local(argsOnly = true) Entity entity) {
        return exposed && (entity == null || !entity.getWorld().isClient() || EngineTrainMotion.windAllowed());
    }
}
