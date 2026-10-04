package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla raycasts the crosshair target from the camera entity, i.e. from the drone while piloting. TAIL replaces it
 * with a miss so nothing next to the drone can be attacked, used, block-outlined or picked as a target by add-on
 * abilities from the drone's point of view. Untouched unless the local player pilots a drone.
 * 原版从镜头实体（驾驶时即无人机）射线检测准星目标。TAIL 将其替换为未命中，避免以无人机视角攻击、使用、描边方块或被附属模组技能
 * 选中无人机旁的目标。未驾驶时不做改动。
 */
@Mixin(GameRenderer.class)
public abstract class DronePilotTargetMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"))
    private void sparkstrength$noDroneCrosshairTarget(float tickDelta, CallbackInfo ci) {
        DronePilotClient.clearCrosshairTarget(this.client);
    }
}
