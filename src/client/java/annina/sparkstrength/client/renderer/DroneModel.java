package annina.sparkstrength.client.renderer;

import annina.sparkstrength.entity.DroneEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared base of the two Bomber drone models: four counter-rotating rotors with motion-blur discs and full-bright LEDs.
 * Rendered in passes: body (cutout), LEDs (full-bright), rotors (translucent, blades fade as the blur disc appears).
 * Model units are 1/32 block; the renderer scales ModelPart output by {@link #MODEL_SCALE}. Geometry and UVs are
 * generated together with the textures, so edit both together.
 * 两种炸弹客无人机模型的共用基类：四个反向旋转的旋翼（带运动模糊盘）与全亮 LED。分通道渲染：机身（镂空）、LED（全亮）、
 * 旋翼（半透明，模糊盘出现时桨叶淡出）。模型单位为 1/32 格，渲染器按 MODEL_SCALE 缩放；几何与 UV 与贴图一同生成，需同步修改。
 */
public abstract class DroneModel extends EntityModel<DroneEntity> {
    /** One model unit is half a vanilla pixel. / 一个模型单位等于半个原版像素。 */
    public static final float MODEL_SCALE = 0.5F;
    protected static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    /** Vertex tint for an unlit LED. / 熄灭 LED 的顶点着色。 */
    protected static final int LED_OFF_COLOR = 0xFF3C3C3C;

    private static final String[] CORNERS = {"fr", "fl", "br", "bl"};
    // Adjacent rotors counter-rotate; phase offsets keep the props out of lockstep.
    // 相邻旋翼反向旋转；相位偏移避免四个桨叶同步。
    private static final float[] DIRECTION = {1.0F, -1.0F, -1.0F, 1.0F};
    private static final float[] PHASE = {0.0F, 0.9F, 2.1F, 2.8F};

    protected final ModelPart root;
    private final ModelPart props;
    private final ModelPart blurs;
    private final ModelPart[] propParts = new ModelPart[CORNERS.length];
    private final List<ModelPart> passGroups = new ArrayList<>();

    protected DroneModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        this.props = root.getChild("props");
        this.blurs = root.getChild("blurs");
        for (int i = 0; i < CORNERS.length; i++) {
            propParts[i] = props.getChild("prop_" + CORNERS[i]);
        }
        passGroups.add(props);
        passGroups.add(blurs);
        passGroups.add(root.getChild("lights"));
        if (root.hasChild("halo")) {
            passGroups.add(root.getChild("halo"));
        }
    }

    protected static float deg(float degrees) {
        return degrees * MathHelper.RADIANS_PER_DEGREE;
    }

    @Override
    public void setAngles(DroneEntity drone, float limbAngle, float limbDistance, float animationProgress, float headYaw,
                          float headPitch) {
        // Unused: DroneEntityRenderer poses the model per frame via pose(...). / 未使用：由渲染器每帧调用 pose(...)。
    }

    /**
     * Per-frame pose. {@code rotorAngle} is in radians, {@code clawOpen} is 0 (closed) to 1 (open).
     * 每帧姿态。rotorAngle 为弧度，clawOpen 从 0（闭合）到 1（张开）。
     */
    public void pose(DroneEntity drone, float tickDelta, float rotorAngle, float clawOpen) {
        for (int i = 0; i < propParts.length; i++) {
            propParts[i].yaw = rotorAngle * DIRECTION[i] + PHASE[i];
        }
        poseDetails(drone, tickDelta, clawOpen);
    }

    protected abstract void poseDetails(DroneEntity drone, float tickDelta, float clawOpen);

    /** LED pass; subclasses decide which LEDs are lit. / LED 通道，由子类决定哪些灯亮。 */
    public abstract void renderLights(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                                      DroneEntity drone, float time, int light, int overlay);

    /** Body pass only; rotors and LEDs have their own passes. / 仅机身通道；旋翼与 LED 另有通道。 */
    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        for (ModelPart group : passGroups) {
            group.visible = false;
        }
        root.render(matrices, vertices, light, overlay, color);
        for (ModelPart group : passGroups) {
            group.visible = true;
        }
    }

    /**
     * Translucent rotor pass: blades at {@code bladeAlpha}, blur discs at {@code blurAlpha} (0 hides them).
     * 半透明旋翼通道：桨叶透明度 bladeAlpha，模糊盘透明度 blurAlpha（0 时隐藏）。
     */
    public void renderRotors(MatrixStack matrices, VertexConsumer translucent, int light, int overlay, float bladeAlpha,
                             float blurAlpha) {
        props.render(matrices, translucent, light, overlay, white(bladeAlpha));
        if (blurAlpha > 0.02F) {
            blurs.render(matrices, translucent, light, overlay, white(blurAlpha));
        }
    }

    protected static void renderLed(ModelPart led, MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                                    boolean lit) {
        led.render(matrices, vertices, lit ? FULL_BRIGHT : light, overlay, lit ? -1 : LED_OFF_COLOR);
    }

    private static int white(float alpha) {
        int a = MathHelper.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | 0x00FFFFFF;
    }
}
