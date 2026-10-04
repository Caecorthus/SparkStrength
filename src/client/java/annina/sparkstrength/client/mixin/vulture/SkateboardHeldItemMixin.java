package annina.sparkstrength.client.mixin.vulture;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.role.vulture.SkateboardRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the board is drawn under the rider's feet, the first-person hand holding it draws nothing, so the board is
 * never seen twice. Skips only that hand's skateboard pass; other items and the other hand render normally.
 * 滑板绘制在骑手脚下时，第一人称中持滑板的手不再绘制，避免同时看到两块滑板。只跳过该手的滑板绘制；其他物品与另一只手正常渲染。
 */
@Mixin(HeldItemRenderer.class)
public abstract class SkateboardHeldItemMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$hideRiddenSkateboard(
            AbstractClientPlayerEntity player,
            float tickDelta,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (item.isOf(SparkStrengthItems.skateboard()) && SkateboardRenderer.standsOnBoard(player)) {
            ci.cancel();
        }
    }
}
