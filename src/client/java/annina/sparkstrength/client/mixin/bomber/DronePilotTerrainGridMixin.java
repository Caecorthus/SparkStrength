package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Render grid while the camera looks through a drone (unlimited range): vanilla {@code setupTerrain} centres the
 * built-chunk grid ({@code BuiltChunkStorage.updateCameraPosition}) on {@code client.player}, so past render distance
 * from the body nothing would draw (terrain, nor entities, which need ready sections). The three player coordinate
 * reads return the camera position instead. The server re-centres the chunk view on the drone separately.
 * Pass-through unless piloting.
 * 镜头通过无人机观看时的渲染网格（无限范围）：原版 setupTerrain 以 client.player 为中心布置区块网格，飞出本体渲染距离后将什么都
 * 画不出（地形，以及需要就绪区块段的实体）。三处玩家坐标读取改为镜头位置。服务器另行把区块视野重新以无人机为中心。未驾驶时不做改动。
 *
 * <p>Sodium split: Sodium {@code @Overwrite}s {@code setupTerrain}, and Mixin refuses to inject into an overwritten
 * method at apply time even with {@code require = 0} (client crash on launch). This class therefore holds only the
 * {@code setupTerrain} injectors, and {@code SparkStrengthClientMixinPlugin} skips it when Sodium is loaded. Sodium's
 * own renderer already builds and culls sections around the camera, which is what these injectors emulate.
 * {@code require = 0} only covers a mod that reshapes the method without overwriting it (far terrain is lost, no
 * crash).
 * Sodium 拆分：Sodium 以 @Overwrite 替换 setupTerrain，Mixin 在应用阶段拒绝注入被覆盖的方法，require = 0 也无效（客户端启动即崩溃）。
 * 因此本类只包含 setupTerrain 的注入，Sodium 存在时由 SparkStrengthClientMixinPlugin 跳过。Sodium 自身的渲染器本就以镜头为中心构建
 * 和剔除区块段，正是这些注入所模拟的行为。require = 0 只应对改写但未覆盖该方法的模组（失去远处地形，不会崩溃）。</p>
 */
@Mixin(WorldRenderer.class)
public abstract class DronePilotTerrainGridMixin {
    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"), require = 0)
    private double sparkstrength$droneGridCenterX(double original, @Local(argsOnly = true) Camera camera) {
        return DronePilotClient.renderGridCenter(original, camera, 0);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"), require = 0)
    private double sparkstrength$droneGridCenterY(double original, @Local(argsOnly = true) Camera camera) {
        return DronePilotClient.renderGridCenter(original, camera, 1);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"), require = 0)
    private double sparkstrength$droneGridCenterZ(double original, @Local(argsOnly = true) Camera camera) {
        return DronePilotClient.renderGridCenter(original, camera, 2);
    }
}
