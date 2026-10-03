package annina.sparkstrength.client.item;

import annina.sparkstrength.SparkStrength;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Makes the lit flashlight's lens glow: faces of {@code item/flashlight_on} tagged with
 * {@link #GLOW_TINT_INDEX} are re-emitted with an emissive FRAPI material, so they stay
 * full-bright in total darkness. Purely visual and client-only; the server still only
 * toggles CUSTOM_MODEL_DATA (FlashlightItem.ON_MODEL_DATA) to select the "on" model.
 * 让开启状态手电筒的镜片发光：{@code item/flashlight_on} 中带 {@link #GLOW_TINT_INDEX}
 * 标记的面会以 FRAPI 自发光材质重新输出，在全黑环境下仍保持满亮度。仅为客户端视觉效果；
 * 服务端依旧只通过切换 CUSTOM_MODEL_DATA（FlashlightItem.ON_MODEL_DATA）选择开启模型。
 */
public final class FlashlightModelClient {
    /**
     * Override target of flashlight.json for custom_model_data = 1; baked as a dependency,
     * so it reaches the after-bake hook by resource id (never as a top-level model id).
     * flashlight.json 中 custom_model_data = 1 的覆盖模型；它作为依赖被烘焙，
     * 因此按资源 id（而非顶层模型 id）进入烘焙后钩子。
     */
    static final Identifier ON_MODEL = SparkStrength.id("item/flashlight_on");

    /**
     * Marker shared with flashlight.json: lens + reflector faces carry {@code "tintindex": 7}.
     * No ItemColors provider is registered for the flashlight, so vanilla (and FRAPI without
     * this wrapper) resolves it to white, i.e. the marker never tints anything.
     * 与 flashlight.json 约定的标记：镜片与反光杯面带 {@code "tintindex": 7}。手电筒未注册
     * ItemColors 提供器，原版（以及未包装的 FRAPI 路径）会将其解析为白色，因此该标记不会染色。
     */
    static final int GLOW_TINT_INDEX = 7;

    private FlashlightModelClient() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, bake) -> {
            if (model == null || !ON_MODEL.equals(bake.resourceId())) {
                return model;
            }
            // Without an active FRAPI renderer, keep the plain baked model (lens just isn't emissive).
            // 没有可用的 FRAPI 渲染器时保留普通烘焙模型（镜片只是不自发光）。
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) {
                return model;
            }
            RenderMaterial glow = renderer.materialFinder()
                    .emissive(true)
                    .disableDiffuse(true)
                    .ambientOcclusion(TriState.FALSE)
                    .find();
            return new GlowingLensModel(model, glow);
        }));
    }

    /**
     * Forwards everything to the baked JSON model but routes item rendering through FRAPI
     * (Indigo or Sodium) so a quad transform can swap the material of the tagged faces.
     * 其余行为全部转发给烘焙后的 JSON 模型，但让物品渲染走 FRAPI（Indigo 或 Sodium），
     * 以便通过四边形变换替换被标记面的材质。
     */
    private static final class GlowingLensModel extends ForwardingBakedModel {
        private final RenderContext.QuadTransform glowTransform;

        private GlowingLensModel(BakedModel wrapped, RenderMaterial glow) {
            this.wrapped = wrapped;
            this.glowTransform = quad -> {
                if (quad.colorIndex() == GLOW_TINT_INDEX) {
                    quad.material(glow);
                    // Drop the marker so no colour provider can ever tint the lens.
                    // 清除标记，确保任何颜色提供器都不会给镜片染色。
                    quad.colorIndex(-1);
                }
                return true;
            };
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
            context.pushTransform(glowTransform);
            try {
                super.emitItemQuads(stack, randomSupplier, context);
            } finally {
                context.popTransform();
            }
        }
    }
}
