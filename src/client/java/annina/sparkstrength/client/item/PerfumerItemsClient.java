package annina.sparkstrength.client.item;

import annina.sparkstrength.SparkStrengthEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Flying renderers for the thrown Perfumer bottles: each entity renders its own item stack.
 * 投掷中的调香师瓶子渲染器：实体直接渲染自身物品。
 */
public final class PerfumerItemsClient {
    private PerfumerItemsClient() {
    }

    public static void register() {
        EntityRendererRegistry.register(SparkStrengthEntities.coolingOil(), FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(SparkStrengthEntities.aromaOrb(), FlyingItemEntityRenderer::new);
    }
}
