package annina.sparkstrength.client.role.perfumer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.client.render.ManagedPostEffect;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

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
    private static final ManagedPostEffect EFFECT =
            new ManagedPostEffect(SHADER, "Unable to load Perfumer Cooling Oil blur shader; using overlay only");

    private PerfumerBlurRenderer() {
    }

    /** Render thread, after the world (and vanilla's post effect) and before the HUD. / 渲染线程：世界与原版后处理之后、HUD 之前。 */
    public static void render(MinecraftClient client, float tickDelta) {
        Framebuffer framebuffer = client.getFramebuffer();
        float radius = PerfumerClientEffects.coolingOilBlurRadius(tickDelta)
                * PerfumerRules.coolingOilBlurResolutionScale(framebuffer.textureHeight);
        // box_blur rounds the radius, so anything below half a texel is a no-op. / box_blur 会取整，半个纹素以下无效果。
        if (radius < MIN_VISIBLE_RADIUS) {
            EFFECT.release();
            return;
        }
        PostEffectProcessor activeProcessor = EFFECT.acquire(client, framebuffer);
        if (activeProcessor == null) {
            return;
        }
        activeProcessor.setUniforms("Radius", radius);
        EFFECT.draw(framebuffer, tickDelta);
    }

    /** Render thread: resource reload, disconnect and client shutdown. Allows a fresh load attempt. / 渲染线程：资源重载、断开连接与关闭客户端时调用，允许重新尝试加载。 */
    public static void close() {
        EFFECT.close();
    }
}
