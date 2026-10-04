package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.SparkStrength;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws every visible flashlight in two hooks. Receivers run at {@code WorldRenderEvents.AFTER_ENTITIES}: an additive
 * pass that re-renders lit block geometry as albedo x light (wathe's true darkness makes unlit pixels pure black, so
 * the scene cannot simply be brightened). Running before block entities, translucent terrain and translucent entity
 * layers lets glass windows blend over lit walls and lets anything drawn later in front simply cover the light, while
 * the opaque terrain and entity depth already occludes it. The volumetric beam runs last, over everything, at
 * {@code LAST} (before the first-person hand), or at {@code AFTER_TRANSLUCENT} with Fabulous graphics, because Fabulous
 * composites the transparency layers into the main framebuffer and clears its depth before LAST. Lights are collected
 * once per frame; nothing runs without lights or while an Iris shader pack is active.
 * 分两个挂载点绘制所有可见手电筒。受光面在 {@code WorldRenderEvents.AFTER_ENTITIES} 绘制：叠加通道把受光方块几何以
 * “反照率 × 光照”重新绘制（wathe 真黑暗使未受光像素为纯黑，无法直接提亮画面）。在方块实体、半透明地形与半透明实体层之前
 * 绘制，玻璃窗会正确叠在被照亮的墙上，之后绘制在前方的物体会直接覆盖光照，而不透明地形与实体深度已提供遮挡。
 * 体积光束最后绘制在所有内容之上：在 {@code LAST}（第一人称手部之前），或在“极佳”画质下于 {@code AFTER_TRANSLUCENT}，
 * 因为极佳模式在 LAST 之前会把透明层合成到主帧缓冲并清除其深度。每帧只收集一次光源；没有光源或启用 Iris 光影包时不执行任何操作。
 */
public final class FlashlightRenderer {
    private static final FlashlightReceiverCache RECEIVERS = new FlashlightReceiverCache();
    private static final FlashlightBeamPass BEAM = new FlashlightBeamPass();
    /** This frame's drawable lights, filled at AFTER_ENTITIES. / 本帧可绘制的光源，在 AFTER_ENTITIES 时填充。 */
    private static final List<FlashlightLight> FRAME_LIGHTS = new ArrayList<>(FlashlightLights.MAX_LIGHTS);

    private static boolean irisResolved;
    private static Object irisApi;
    private static Method irisShaderPackInUse;

    private FlashlightRenderer() {
    }

