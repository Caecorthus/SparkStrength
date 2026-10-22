package annina.sparkstrength.client.role.jester;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.client.render.ManagedPostEffect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

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
        ManagedPostEffect.registerLifecycle(SparkStrength.id("jester_moment_grayscale"),
                JesterMomentGrayscale::reset, JesterMomentGrayscale::reset, JesterMomentGrayscale::close);
    }
}
