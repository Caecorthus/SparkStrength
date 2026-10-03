package annina.sparkstrength.client.role.perfumer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Screen-space Perfumer overlays drawn under the vanilla HUD: Cooling Oil (mint tint, tear-light texture, squinting
 * eyelids) and Aroma (drifting lavender haze, violet edge glow). Purely cosmetic and local; textures that are
 * missing from the active resource packs are skipped instead of drawing the missing-texture checkerboard.
 * 绘制在原版 HUD 下层的调香师屏幕覆盖层：风油精（薄荷色调、泪光贴图、眯眼眼睑）与香薰（流动的淡紫香雾、
 * 紫色边缘光晕）。纯本地表现；当前资源包缺少的贴图直接跳过，不会画出缺失贴图的紫黑格。
 */
public final class PerfumerOverlayRenderer {
    private static final Identifier COOLING_OIL_TEXTURE = SparkStrength.id("textures/gui/perfumer/cooling_oil_overlay.png");
    private static final Identifier AROMA_HAZE_TEXTURE = SparkStrength.id("textures/gui/perfumer/aroma_haze.png");
    private static final Identifier VIGNETTE_TEXTURE = Identifier.ofVanilla("textures/misc/vignette.png");
    private static final int TEXTURE_SIZE = 256;

    private static final int MINT_RGB = 0x2A9D6E;
    private static final float MINT_TINT_ALPHA = 0.12F;
    private static final float TEAR_TEXTURE_ALPHA = 0.85F;
    private static final int EYELID_RGB = 0x0B0F0D;
    private static final float EYELID_ALPHA = 0.96F;
    private static final int EYELID_FEATHER = 18;

    private static final float HAZE_ALPHA = 0.35F;
    private static final float HAZE_SECOND_LAYER_ALPHA = 0.18F;
    private static final float HAZE_TILE_SIZE = 192.0F;
    private static final float VIOLET_RED = 0.62F;
    private static final float VIOLET_GREEN = 0.42F;
    private static final float VIOLET_BLUE = 0.95F;
    private static final float VIOLET_EDGE_ALPHA = 0.22F;

    private static final Map<Identifier, Boolean> TEXTURE_PRESENT = new HashMap<>();

    private PerfumerOverlayRenderer() {
    }