    /** Client init. / 客户端初始化。 */
    public static void register() {
        FlashlightShaders.register();
        WorldRenderEvents.START.register(context -> FRAME_LIGHTS.clear());
        WorldRenderEvents.AFTER_ENTITIES.register(FlashlightRenderer::renderReceivers);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.advancedTranslucency()) {
                renderBeams(context);
            }
        });
        WorldRenderEvents.LAST.register(context -> {
            if (!context.advancedTranslucency()) {
                renderBeams(context);
            }
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                onRenderThread(() -> RECEIVERS.onChunkLoaded(chunk.getPos())));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                onRenderThread(() -> RECEIVERS.onChunkUnloaded(chunk.getPos())));
        // WorldRenderer.reload(): world change, resource reload (new atlas UVs) and video setting changes.
        // WorldRenderer.reload()：世界切换、资源重载（图集 UV 改变）与视频设置变化。
        InvalidateRenderStateCallback.EVENT.register(() -> onRenderThread(FlashlightRenderer::reset));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FlashlightRenderer::reset));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> reset());
    }

    /**
     * Client block change (see FlashlightReceiverInvalidationMixin): re-mesh the section and border neighbours, and
     * let nearby beams recast their occlusion next tick (a door that just closed must stop light at once).
     * 客户端方块变化（见 FlashlightReceiverInvalidationMixin）：重新网格化所在区段及边界相邻区段，并让附近光束在下一 tick
     * 重新投射遮挡（刚关上的门必须立即挡光）。
     */
    public static void onBlockChanged(BlockPos pos) {
        if (RenderSystem.isOnRenderThread()) {
            RECEIVERS.onBlockChanged(pos);
            FlashlightLights.onBlockChanged(pos);
        } else {
            BlockPos immutable = pos.toImmutable();
            RenderSystem.recordRenderCall(() -> {
                RECEIVERS.onBlockChanged(immutable);
                FlashlightLights.onBlockChanged(immutable);
            });
        }
    }

    private static void renderReceivers(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = context.world();
        if (world == null) {
            return;
        }
        List<FlashlightLight> lights = FlashlightLights.collect(client, context.tickCounter().getTickDelta(true));
        if (lights.isEmpty() || irisShaderPackInUse()) {
            return;
        }
        for (FlashlightLight light : lights) {
            // Without a ray map yet, only a camera-anchored light has exact occlusion (the depth test).
            // 尚无射线图时，只有与相机重合的光源具有精确遮挡（深度测试）。
            if (light.cameraAnchored() || light.rayMap() != null) {
                FRAME_LIGHTS.add(light);
            }
        }
        ShaderProgram receiver = FlashlightShaders.receiver();
        if (FRAME_LIGHTS.isEmpty() || receiver == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        Frustum frustum = context.frustum();
        context.profiler().push("sparkstrength_flashlight_receivers");
        int[] saved = beginPass(client, context);
        try {
            RECEIVERS.prepare(world, FRAME_LIGHTS, frustum);
            drawReceivers(client, receiver, context, camera, frustum);
        } finally {
            endPass(client, context, saved);
            context.profiler().pop();
        }
    }

    private static void renderBeams(WorldRenderContext context) {
        if (FRAME_LIGHTS.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        context.profiler().push("sparkstrength_flashlight_beams");
        int[] saved = beginPass(client, context);
        try {
            BEAM.render(client, FRAME_LIGHTS, context.camera().getPos(), context.positionMatrix(),
                    context.projectionMatrix());
        } finally {
            endPass(client, context, saved);
            context.profiler().pop();
        }
    }

    /**
     * Binds the main framebuffer, the block atlas (Sampler0) and the live lightmap (Sampler2); returns the shader
     * textures it replaced.
     * 绑定主帧缓冲、方块图集（Sampler0）与实时光照贴图（Sampler2）；返回被替换的着色器纹理。
     */
    private static int[] beginPass(MinecraftClient client, WorldRenderContext context) {
        int[] saved = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(2)};
        client.getFramebuffer().beginWrite(false);
        RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        context.lightmapTextureManager().enable();
        return saved;
    }

    /**
     * Restores vanilla's state at all three hook points: blending off, depth test and writes on, LEQUAL, back-face
     * culling, no polygon offset, main framebuffer bound.
     * 恢复三个挂载点处的原版状态：关闭混合、开启深度测试与写入、LEQUAL、背面剔除、无多边形偏移、绑定主帧缓冲。
     */
    private static void endPass(MinecraftClient client, WorldRenderContext context, int[] saved) {
        context.lightmapTextureManager().disable();
        RenderSystem.setShaderTexture(0, saved[0]);
        RenderSystem.setShaderTexture(2, saved[1]);
        RenderSystem.disablePolygonOffset();
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        client.getFramebuffer().beginWrite(false);
    }

    /**
     * Additive (ONE, ONE), depth-tested (LEQUAL) without depth writes, pulled forward by the same polygon offset as
     * vanilla's decal layering so it never z-fights with Sodium's or vanilla's terrain.
     * 叠加（ONE, ONE）、深度测试（LEQUAL）但不写深度，并使用与原版贴花分层相同的多边形偏移前移，
     * 避免与 Sodium 或原版地形产生深度冲突。
     */
    private static void drawReceivers(MinecraftClient client, ShaderProgram receiver, WorldRenderContext context,
                                      Vec3d camera, Frustum frustum) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        RenderSystem.enableCull();
        receiver.initializeUniforms(VertexFormat.DrawMode.QUADS, context.positionMatrix(), context.projectionMatrix(),
                client.getWindow());
        for (int index = 0; index < FRAME_LIGHTS.size(); index++) {
            FlashlightShaders.applyLight(receiver, FRAME_LIGHTS.get(index), camera);
            receiver.bind();
            RECEIVERS.draw(receiver, index, camera, frustum);
            receiver.unbind();
        }
        VertexBuffer.unbind();
    }

    private static void reset() {
        RECEIVERS.clear();
        BEAM.close();
    }

    private static void onRenderThread(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            RenderSystem.recordRenderCall(task::run);
        }
    }

    /**
     * Iris replaces the terrain pipeline with its own shader programs, so a pack's lighting would fight this pass.
     * Reflection only: Iris is not a dependency.
     * Iris 会用自身着色器替换地形管线，光影包的光照会与本通道冲突。仅使用反射：Iris 不是依赖项。
     */
    private static boolean irisShaderPackInUse() {
        if (!irisResolved) {
            irisResolved = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    irisShaderPackInUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError exception) {
                    SparkStrength.LOGGER.warn("Iris is loaded but its API is unavailable; flashlight stays on",
                            exception);
                }
            }
        }
        if (irisShaderPackInUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(irisShaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
