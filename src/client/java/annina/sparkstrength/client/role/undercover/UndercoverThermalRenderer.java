package annina.sparkstrength.client.role.undercover;

import annina.sparkstrength.SparkStrength;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

import java.io.IOException;

/**
 * Owns the private thermal {@link PostEffectProcessor} (ironbow grade; magenta outlines become white heat), separate
 * from GameRenderer's shared post slot, like the Cooling Oil blur. Lazy-loaded on the first thermal frame, closed when
 * thermal turns off; a load failure is logged once and thermal keeps its outlines only.
 * 持有私有的热成像后处理器（铁虹调色，品红描边变成白热），与风油精模糊一样不占用 GameRenderer 的共享槽位。
 * 首个热成像帧时加载，关闭热成像即释放；加载失败只记一次日志，热成像只保留描边。
 */
public final class UndercoverThermalRenderer {
    private static final Identifier SHADER = SparkStrength.id("shaders/post/undercover_thermal.json");

    private static PostEffectProcessor processor;
    private static Framebuffer processorTarget;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean loadFailed;

    private UndercoverThermalRenderer() {
    }

    /** Render thread, after the world and before the HUD. / 渲染线程：世界之后、HUD 之前。 */
    public static void render(MinecraftClient client, float tickDelta) {
        if (!UndercoverClient.isThermalOn()) {
            closeProcessor();
            return;
        }
        Framebuffer framebuffer = client.getFramebuffer();
        PostEffectProcessor activeProcessor = ensureProcessor(client, framebuffer);
        if (activeProcessor == null) {
            return;
        }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            activeProcessor.render(tickDelta);
        } finally {
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
                SparkStrength.LOGGER.warn("Unable to load the Undercover thermal shader; outlines only", exception);
                closeProcessor();
                loadFailed = true;
                return null;
            }
            processorTarget = framebuffer;
            processorWidth = -1;
            processorHeight = -1;
        }
        if (processorWidth != framebuffer.textureWidth || processorHeight != framebuffer.textureHeight) {
            processor.setupDimensions(framebuffer.textureWidth, framebuffer.textureHeight);
            processorWidth = framebuffer.textureWidth;
            processorHeight = framebuffer.textureHeight;
        }
        return processor;
    }

    /** Resource reload, disconnect and shutdown: allows a fresh load attempt. / 资源重载、断开连接与关闭客户端：允许重新加载。 */
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
