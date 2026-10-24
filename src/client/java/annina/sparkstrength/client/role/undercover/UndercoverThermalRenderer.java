package annina.sparkstrength.client.role.undercover;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.client.render.ClientFrame;
import annina.sparkstrength.client.render.ManagedPostEffect;
import annina.sparkstrength.client.world.LoadedEntities;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import annina.sparkstrength.role.undercover.UndercoverRules;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.lang.reflect.Method;

/**
 * The stage-2 Undercover's thermal vision, after Tarkov's T-7 goggles (owner 2026-10-19): a clean, full-resolution
 * white-hot monochrome image, living players and Partygoers (and the Undercover's own first-person arm) as white bodies
 * with a soft bloom, the world in readable greys, inside a rounded eyepiece. Line of sight is real: bodies are re-rendered flat
 * white into the private {@code heat} target over a copy of the world depth, so walls and furniture hide them exactly
 * like they hide anything else, and invisible players still show (their body heat is there).
 * 二阶段卧底的热成像，参照塔科夫 T-7 夜视仪（负责人 2026-10-19）：干净的全分辨率白热单色画面，活人与派对客（以及卧底自己的
 * 第一人称手臂）是带柔光的白色人形，世界是看得清的灰色，外加圆角目镜。视线遮挡是真实的：人体以纯白重新渲染到私有 {@code heat}
 * 目标，并以世界深度做深度测试，所以墙和家具会像遮挡其他东西一样遮住人；隐身的人照样显示（体温还在）。
 *
 * <p>Two steps per frame on the render thread: {@link #captureHeat} at {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}
 * (world depth still intact, before the hand clears it), then {@link #render} before the HUD. With an Iris shader pack
 * the private targets cannot be trusted, so thermal falls back to white outlines (UndercoverClient).
 * 每帧在渲染线程上分两步：在 BEFORE_DEBUG_RENDER（世界深度仍在，手部渲染清空它之前）采集热图，然后在 HUD 前合成。
 * 使用 Iris 光影包时私有目标不可信，热成像退回白色描边（UndercoverClient）。</p>
 */
public final class UndercoverThermalRenderer {
    private static final Identifier SHADER = SparkStrength.id("shaders/post/undercover_thermal.json");
    private static final String HEAT_TARGET = "heat";
    private static final int INITIAL_BUFFER_BYTES = 64 * 1024;
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    /** Frustum margin around a body; vanilla culls entities with 0.5. / 人体周围的视锥余量；原版剔除实体用 0.5。 */
    private static final double CULL_MARGIN = 1.5;
    private static final ManagedPostEffect EFFECT =
            new ManagedPostEffect(SHADER, "Unable to load the Undercover thermal shader");

    private static boolean rendering;
    private static boolean warned;
    private static @Nullable BufferAllocator allocator;
    private static @Nullable Method irisInstance;
    private static @Nullable Method irisPackInUse;
    private static boolean shaderPackInUse;
    private static int shaderPackCheckedEpoch;
    private static boolean shaderPackChecked;

    private UndercoverThermalRenderer() {
    }

    /** True only while thermal bodies are being emitted (labels off, invisible bodies drawn). / 仅在输出热成像人体期间为真。 */
    public static boolean isRenderingHeat() {
        return rendering;
    }

    /**
     * An Iris shader pack renders into its own buffers; any failure to ask Iris counts as "in use" (fail closed).
     * Asked once per client tick and frame ({@link ClientFrame}): the thermal outline fallback asks for every player.
     * Iris 光影包渲染到自己的缓冲区；询问 Iris 失败也按“正在使用”处理。每个客户端 tick 与每帧只询问一次（见 ClientFrame）：
     * 热成像的描边退回逻辑会对每个玩家询问。
     */
    public static boolean shaderPackInUse() {
        if (!IRIS_LOADED) {
            return false;
        }
        int epoch = ClientFrame.epoch();
        if (!shaderPackChecked || shaderPackCheckedEpoch != epoch) {
            shaderPackInUse = askIris();
            shaderPackCheckedEpoch = epoch;
            shaderPackChecked = true;
        }
        return shaderPackInUse;
    }

    private static boolean askIris() {
        try {
            if (irisInstance == null || irisPackInUse == null) {
                Class<?> api = Class.forName(IRIS_API);
                irisInstance = api.getMethod("getInstance");
                irisPackInUse = api.getMethod("isShaderPackInUse");
            }
            return !Boolean.FALSE.equals(irisPackInUse.invoke(irisInstance.invoke(null)));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return true;
        }
    }

    /**
     * True when the heat image can't be drawn (an Iris shader pack, or the post chain failed to load): thermal then falls
     * back to white outlines. / 无法绘制热图时（Iris 光影包，或后处理加载失败）为 true：热成像退回白色描边。
     */
    public static boolean outlineFallback() {
        return EFFECT.loadFailed() || shaderPackInUse();
    }

    private static boolean active(MinecraftClient client) {
        return UndercoverClient.isThermalOn() && client.world != null && !shaderPackInUse();
    }

