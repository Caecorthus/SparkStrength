package annina.sparkstrength.client.role.perfumer;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PerfumerBlurRenderer::close));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PerfumerBlurRenderer.close());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return SparkStrength.id("perfumer_screen_effects");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().execute(() -> {
                            PerfumerBlurRenderer.close();
                            PerfumerOverlayRenderer.resetTextureCache();
                        });
                    }
                });
    }
}
