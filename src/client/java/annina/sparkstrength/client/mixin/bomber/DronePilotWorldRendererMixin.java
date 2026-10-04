package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * World rendering while the camera looks through a drone.
 * <ul>
 *   <li>Render grid (unlimited range): vanilla {@code setupTerrain} centres the built-chunk grid
 *   ({@code BuiltChunkStorage.updateCameraPosition}) on {@code client.player}, so past render distance from the body
 *   nothing would draw (terrain, nor entities, which need ready sections). The three player coordinate reads return the
 *   camera position instead. The server re-centres the chunk view on the drone separately.</li>
 *   <li>Own body: vanilla's entity loop skips the local {@code ClientPlayerEntity} whenever the camera focuses another
 *   entity ({@code entity instanceof ClientPlayerEntity && camera.getFocusedEntity() != entity}). The 4th
 *   {@code getFocusedEntity()} call in {@code render} is that comparison (only reached for a ClientPlayerEntity);
 *   answering the local player there lets the pilot see its still body through the normal entity pass.</li>
 * </ul>
 * {@code require = 0} on all: Sodium replaces {@code setupTerrain} with a camera-centred renderer, and a renderer mod
 * reshaping {@code render} would only cost the visible body; neither may crash the game (the client config defaults to
 * require 1). Pass-through unless piloting.
 * 镜头通过无人机观看时的世界渲染。
 * 渲染网格（无限范围）：原版 setupTerrain 以 client.player 为中心布置区块网格，飞出本体渲染距离后将什么都画不出（地形，以及需要
 * 就绪区块段的实体）。三处玩家坐标读取改为镜头位置。服务器另行把区块视野重新以无人机为中心。
 * 自身本体：原版实体循环在镜头聚焦其他实体时跳过本地 ClientPlayerEntity。render 中第 4 次 getFocusedEntity() 调用正是该比较
 * （仅对 ClientPlayerEntity 执行）；在此返回本地玩家，即可让驾驶者通过正常实体渲染看到静止的本体。
 * 全部 require = 0：Sodium 会替换 setupTerrain（以镜头为中心），渲染模组改写 render 也只会失去本体显示；两者都不能导致崩溃
 * （客户端配置默认 require 1）。未驾驶时不做改动。
 */
@Mixin(WorldRenderer.class)
public abstract class DronePilotWorldRendererMixin {
    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"), require = 0)
    private double sparkstrength$droneGridCenterX(double original, @Local(argsOnly = true) Camera camera) {
        return DronePilotClient.renderGridCenter(original, camera, 0);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"), require = 0)
    private double sparkstrength$droneGridCenterY(double original, @Local(argsOnly = true) Camera camera) {
        return DronePilotClient.renderGridCenter(original, camera, 1);
    }

    @ModifyExpressionValue(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"), require = 0)
    private double sparkstrength$droneGridCenterZ(double original, @Local(argsOnly = true) Camera camera) {
        return DronePilotClient.renderGridCenter(original, camera, 2);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;", ordinal = 3),
            require = 0)
    private Entity sparkstrength$showBodyWhilePiloting(Entity focused) {
        return DronePilotClient.isViewingDrone() ? MinecraftClient.getInstance().player : focused;
    }
}