    /** {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}, render thread. / 渲染线程。 */
    public static void captureHeat(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active(client)) {
            EFFECT.release();
            return;
        }
        Framebuffer main = client.getFramebuffer();
        PostEffectProcessor active = EFFECT.acquire(client, main);
        Framebuffer heat = active == null ? null : active.getSecondaryTarget(HEAT_TARGET);
        if (heat == null) {
            return;
        }
        try {
            heat.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            heat.clear(MinecraftClient.IS_SYSTEM_MAC);
            heat.copyDepthFrom(main);
            renderBodies(client, heat, main, context.projectionMatrix(), context.positionMatrix(),
                    context.camera().getPos(), context.frustum(), context.tickCounter().getTickDelta(true));
        } catch (RuntimeException exception) {
            warnOnce("Undercover thermal heat capture failed", exception);
        } finally {
            main.beginWrite(false);
        }
    }

    /** Render thread, after the world and before the HUD. / 渲染线程：世界之后、HUD 之前。 */
    public static void render(MinecraftClient client, float tickDelta) {
        if (!active(client)) {
            EFFECT.release();
            return;
        }
        Framebuffer framebuffer = client.getFramebuffer();
        PostEffectProcessor activeProcessor = EFFECT.acquire(client, framebuffer);
        if (activeProcessor == null) {
            return;
        }
        EFFECT.draw(framebuffer, tickDelta);
    }

    private static void renderBodies(MinecraftClient client, Framebuffer heat, Framebuffer main, Matrix4f projection,
                                     Matrix4f viewRotation, Vec3d camera, @Nullable Frustum frustum, float tickDelta) {
        BufferBuilder builder = new BufferBuilder(allocator(), VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        WhiteVertexConsumer white = new WhiteVertexConsumer(builder);
        VertexConsumerProvider provider = layer -> white;
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        double range = UndercoverRules.SIGHT_RANGE * UndercoverRules.SIGHT_RANGE;
        rendering = true;
        try {
            for (AbstractClientPlayerEntity body : client.world.getPlayers()) {
                if (body == client.player && client.options.getPerspective().isFirstPerson()) {
                    continue;
                }
                if (!GameFunctions.isPlayerPlayingAndAlive(body) || body.squaredDistanceTo(camera) > range) {
                    continue;
                }
                emitBody(dispatcher, body, camera, frustum, tickDelta, provider);
            }
            // A Partygoer is warm too; without it only the player in its arms would glow (2026-10-24).
            // 派对客也有体温；否则只有它怀里的人在发热（2026-10-24）。
            if (LoadedEntities.anyPartygoer(client.world)) {
                double side = 2.0 * UndercoverRules.SIGHT_RANGE;
                for (PartygoerEntity partygoer : client.world.getEntitiesByClass(PartygoerEntity.class,
                        Box.of(camera, side, side, side), UndercoverThermalRenderer::isHot)) {
                    if (partygoer.squaredDistanceTo(camera) <= range) {
                        emitBody(dispatcher, partygoer, camera, frustum, tickDelta, provider);
                    }
                }
            }
        } finally {
            rendering = false;
            white.close();
        }
        BuiltBuffer built = builder.endNullable();
        if (built == null) {
            return;
        }
        heat.beginWrite(false);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(viewRotation);
        RenderSystem.applyModelViewMatrix();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GlConst.GL_LEQUAL);
        RenderSystem.depthMask(false);
        // Pull the re-rendered bodies a hair toward the camera so they win against their own depth.
        // 把重新渲染的人体稍微拉近相机，使其在与自身深度比较时稳定通过。
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -10.0F);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            BufferRenderer.drawWithGlobalProgram(built);
        } finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            main.beginWrite(false);
        }
    }

    /**
     * PlayerEntityRenderer#renderArm, render thread: the first-person arm, already posed, drawn white into the heat target.
     * The hand pass runs after {@link #captureHeat} with its own projection and a cleared depth buffer, so the arm goes on
     * top without a depth test, using the projection and model-view the hand pass set.
     * 渲染线程：已摆好姿势的第一人称手臂以白色画进热图。手部 pass 在 captureHeat 之后，有自己的投影且深度已清空，
     * 所以手臂不做深度测试直接画在最上层，沿用手部 pass 设置的投影与模型视图。
     */
    public static void renderArmHeat(MatrixStack matrices, ModelPart arm, ModelPart sleeve) {
        MinecraftClient client = MinecraftClient.getInstance();
        PostEffectProcessor loaded = EFFECT.current();
        if (loaded == null || !active(client)) {
            return;
        }
        Framebuffer heat = loaded.getSecondaryTarget(HEAT_TARGET);
        if (heat == null) {
            return;
        }
        BufferBuilder builder = new BufferBuilder(allocator(), VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        WhiteVertexConsumer white = new WhiteVertexConsumer(builder);
        try {
            arm.render(matrices, white, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            sleeve.render(matrices, white, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        } finally {
            white.close();
        }
        BuiltBuffer built = builder.endNullable();
        if (built == null) {
            return;
        }
        heat.beginWrite(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            BufferRenderer.drawWithGlobalProgram(built);
        } catch (RuntimeException exception) {
            warnOnce("Undercover thermal arm render failed", exception);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            client.getFramebuffer().beginWrite(false);
        }
    }

    /**
     * Conservative frustum cull: only a body that cannot put a pixel on screen is skipped. The box is swept from the
     * last to the current tick position (emitBody draws in between), stretched by the renderer offset and grown by
     * {@link #CULL_MARGIN}, wider than vanilla's 0.5: the heat pass draws against the world depth, so arms, held items,
     * capes or a skateboard reaching past vanilla's margin still glow. No frustum or no usable box: draw it, as before.
     * 保守的视锥剔除：只跳过不可能在屏幕上留下像素的人体。包围盒从上一 tick 位置扫到当前 tick 位置（emitBody 画在两者之间），
     * 按渲染偏移拉伸，并按比原版 0.5 更宽的 CULL_MARGIN 扩大：热图以世界深度做深度测试，所以伸出原版余量的手臂、手持物品、
     * 披风或滑板仍会发热。没有视锥或包围盒不可用时照常绘制。
     */
    private static boolean inView(@Nullable Frustum frustum, Entity body, Vec3d offset) {
        if (frustum == null || body.ignoreCameraFrustum) {
            return true;
        }
        Box now = body.getVisibilityBoundingBox();
        if (now.isNaN() || now.getAverageSideLength() == 0.0) {
            return true;
        }
        Box last = now.offset(body.lastRenderX - body.getX(), body.lastRenderY - body.getY(),
                body.lastRenderZ - body.getZ());
        return frustum.isVisible(now.union(last).stretch(offset).expand(CULL_MARGIN));
    }

    /**
     * In the world and not hidden inside a Taotie (where the heat pass would draw it anyway: it shows invisible bodies).
     * 在场且没有藏在饕餮肚里（热图 pass 会画出隐身的身体，所以必须排除）。
     */
    private static boolean isHot(PartygoerEntity partygoer) {
        return partygoer.isAlive() && partygoer.partyState() != PartygoerState.SWALLOWED;
    }

    private static <T extends Entity> void emitBody(EntityRenderDispatcher dispatcher, T body, Vec3d camera,
                                                    @Nullable Frustum frustum, float tickDelta,
                                                    VertexConsumerProvider provider) {
        // Same placement as WorldRenderer#renderEntity + EntityRenderDispatcher#render, minus shadow, fire, hitbox.
        // 与 WorldRenderer#renderEntity + EntityRenderDispatcher#render 摆放一致，但不画阴影、火焰与碰撞箱。
        double x = MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()) - camera.z;
        float yaw = MathHelper.lerp(tickDelta, body.prevYaw, body.getYaw());
        MatrixStack matrices = new MatrixStack();
        try {
            EntityRenderer<? super T> renderer = dispatcher.getRenderer(body);
            Vec3d offset = renderer.getPositionOffset(body, tickDelta);
            if (!inView(frustum, body, offset)) {
                return;
            }
            matrices.translate(x + offset.x, y + offset.y, z + offset.z);
            renderer.render(body, yaw, tickDelta, matrices, provider, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        } catch (RuntimeException exception) {
            // Another mod's render hook failing must not crash the frame; that body just stays cold.
            // 其他模组的渲染钩子出错不能让整帧崩溃；该人体只是不发热。
            warnOnce("Undercover thermal body render failed for " + body.getName().getString(), exception);
        }
    }

    private static BufferAllocator allocator() {
        if (allocator == null) {
            allocator = new BufferAllocator(INITIAL_BUFFER_BYTES);
        }
        return allocator;
    }

    private static void warnOnce(String message, Throwable error) {
        if (!warned) {
            warned = true;
            SparkStrength.LOGGER.warn(message, error);
        }
    }

    /** Resource reload, disconnect and shutdown: frees everything and allows a fresh load. / 资源重载、断开连接与关闭：释放一切并允许重新加载。 */
    public static void close() {
        EFFECT.close();
        if (allocator != null) {
            allocator.close();
            allocator = null;
        }
    }

    /**
     * Keeps positions only and paints every vertex opaque white. Valid for one pass: writes after {@link #close()} are
     * dropped, since another mod may keep the provider (same guard as SparkWitch's Blind silhouettes).
     * 只保留位置并把每个顶点涂成不透明白色。仅在一次 pass 内有效：close() 之后的写入全部丢弃，因为其他模组可能保留提供器
     * （与 SparkWitch 盲人轮廓同样的防护）。
     */
    private static final class WhiteVertexConsumer implements VertexConsumer {
        private @Nullable VertexConsumer delegate;

        WhiteVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        void close() {
            delegate = null;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            VertexConsumer target = delegate;
            if (target != null) {
                target.vertex(x, y, z).color(255, 255, 255, 255);
            }
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }
}
