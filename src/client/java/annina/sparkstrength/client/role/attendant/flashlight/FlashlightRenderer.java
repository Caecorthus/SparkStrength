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
import net.minecraft.client.gl.Framebuffer;
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
 * Draws every visible flashlight in three steps. Opaque receivers (solid and cutout layers) run at
 * {@code WorldRenderEvents.AFTER_ENTITIES}: an additive pass that re-renders lit block geometry as albedo x light
 * (wathe's true darkness makes unlit pixels pure black, so the scene cannot simply be brightened). Running before block
 * entities, translucent terrain and translucent entity layers lets glass windows blend over lit walls and lets anything
 * drawn later in front simply cover the light, while the opaque terrain and entity depth already occludes it.
 * Translucent receivers (glass, wathe's hull and privacy panels) run at {@code AFTER_TRANSLUCENT}, once translucent
 * terrain has written its depth, adding albedo x alpha x light on top of it: into the main framebuffer with Fast and
 * Fancy graphics, into the translucent target with Fabulous, whose composite blends premultiplied colour, so the same
 * additive term stays correct. The volumetric beam runs last, over everything, at {@code LAST} (before the
 * first-person hand), or at {@code AFTER_TRANSLUCENT} with Fabulous graphics, because Fabulous composites the
 * transparency layers into the main framebuffer and clears its depth before LAST. Lights are collected once per frame;
 * nothing runs without lights or while an Iris shader pack is active.
 * 分三步绘制所有可见手电筒。不透明受光面（实心与镂空层）在 {@code WorldRenderEvents.AFTER_ENTITIES} 绘制：叠加通道把受光
 * 方块几何以“反照率 × 光照”重新绘制（wathe 真黑暗使未受光像素为纯黑，无法直接提亮画面）。在方块实体、半透明地形与半透明
 * 实体层之前绘制，玻璃窗会正确叠在被照亮的墙上，之后绘制在前方的物体会直接覆盖光照，而不透明地形与实体深度已提供遮挡。
 * 半透明受光面（玻璃、wathe 船体与隐私面板）在 {@code AFTER_TRANSLUCENT} 绘制，此时半透明地形已写入深度，在其上叠加
 * “反照率 × alpha × 光照”：快速与高品质画质下写入主帧缓冲，“极佳”画质下写入半透明目标；其合成按预乘颜色混合，
 * 因此同样的叠加项依然正确。体积光束最后绘制在所有内容之上：在 {@code LAST}（第一人称手部之前），或在“极佳”画质下于
 * {@code AFTER_TRANSLUCENT}，因为极佳模式在 LAST 之前会把透明层合成到主帧缓冲并清除其深度。每帧只收集一次光源；
 * 没有光源或启用 Iris 光影包时不执行任何操作。
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
        WorldRenderEvents.AFTER_ENTITIES.register(FlashlightRenderer::renderOpaqueReceivers);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            renderTranslucentReceivers(context);
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

    private static void renderOpaqueReceivers(WorldRenderContext context) {
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
        int[] saved = beginPass(client, context, client.getFramebuffer());
        try {
            RECEIVERS.prepare(world, FRAME_LIGHTS, frustum);
            drawReceivers(client, receiver, context, camera, frustum, false);
        } finally {
            endPass(client, context, saved);
            context.profiler().pop();
        }
    }

    /**
     * Translucent-layer receivers over the translucent terrain drawn this frame, reusing the sections prepared at
     * AFTER_ENTITIES. Fabulous keeps translucent terrain (and its depth) in its own target until the composite. Sodium
     * may draw translucent-layer quads with opaque textures in its solid passes instead; with Fast/Fancy they are in
     * the main target anyway, and with Fabulous their depth reaches the translucent target through vanilla's depth
     * copy, so they still pass the depth test and the receiver shader's alpha mark keeps the light in the composite.
     * 在本帧已绘制的半透明地形之上绘制半透明层受光面，复用 AFTER_ENTITIES 时准备的区段。“极佳”画质在合成前把半透明地形
     * （及其深度）保存在独立目标中。Sodium 可能把纹理不透明的半透明层四边形改在其实心通道中绘制：快速/高品质画质下它们本就在
     * 主目标中；“极佳”画质下其深度经原版的深度复制进入半透明目标，仍能通过深度测试，受光着色器的 alpha 标记使光照保留在合成结果中。
     */
    private static void renderTranslucentReceivers(WorldRenderContext context) {
        ShaderProgram receiver = FlashlightShaders.receiver();
        if (FRAME_LIGHTS.isEmpty() || receiver == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer target = context.advancedTranslucency()
                ? context.worldRenderer().getTranslucentFramebuffer() : client.getFramebuffer();
        if (target == null) {
            return;
        }
        context.profiler().push("sparkstrength_flashlight_translucent_receivers");
        int[] saved = beginPass(client, context, target);
        try {
            drawReceivers(client, receiver, context, context.camera().getPos(), context.frustum(), true);
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
        int[] saved = beginPass(client, context, client.getFramebuffer());
        try {
            BEAM.render(client, FRAME_LIGHTS, context.camera().getPos(), context.positionMatrix(),
                    context.projectionMatrix());
        } finally {
            endPass(client, context, saved);
            context.profiler().pop();
        }
    }

    /**
     * Binds the target framebuffer, the block atlas (Sampler0) and the live lightmap (Sampler2); returns the shader
     * textures it replaced.
     * 绑定目标帧缓冲、方块图集（Sampler0）与实时光照贴图（Sampler2）；返回被替换的着色器纹理。
     */
    private static int[] beginPass(MinecraftClient client, WorldRenderContext context, Framebuffer target) {
        int[] saved = {RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(2)};
        target.beginWrite(false);
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
     * vanilla's decal layering so it never z-fights with Sodium's or vanilla's terrain. The translucent layer weights
     * the light by texture alpha (PremultiplyAlpha), matching how much of the pixel the glass itself covers.
     * 叠加（ONE, ONE）、深度测试（LEQUAL）但不写深度，并使用与原版贴花分层相同的多边形偏移前移，
     * 避免与 Sodium 或原版地形产生深度冲突。半透明层按纹理 alpha 加权光照（PremultiplyAlpha），与玻璃自身覆盖像素的比例一致。
     */
    private static void drawReceivers(MinecraftClient client, ShaderProgram receiver, WorldRenderContext context,
                                      Vec3d camera, Frustum frustum, boolean translucentLayer) {
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
        receiver.getUniformOrDefault("PremultiplyAlpha").set(translucentLayer ? 1.0F : 0.0F);
        for (int index = 0; index < FRAME_LIGHTS.size(); index++) {
            FlashlightShaders.applyLight(receiver, FRAME_LIGHTS.get(index), camera);
            receiver.bind();
            RECEIVERS.draw(receiver, index, translucentLayer, camera, frustum);
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