    /** Called at InGameHud.render HEAD on the render thread. / 在渲染线程的 InGameHud.render HEAD 调用。 */
    public static void render(DrawContext context, float tickDelta) {
        PerfumerClientEffects.OverlayState state = PerfumerClientEffects.overlayState(tickDelta);
        if (state == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        // Like the vanilla vignette: never write depth, or HUD layers drawn below z=0 (hotbar at -90) would fail
        // the depth test where these quads were drawn.
        // 与原版暗角一致：不写入深度，否则绘制在 z=0 以下的 HUD 层（快捷栏 z=-90）会在这些区域深度测试失败。
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            if (state.aromaStrength() > 0.0F) {
                // Effect-local time: the haze never jumps mid-effect. / 以效果自身计时，香雾不会中途跳变。
                renderAroma(context, client, width, height, state.aromaStrength(), state.aromaElapsedTicks());
            }
            if (state.coolingOilAlpha() > 0.0F) {
                renderCoolingOil(context, client, width, height, state.coolingOilAlpha(),
                        state.coolingOilElapsedTicks());
            }
        } finally {
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderCoolingOil(DrawContext context, MinecraftClient client, int width, int height,
                                         float alpha, float elapsedTicks) {
        context.fill(0, 0, width, height, argb(MINT_TINT_ALPHA * alpha, MINT_RGB));
        if (isTexturePresent(client, COOLING_OIL_TEXTURE)) {
            beginTextureBlend(false);
            context.setShaderColor(1.0F, 1.0F, 1.0F, TEAR_TEXTURE_ALPHA * alpha);
            context.drawTexture(COOLING_OIL_TEXTURE, 0, 0, width, height,
                    0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        renderEyelids(context, width, height, alpha, PerfumerRules.coolingOilEyelidCoverage(elapsedTicks));
    }

    /**
     * Top and bottom lids, each solid up to {@code coverage} of half the screen height with a feathered inner edge.
     * Float translation keeps the motion sub-pixel smooth at large GUI scales.
     * 上下眼睑各覆盖半屏高度的 {@code coverage}，内侧边缘柔化；用浮点平移保证大 GUI 缩放下运动平滑。
     */
    private static void renderEyelids(DrawContext context, int width, int height, float alpha, float coverage) {
        int solid = argb(EYELID_ALPHA * alpha, EYELID_RGB);
        int clear = argb(0.0F, EYELID_RGB);
        float edge = coverage * height / 2.0F;
        float halfFeather = EYELID_FEATHER / 2.0F;
        MatrixStack matrices = context.getMatrices();

        matrices.push();
        matrices.translate(0.0F, edge - halfFeather, 0.0F);
        context.fill(0, -height, width, 0, solid);
        context.fillGradient(0, 0, width, EYELID_FEATHER, solid, clear);
        matrices.pop();

        matrices.push();
        matrices.translate(0.0F, height - edge - halfFeather, 0.0F);
        context.fillGradient(0, 0, width, EYELID_FEATHER, clear, solid);
        context.fill(0, EYELID_FEATHER, width, EYELID_FEATHER + height, solid);
        matrices.pop();
    }

    private static void renderAroma(DrawContext context, MinecraftClient client, int width, int height,
                                    float strength, float time) {
        if (isTexturePresent(client, AROMA_HAZE_TEXTURE)) {
            // Two tiled layers drifting along slow circles in opposite directions read as a swirl.
            // 两层平铺香雾沿缓慢的圆周反向漂移，形成旋绕感。
            beginTextureBlend(false);
            drawHazeLayer(context, width, height, HAZE_ALPHA * strength,
                    time * 0.45F + 18.0F * (float) Math.sin(time * 0.045F),
                    time * 0.20F + 18.0F * (float) Math.cos(time * 0.045F), 1.0F);
            drawHazeLayer(context, width, height, HAZE_SECOND_LAYER_ALPHA * strength,
                    -time * 0.30F + 24.0F * (float) Math.cos(time * 0.03F),
                    time * 0.35F - 24.0F * (float) Math.sin(time * 0.03F), 1.6F);
        }
        if (isTexturePresent(client, VIGNETTE_TEXTURE)) {
            // Additive tint of vanilla's vignette mask (black centre, bright corners) gives a faint violet edge glow.
            // 以叠加方式给原版暗角遮罩（中心黑、四角亮）上色，形成淡紫色边缘光晕。
            beginTextureBlend(true);
            context.setShaderColor(VIOLET_RED, VIOLET_GREEN, VIOLET_BLUE, VIOLET_EDGE_ALPHA * strength);
            context.drawTexture(VIGNETTE_TEXTURE, 0, 0, width, height,
                    0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void drawHazeLayer(DrawContext context, int width, int height, float alpha,
                                      float scrollU, float scrollV, float tileScale) {
        float tile = HAZE_TILE_SIZE * tileScale;
        int regionWidth = Math.max(1, Math.round(width * TEXTURE_SIZE / tile));
        int regionHeight = Math.max(1, Math.round(height * TEXTURE_SIZE / tile));
        context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        // Resource textures default to GL_REPEAT, so UVs past the edge wrap around. / 资源贴图默认 GL_REPEAT，越界 UV 会循环。
        context.drawTexture(AROMA_HAZE_TEXTURE, 0, 0, width, height,
                floorMod(scrollU, TEXTURE_SIZE), floorMod(scrollV, TEXTURE_SIZE),
                regionWidth, regionHeight, TEXTURE_SIZE, TEXTURE_SIZE);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** GUI fills disable blending when they finish, so re-enable it before every immediate texture draw. / GUI 填充结束时会关闭混合，因此每次绘制贴图前都要重新开启。 */
    private static void beginTextureBlend(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
    }

    private static boolean isTexturePresent(MinecraftClient client, Identifier texture) {
        return TEXTURE_PRESENT.computeIfAbsent(texture,
                id -> client.getResourceManager().getResource(id).isPresent());
    }

    /** Resource reload: re-check texture availability. / 资源重载时重新检查贴图是否存在。 */
    public static void resetTextureCache() {
        TEXTURE_PRESENT.clear();
    }

    private static float floorMod(float value, int modulus) {
        float result = value % modulus;
        return result < 0.0F ? result + modulus : result;
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return ColorHelper.Argb.getArgb(a, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
