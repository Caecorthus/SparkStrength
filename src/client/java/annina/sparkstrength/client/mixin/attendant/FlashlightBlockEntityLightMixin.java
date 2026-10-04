package annina.sparkstrength.client.mixin.attendant;

import annina.sparkstrength.client.role.attendant.flashlight.FlashlightLights;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lights block entities (Wathe doors, chests, signs...) caught in a flashlight beam. The private static
 * {@code render(BlockEntityRenderer, BlockEntity, float, MatrixStack, VertexConsumerProvider)} is where every
 * in-world block entity gets its packed light ({@code WorldRenderer.getLightmapCoordinates}) before its renderer
 * runs; Sodium still dispatches through it. Only the light argument changes, and only its block-light half; the
 * multi-argument form reads the block entity and tick delta straight from the call. Client-only and visual.
 * 让被手电光束照到的方块实体（Wathe 门、箱子、告示牌等）变亮。私有静态 render 是所有世界内方块实体在渲染器执行前
 * 获取打包光照的位置，Sodium 也经由它分发。只修改光照参数中的方块光部分；多参数形式直接从调用中读取方块实体与
 * tick 插值。仅客户端视觉效果。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class FlashlightBlockEntityLightMixin {
    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"
            ),
            index = 4
    )
    private static int sparkstrength$applyFlashlightBlockLight(
            BlockEntity blockEntity,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int packedLight,
            int overlay
    ) {
        int blockLight = LightmapTextureManager.getBlockLightCoordinates(packedLight);
        int boosted = FlashlightLights.boostBlockLightAt(blockEntity.getPos(), tickDelta, blockLight);
        if (boosted == blockLight) {
            return packedLight;
        }
        return LightmapTextureManager.pack(boosted, LightmapTextureManager.getSkyLightCoordinates(packedLight));
    }
}
