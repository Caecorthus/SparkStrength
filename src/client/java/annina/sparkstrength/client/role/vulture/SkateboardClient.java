package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.component.vulture.SkateboardRideComponent;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Client entry point for the Vulture's skateboard: board rendering, rolling sound and the shared ride check used by
 * the vulture mixins. Everything here only reads the server-synced {@link SkateboardRideComponent}; speed itself is a
 * synced attribute modifier, so the client never decides who rides.
 * 秃鹫滑板的客户端入口：滑板渲染、滚轮声，以及 vulture 包内 mixin 共用的滑行判定。这里只读取服务端同步的
 * {@link SkateboardRideComponent}；速度本身是同步的属性修饰符，客户端从不决定谁在滑行。
 */
public final class SkateboardClient {
    private SkateboardClient() {
    }

    public static void register() {
        // Fired once per player renderer (default and slim), so morphling skin swaps keep the board.
        // 每个玩家渲染器（默认与纤细）各触发一次，因此化形者切换皮肤模型后滑板仍在。
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new SkateboardRenderer.Feature(playerRenderer));
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(SkateboardRenderer::renderCameraBoard);
        SkateboardSoundClient.register();
    }

    /** Whether {@code player} visibly rides a skateboard right now. / 该玩家此刻是否处于可见的滑行状态。 */
    public static boolean isRiding(PlayerEntity player) {
        return !player.isSpectator() && SkateboardRideComponent.KEY.get(player).isRiding();
    }
}
