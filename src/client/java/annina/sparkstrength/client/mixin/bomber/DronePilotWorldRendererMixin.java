package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Own body while the camera looks through a drone: vanilla's entity loop skips the local {@code ClientPlayerEntity}
 * whenever the camera focuses another entity ({@code entity instanceof ClientPlayerEntity && camera.getFocusedEntity()
 * != entity}). The 4th {@code getFocusedEntity()} call in {@code render} is that comparison (only reached for a
 * ClientPlayerEntity); answering the local player there lets the pilot see its still body through the normal entity
 * pass. Pass-through unless piloting.
 * 镜头通过无人机观看时显示自身本体：原版实体循环在镜头聚焦其他实体时跳过本地 ClientPlayerEntity。render 中第 4 次
 * getFocusedEntity() 调用正是该比较（仅对 ClientPlayerEntity 执行）；在此返回本地玩家，即可让驾驶者通过正常实体渲染看到静止的本体。
 * 未驾驶时不做改动。
 *
 * <p>The render-grid injectors live in {@link DronePilotTerrainGridMixin}, which is skipped under Sodium (it overwrites
 * {@code setupTerrain}). This one stays applied under Sodium, which only adds callbacks to {@code render}.
 * {@code require = 0}: a renderer mod reshaping {@code render} would only cost the visible body, never a crash (the
 * client config defaults to require 1).
 * 渲染网格注入位于 DronePilotTerrainGridMixin，Sodium 下会被跳过（Sodium 覆盖了 setupTerrain）。本类在 Sodium 下照常应用，
 * Sodium 只向 render 添加回调。require = 0：渲染模组改写 render 只会失去本体显示，不会崩溃（客户端配置默认 require 1）。</p>
 */
@Mixin(WorldRenderer.class)
public abstract class DronePilotWorldRendererMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;", ordinal = 3),
            require = 0)
    private Entity sparkstrength$showBodyWhilePiloting(Entity focused) {
        return DronePilotClient.isViewingDrone() ? MinecraftClient.getInstance().player : focused;
    }
}
