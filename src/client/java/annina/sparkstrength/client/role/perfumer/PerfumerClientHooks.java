package annina.sparkstrength.client.role.perfumer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.client.render.ManagedPostEffect;

/**
 * Lifecycle for the Perfumer screen effects: release the blur processor and re-check overlay textures on resource
 * reload, disconnect and shutdown. All GL work is posted to the render thread.
 * 调香师屏幕效果的生命周期：资源重载、断开连接与关闭客户端时释放模糊处理器并重新检查覆盖层贴图。
 * 所有 GL 操作都投递到渲染线程执行。
 */
public final class PerfumerClientHooks {
    private PerfumerClientHooks() {
    }

    public static void register() {
        ManagedPostEffect.registerLifecycle(SparkStrength.id("perfumer_screen_effects"),
                PerfumerBlurRenderer::close, PerfumerBlurRenderer::close, () -> {
                    PerfumerBlurRenderer.close();
                    PerfumerOverlayRenderer.resetTextureCache();
                });
    }
}
