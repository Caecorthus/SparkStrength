package annina.sparkstrength.client.role.jester;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.jester.JesterMomentRules;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.agmas.noellesroles.jester.JesterPlayerComponent;

import java.io.IOException;

/**
 * The Jester's screen greys 10% per Jester Moment kill, up to 50%, like SparkTraits Depression's grayscale. The kill
 * count is NoellesRoles' own, which it syncs to the Jester. Owns a private vanilla {@code color_convolve}
 * {@link PostEffectProcessor} (its Saturation uniform = 1 - grayscale), separate from GameRenderer's shared slot and
 * from the other grayscale/blur processors; each reads and writes the main framebuffer in turn, so they stack.
 * 小丑在小丑时刻中每击杀一人，屏幕灰度增加 10%，最高 50%，效果类似 SparkTraits 抑郁的灰阶。击杀数取自 NoellesRoles
 * 同步给小丑本人的计数。持有私有的原版 {@code color_convolve} 后处理器（Saturation = 1 - 灰度），独立于 GameRenderer
 * 的共享槽位以及其他灰阶/模糊处理器；各处理器依次读写主帧缓冲，因此可以叠加。
 */
public final class JesterMomentGrayscale {
    private static final Identifier SHADER = SparkStrength.id("shaders/post/jester_grayscale.json");
    private static final float MIN_VISIBLE_GRAYSCALE = 0.005F;

    private static float previousGrayscale;
    private static float grayscale;
    private static PostEffectProcessor processor;
    private static Framebuffer processorTarget;
    private static int processorWidth = -1;
    private static int processorHeight = -1;
    private static boolean loadFailed;

    private JesterMomentGrayscale() {
    }

    /** End of client tick: ease toward the current kill count's grayscale. / 客户端 tick 末：向当前击杀数对应的灰度缓动。 */
    public static void tick(MinecraftClient client) {
        previousGrayscale = grayscale;
        grayscale = JesterMomentRules.stepGrayscale(grayscale, targetGrayscale(client.player));
    }

    private static float targetGrayscale(ClientPlayerEntity player) {
        if (player == null || player.isSpectator() || player.isCreative()) {
            return 0.0F;
        }
        JesterPlayerComponent jester = JesterPlayerComponent.KEY.get(player);
        return jester.inPsychoMode ? JesterMomentRules.grayscaleForKills(jester.killCount) : 0.0F;
    }

    /** Render thread, after the world and before the HUD. / 渲染线程：世界之后、HUD 之前。 */
    public static void render(MinecraftClient client, float tickDelta) {
        float amount = MathHelper.lerp(tickDelta, previousGrayscale, grayscale);
        if (amount < MIN_VISIBLE_GRAYSCALE) {
            closeProcessor();
            return;
        }
        Framebuffer framebuffer = client.getFramebuffer();
        PostEffectProcessor activeProcessor = ensureProcessor(client, framebuffer);
        if (activeProcessor == null) {
            return;
        }
        activeProcessor.setUniforms("Saturation", 1.0F - amount);
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
                SparkStrength.LOGGER.warn("Unable to load Jester Moment grayscale shader", exception);
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

    /** Render thread: disconnect and client shutdown. Clears the fade so the next server starts clean. / 断开连接与关闭客户端时调用，清除渐变状态。 */
    public static void reset() {
        previousGrayscale = 0.0F;
        grayscale = 0.0F;
        close();
    }

    /** Render thread: resource reload, disconnect and shutdown. Allows a fresh load attempt. / 资源重载等时调用，允许重新加载。 */
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
