package annina.sparkstrength.client.role.attendant.flashlight;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Volumetric beam and lens glare: one fullscreen additive pass per light that is not camera-anchored (looking along
 * your own beam would only fog the screen centre). The scene depth is first copied into a private framebuffer, so the
 * shader never samples the depth buffer it is drawing into.
 * 体积光束与镜头眩光：对每个不与相机重合的光源执行一次全屏叠加通道（沿自己的光束看只会让屏幕中心发雾）。
 * 先把场景深度复制到私有帧缓冲，着色器因此不会采样正在绘制的深度缓冲。
 */
final class FlashlightBeamPass {
    private static final Matrix4f IDENTITY = new Matrix4f();

    private @Nullable SimpleFramebuffer depthCopy;
    private @Nullable VertexBuffer quad;

    /**
     * Render thread, with the main framebuffer bound and the lightmap on Sampler2. Leaves blending on (ONE, ONE) and
     * depth test off; the caller restores vanilla state.
     * 渲染线程，调用时主帧缓冲已绑定、光照贴图位于 Sampler2。结束时混合为 (ONE, ONE)、深度测试关闭；由调用方恢复原版状态。
     */
    void render(MinecraftClient client, List<FlashlightLight> lights, Vec3d camera, Matrix4f positionMatrix,
                Matrix4f projectionMatrix) {
        ShaderProgram program = FlashlightShaders.beam();
        if (program == null || !hasBeam(lights)) {
            return;
        }
        Framebuffer main = client.getFramebuffer();
        SimpleFramebuffer depth = depthCopy(main);
        depth.copyDepthFrom(main);
        // copyDepthFrom leaves framebuffer 0 bound. / copyDepthFrom 结束时绑定的是帧缓冲 0。
        main.beginWrite(false);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);

        Matrix4f viewProjection = new Matrix4f(projectionMatrix).mul(positionMatrix);
        program.getUniformOrDefault("ViewProjMat").set(viewProjection);
        program.getUniformOrDefault("InvViewProjMat").set(new Matrix4f(viewProjection).invert());
        program.getUniformOrDefault("BeamStrength").set(FlashlightShaderConstants.BEAM_STRENGTH);
        program.getUniformOrDefault("GlareStrength").set(FlashlightShaderConstants.GLARE_STRENGTH);
        program.addSampler("DepthSampler", depth.getDepthAttachment());

        VertexBuffer fullscreen = quad();
        fullscreen.bind();
        for (FlashlightLight light : lights) {
            if (light.cameraAnchored() || light.rayMap() == null) {
                continue;
            }
            FlashlightShaders.applyLight(program, light, camera);
            fullscreen.draw(IDENTITY, IDENTITY, program);
        }
        VertexBuffer.unbind();
    }

    /** Render thread: world change, resource reload and shutdown. / 渲染线程：世界切换、资源重载与关闭时调用。 */
    void close() {
        if (depthCopy != null) {
            depthCopy.delete();
            depthCopy = null;
        }
        if (quad != null) {
            quad.close();
            quad = null;
        }
    }

    private static boolean hasBeam(List<FlashlightLight> lights) {
        for (FlashlightLight light : lights) {
            if (!light.cameraAnchored() && light.rayMap() != null) {
                return true;
            }
        }
        return false;
    }

    private SimpleFramebuffer depthCopy(Framebuffer main) {
        if (depthCopy == null) {
            depthCopy = new SimpleFramebuffer(main.textureWidth, main.textureHeight, true,
                    MinecraftClient.IS_SYSTEM_MAC);
        } else if (depthCopy.textureWidth != main.textureWidth || depthCopy.textureHeight != main.textureHeight) {
            depthCopy.resize(main.textureWidth, main.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
        }
        return depthCopy;
    }

    private VertexBuffer quad() {
        if (quad == null) {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                    FlashlightShaders.BEAM_FORMAT);
            builder.vertex(-1.0F, -1.0F, 0.0F);
            builder.vertex(1.0F, -1.0F, 0.0F);
            builder.vertex(1.0F, 1.0F, 0.0F);
            builder.vertex(-1.0F, 1.0F, 0.0F);
            quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
            quad.bind();
            quad.upload(builder.end());
        }
        return quad;
    }
}
