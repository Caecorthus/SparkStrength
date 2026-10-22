package annina.sparkstrength.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Render-thread clock for per-frame caches. The epoch moves at every client tick and at the start of every world
 * render, so nothing cached under one epoch outlives a tick or a frame. Hot per-entity hooks (instinct outlines, player
 * skins) may reuse a value only while the world is being rendered: packets and ticks run outside
 * {@code WorldRenderEvents.START}..{@code END}, so nothing they read changes in between. Anywhere else they recompute.
 * 渲染线程上供逐帧缓存使用的时钟。每个客户端 tick 以及每次世界渲染开始时 epoch 都会前进，因此按某个 epoch 缓存的值不会
 * 跨过一个 tick 或一帧。逐实体的热点钩子（本能描边、玩家皮肤）只在世界渲染期间复用缓存值：数据包与 tick 都不在
 * START..END 之间执行，它们读取的东西在此期间不会变化；其他时候一律重新计算。
 */
public final class ClientFrame {
    private static boolean registered;
    private static int epoch;
    private static int worldRenderDepth;

    private ClientFrame() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            epoch++;
            // Ticks never run inside a world render; a render that threw before END must not keep caches open.
            // tick 不会在世界渲染内部执行；在 END 之前抛出异常的渲染不能让缓存一直保持有效。
            worldRenderDepth = 0;
        });
        WorldRenderEvents.START.register(context -> {
            epoch++;
            worldRenderDepth++;
        });
        WorldRenderEvents.END.register(context -> {
            if (worldRenderDepth > 0) {
                worldRenderDepth--;
            }
        });
    }

    /** Changes at every client tick and world render start. / 每个客户端 tick 与每次世界渲染开始时变化。 */
    public static int epoch() {
        return epoch;
    }

    /** Between WorldRenderEvents.START and END. / 处于世界渲染的 START 与 END 之间。 */
    public static boolean inWorldRender() {
        return worldRenderDepth > 0;
    }
}
