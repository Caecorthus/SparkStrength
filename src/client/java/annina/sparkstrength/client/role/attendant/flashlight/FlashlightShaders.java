package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The two flashlight core shaders ({@code assets/sparkstrength/shaders/core/flashlight_receiver|flashlight_beam}) and
 * the uniforms both share. Beam-rule constants are written once per (re)load; per-light values before each draw.
 * 两个手电筒核心着色器及其共享 uniform。光束规则常量在每次（重新）加载时写入一次；逐光源数值在每次绘制前写入。
 */
public final class FlashlightShaders {
    /**
     * Receiver mesh layout. The receiver vertex shader's inputs (Position, UV0, Color, Normal) must match these
     * attribute names; 1.21 binds attributes from the registered format, not from the shader JSON.
     * 受光网格顶点格式。受光顶点着色器的输入须与这些属性名一致；1.21 按注册的格式而非着色器 JSON 绑定属性。
     */
    public static final VertexFormat RECEIVER_FORMAT = VertexFormats.POSITION_TEXTURE_COLOR_NORMAL;
    /** Fullscreen clip-space quad for the beam pass. / 光束通道使用的裁剪空间全屏四边形。 */
    public static final VertexFormat BEAM_FORMAT = VertexFormats.POSITION;

    private static @Nullable ShaderProgram receiver;
    private static @Nullable ShaderProgram beam;

    private FlashlightShaders() {
    }

    /**
     * Client init. A shader that fails to compile fails the resource reload like any vanilla core shader, so the
     * programs here are always either loaded or not yet loaded.
     * 客户端初始化。着色器编译失败会像原版核心着色器一样导致资源重载失败，因此这里的程序要么已加载、要么尚未加载。
     */
    static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(SparkStrength.id("flashlight_receiver"), RECEIVER_FORMAT,
                    program -> receiver = withBeamRules(program));
            context.register(SparkStrength.id("flashlight_beam"), BEAM_FORMAT,
                    program -> beam = withBeamRules(program));
        });
    }

    static @Nullable ShaderProgram receiver() {
        return receiver;
    }

    static @Nullable ShaderProgram beam() {
        return beam;
    }

    private static ShaderProgram withBeamRules(ShaderProgram program) {
        program.getUniformOrDefault("ConeCos").set(FlashlightShaderConstants.coneCos());
        program.getUniformOrDefault("SpillStrength").set(FlashlightShaderConstants.spillStrength());
        program.getUniformOrDefault("RangeFalloff").set(FlashlightShaderConstants.rangeFalloff());
        program.getUniformOrDefault("PeakIntensity").set(FlashlightShaderConstants.peakIntensity());
        program.getUniformOrDefault("Exposure").set(FlashlightShaderConstants.exposure());
        program.getUniformOrDefault("ShadowBias").set(FlashlightShaderConstants.shadowBias());
        program.getUniformOrDefault("RayGrid").set(FlashlightShaderConstants.rayGrid());
        program.getUniformOrDefault("LightColor").set(FlashlightShaderConstants.LIGHT_RED,
                FlashlightShaderConstants.LIGHT_GREEN, FlashlightShaderConstants.LIGHT_BLUE);
        return program;
    }

    /**
     * Render thread: camera-relative light and ray-map uniforms shared by both programs. Lights without a ray map
     * reach here only when camera-anchored; they get neither shadows nor the ambient lookup.
     * 渲染线程：两个程序共享的相对相机光源与射线图 uniform。没有射线图的光源只有在与相机重合时才会到达这里，
     * 此时既不做阴影也不做环境光查询。
     */
    static void applyLight(ShaderProgram program, FlashlightLight light, Vec3d camera) {
        setVector(program, "LightOrigin", light.origin().subtract(camera));
        setVector(program, "LightDirection", light.direction());
        FlashlightRayMap rayMap = light.rayMap();
        if (rayMap != null) {
            setVector(program, "ShadowOrigin", rayMap.origin().subtract(camera));
            setVector(program, "ShadowForward", rayMap.forward());
            setVector(program, "ShadowRight", rayMap.right());
            setVector(program, "ShadowUp", rayMap.up());
            program.addSampler("RayMapSampler", rayMap.texture());
        } else {
            // Texture 0 keeps the slot valid; both flags below are 0, so it is never sampled.
            // 纹理 0 保持采样槽有效；下方两个开关均为 0，因此不会被采样。
            program.addSampler("RayMapSampler", 0);
        }
        program.getUniformOrDefault("ShadowEnabled").set(rayMap != null && !light.cameraAnchored() ? 1.0F : 0.0F);
        program.getUniformOrDefault("AmbientEnabled").set(rayMap != null ? 1.0F : 0.0F);
    }

    private static void setVector(ShaderProgram program, String name, Vec3d value) {
        program.getUniformOrDefault(name).set((float) value.x, (float) value.y, (float) value.z);
    }
}
