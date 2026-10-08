package annina.sparkstrength.client.mixin.spiritualist;

import annina.sparkstrength.client.role.spiritualist.SpiritPossessionClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Render grid while possessing a Wraith at any distance: vanilla {@code setupTerrain} centres the built-chunk grid on
 * {@code client.player}, so past render distance from the body nothing would draw. The three player coordinate reads
 * return the camera (the Wraith's eyes) instead, chained with the drone's {@code DronePilotTerrainGridMixin}. The
 * server re-centres the chunk view on the Wraith separately. Pass-through unless possessing.
 * 无视距离附身冤魂时的渲染网格：原版 setupTerrain 以 client.player 为中心布置区块网格，离开肉身渲染距离后将什么都画不出。三处
 * 玩家坐标读取改为镜头（冤魂视点）位置，与无人机的 DronePilotTerrainGridMixin 串联。服务器另行把区块视野以冤魂为中心。未附身时不做改动。
 *
 * <p>Sodium {@code @Overwrite}s {@code setupTerrain} (any injector there crashes on launch, even with
 * {@code require = 0}), so {@code SparkStrengthClientMixinPlugin} skips this class when Sodium is loaded; Sodium already
 * builds sections around the camera. / Sodium 以 @Overwrite 替换 setupTerrain（即使 require = 0 注入也会启动崩溃），因此
 * Sodium 存在时由 SparkStrengthClientMixinPlugin 跳过本类；Sodium 本就围绕镜头构建区块段。</p>
 */
@Mixin(WorldRenderer.class)
public abstract class SpiritPossessionTerrainGridMixin {
    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"), require = 0)
    private double sparkstrength$possessionGridCenterX(double original, @Local(argsOnly = true) Camera camera) {
        return SpiritPossessionClient.renderGridCenter(original, camera, 0);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"), require = 0)
    private double sparkstrength$possessionGridCenterY(double original, @Local(argsOnly = true) Camera camera) {
        return SpiritPossessionClient.renderGridCenter(original, camera, 1);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"), require = 0)
    private double sparkstrength$possessionGridCenterZ(double original, @Local(argsOnly = true) Camera camera) {
        return SpiritPossessionClient.renderGridCenter(original, camera, 2);
    }
}
