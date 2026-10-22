package annina.sparkstrength.client.render;

import annina.sparkstrength.SparkStrength;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * One private {@link PostEffectProcessor} over the main framebuffer, separate from GameRenderer's shared slot and from
 * every other add-on processor; each reads and writes the main framebuffer in turn, so they stack. Loaded on the first
 * frame that needs it, resized in place with the window, rebuilt when the main target changes, released when the
 * effect ends. A load failure is logged once and blocks retries until {@link #close()} (resource reload, disconnect,
 * shutdown). All calls run on the render thread.
 * 主帧缓冲上的一个私有后处理器，独立于 GameRenderer 的共享槽位与其他附属模组的处理器；各处理器依次读写主帧缓冲，因此可以叠加。
 * 首个需要它的帧才加载，随窗口原地调整尺寸，主目标变化时重建，效果结束即释放。加载失败只记录一次日志，并在 close()
 * （资源重载、断开连接、关闭客户端）之前不再重试。所有调用都在渲染线程上执行。
 */
public final class ManagedPostEffect {
    private final Identifier shader;
    private final String loadFailureMessage;
    private @Nullable PostEffectProcessor processor;
    private @Nullable Framebuffer processorTarget;
    private int processorWidth = -1;
    private int processorHeight = -1;
    private boolean loadFailed;

    public ManagedPostEffect(Identifier shader, String loadFailureMessage) {
        this.shader = shader;
        this.loadFailureMessage = loadFailureMessage;
    }

    /**
     * The processor for {@code framebuffer}, loaded or resized as needed; null while it cannot run (failed load, empty
     * target). / 对应 framebuffer 的处理器，按需加载或调整尺寸；无法运行时（加载失败、目标为空）为 null。
     */
    public @Nullable PostEffectProcessor acquire(MinecraftClient client, Framebuffer framebuffer) {
        if (loadFailed || framebuffer.textureWidth <= 0 || framebuffer.textureHeight <= 0) {
            return null;
        }
        if (processor != null && processorTarget != framebuffer) {
            release();
        }
        if (processor == null) {
            try {
                processor = new PostEffectProcessor(
                        client.getTextureManager(), client.getResourceManager(), framebuffer, shader);
            } catch (IOException | RuntimeException exception) {
                SparkStrength.LOGGER.warn(loadFailureMessage, exception);
                release();
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

    /** The loaded processor, or null; never loads. / 已加载的处理器或 null；从不加载。 */
    public @Nullable PostEffectProcessor current() {
        return processor;
    }

    /** True after a failed load, until {@link #close()}. / 加载失败后为 true，直到 close()。 */
    public boolean loadFailed() {
        return loadFailed;
    }

    /**
     * Runs the loaded processor over {@code framebuffer} and rebinds it for the HUD.
     * 在 framebuffer 上运行已加载的处理器，并为 HUD 重新绑定它。
     */
    public void draw(Framebuffer framebuffer, float tickDelta) {
        PostEffectProcessor active = processor;
        if (active == null) {
            return;
        }
        // Same state reset vanilla performs before its own post effect. / 与原版运行自身后处理前的状态重置一致。
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        try {
            active.render(tickDelta);
        } finally {
            // The last pass unbinds the main target; rebind it for the HUD. / 最后一个 pass 会解绑主帧缓冲，为 HUD 重新绑定。
            framebuffer.beginWrite(false);
        }
    }

    /** The effect is off this frame: free the processor, keep a load failure. / 本帧效果关闭：释放处理器，保留加载失败状态。 */
    public void release() {
        if (processor != null) {
            processor.close();
            processor = null;
        }
        processorTarget = null;
        processorWidth = -1;
        processorHeight = -1;
    }

    /**
     * Resource reload, disconnect and shutdown: free the processor and allow a fresh load.
     * 资源重载、断开连接与关闭：释放处理器并允许重新加载。
     */
    public void close() {
        release();
        loadFailed = false;
    }

    /**
     * Registers an effect's lifecycle: {@code onDisconnect} and {@code onReload} are posted to the render thread,
     * {@code onStop} runs inline at client shutdown. Each must at least {@link #close()} the effect's processors.
     * 注册效果的生命周期：onDisconnect 与 onReload 投递到渲染线程执行，onStop 在关闭客户端时直接执行。每个回调至少要
     * close() 该效果的处理器。
     */
    public static void registerLifecycle(Identifier reloadId, Runnable onDisconnect, Runnable onStop,
                                         Runnable onReload) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(onDisconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onStop.run());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return reloadId;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        MinecraftClient.getInstance().execute(onReload);
                    }
                });
    }
}
