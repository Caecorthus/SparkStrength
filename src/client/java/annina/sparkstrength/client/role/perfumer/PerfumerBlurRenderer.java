package annina.sparkstrength.client.role.perfumer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

import java.io.IOException;

/**
 * Owns a private Cooling Oil blur {@link PostEffectProcessor}, separate from GameRenderer's shared post-processor slot
 * (NoellesRoles Spiritualist) and from the SparkTraits/SparkWitch grayscale processors. Each processor reads and
 * writes the main framebuffer in turn, so they stack in any order. Lazy-loaded on the first blurred frame, closed
 * when the effect ends; a load failure is logged once and the victim keeps the HUD overlay only.
 * 持有私有的风油精模糊后处理器，不占用 GameRenderer 的共享后处理槽位（NoellesRoles 通灵者），也独立于
 * SparkTraits/SparkWitch 的灰阶处理器。各处理器依次读写主帧缓冲，因此任意顺序都能叠加。首个模糊帧时才加载，
 * 效果结束即关闭；加载失败只记录一次日志，受害者仅保留 HUD 覆盖层。
 */
public final class PerfumerBlurRenderer {
    private static final Identifier SHADER = SparkStrength.id("shaders/post/perfumer_blur.json");
    private static final float MIN_VISIBLE_RADIUS = 0.5F;

    private static PostEffectProcessor processor;
    private static Framebuffer processorTarget;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean loadFailed;

    private PerfumerBlurRenderer() {
    }

    /** Render thread, after the world (and vanilla's post effect) and before the HUD. / 渲染线程：世界与原版后处理之后、HUD 之前。 */
    public static void render(MinecraftClient client, float tickDelta) {
        Framebuffer framebuffer = client.getFramebuffer();
        float radius = PerfumerClientEffects.coolingOilBlurRadius(tickDelta)
                * PerfumerRules.coolingOilBlurResolutionScale(framebuffer.textureHeight);
        // box_blur rounds the radius, so anything below half a texel is a no-op. / box_blur 会取整，半个纹素以下无效果。
        if (radius < MIN_VISIBLE_RADIUS) {
            closeProcessor();
            return;
        }
        PostEffectProcessor activeProcessor = ensureProcessor(client, framebuffer);
        if (activeProcessor == null) {
            return;
        }
        activeProcessor.setUniforms("Radius", radius);
        // Same state reset vanilla performs before its own post effect. / 与原版运行自身后处理前的状态重置一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            activeProcessor.render(tickDelta);
        } finally {
            // The last pass unbinds the main target; rebind it for the HUD. / 最后一个 pass 会解绑主帧缓冲，为 HUD 重新绑定。
            framebuffer.beginWrite(false);
        }
    }

    private static PostEffectProcessor ensureProcessor(MinecraftClient client, Framebuffer framebuffer) {
        if (loadFailed || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            return null;
        }
        if (processor != null && processorTarget != framebuffer) {
            closeProcessor();
        }
        if (processor == null) {
            try {
                processor = new PostEffectProcessor(
                        client.getTextureManager(), client.getResourceManager(), framebuffer, SHADER);
            } catch (IOException | RuntimeException exception) {
                SparkStrength.LOGGER.warn("Unable to load Perfumer Cooling Oil blur shader; using overlay only",
                        exception);
                closeProcessor();
                loadFailed = true;
                return null;
            }
            processorTarget = framebuffer;
            processorWidth = -1;
            processorHeight = -1;
        }
        if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
            // Window resize: resize the swap target and projection in place, like vanilla's onResized.
            // 窗口尺寸变化：像原版 onResized 一样原地调整交换目标与投影。
            processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            processorWidth = framebuffer.textureWidth;
            processorHeight = framebuffer.textureHeight;
        }
        return processor;
    }

    /** Render thread: resource reload, disconnect and client shutdown. Allows a fresh load attempt. / 渲染线程：资源重载、断开连接与关闭客户端时调用，允许重新尝试加载。 */
    public static void close() {
        closeProcessor();
        loadFailed = false;
    }

    private static void closeProcessor() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        processorWidth = -1;
        processorHeight = -1;
    }
}
