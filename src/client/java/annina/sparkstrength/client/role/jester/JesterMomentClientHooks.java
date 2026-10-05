package annina.sparkstrength.client.role.jester;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Lifecycle for the Jester Moment grayscale: tick the fade, and release the processor on resource reload, disconnect
 * and shutdown. All GL work runs on the render thread.
 * 小丑时刻灰度的生命周期：每 tick 推进渐变；资源重载、断开连接与关闭客户端时释放处理器。所有 GL 操作都在渲染线程执行。
 */
public final class JesterMomentClientHooks {
    private JesterMomentClientHooks() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(JesterMomentGrayscale::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(JesterMomentGrayscale::reset));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> JesterMomentGrayscale.reset());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return SparkStrength.id("jester_moment_grayscale");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().execute(JesterMomentGrayscale::close);
                    }
                });
    }
}
