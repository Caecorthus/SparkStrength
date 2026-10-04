package annina.sparkstrength.client.renderer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneState;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.EnumSet;

/**
 * Grenade drone (投弹无人机): light-grey consumer quadcopter with a pitching camera gimbal, splayed landing skids and an
 * underslung claw that holds the bound M67 (the grenade itself is drawn by {@link DroneEntityRenderer}). Nav LEDs follow
 * aviation convention: green on the right, red on the left, white double-flash tail strobe while airborne.
 * 投弹无人机：浅灰色消费级四旋翼，带随视角俯仰的云台相机、外八字起落架与机腹挂爪（挂载的 M67 由渲染器绘制）。
 * 航行灯沿用航空惯例：右绿左红，空中时尾部白色双闪。
 */
public final class GrenadeDroneModel extends DroneModel {
    public static final EntityModelLayer LAYER = new EntityModelLayer(SparkStrength.id("grenade_drone"), "main");
    public static final Identifier TEXTURE = SparkStrength.id("textures/entity/grenade_drone.png");
    private static final float CLAW_OPEN = 0.6F;
    private static final float TAIL_STROBE_PERIOD = 30.0F;

    private final ModelPart camera;
    private final ModelPart clawFront;
    private final ModelPart clawBack;
    private final ModelPart ledRight;
    private final ModelPart ledLeft;
    private final ModelPart ledTail;

    public GrenadeDroneModel(ModelPart root) {
        super(root);
        this.camera = root.getChild("gimbal").getChild("camera");
        ModelPart claw = root.getChild("claw");
        this.clawFront = claw.getChild("claw_front");
        this.clawBack = claw.getChild("claw_back");
        ModelPart lights = root.getChild("lights");
        this.ledRight = lights.getChild("led_right");
        this.ledLeft = lights.getChild("led_left");
        this.ledTail = lights.getChild("led_tail");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        // Generated with the texture (units 1/32 block, +y down, nose toward -z).
        // 与贴图一同生成（单位 1/32 格，+y 向下，机头朝 -z）。
        root.addChild("body", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-4.0F, -10.0F, -6.0F, 8.0F, 4.0F, 12.0F)
                .uv(30, 16).cuboid(-3.0F, -11.0F, -3.0F, 6.0F, 1.0F, 8.0F)
                .uv(46, 26).cuboid(-3.0F, -9.5F, -7.0F, 6.0F, 3.0F, 1.0F)
                .uv(0, 35).cuboid(-1.5F, -6.0F, -1.5F, 3.0F, 1.0F, 3.0F), ModelTransform.NONE);
        root.addChild("arm_fr", ModelPartBuilder.create()
                .uv(0, 26).cuboid(-1.0F, -1.0F, -10.0F, 2.0F, 2.0F, 7.0F), ModelTransform.of(0.0F, -9.0F, 0.0F, 0.0F, deg(45.0F), 0.0F));
        root.addChild("arm_fl", ModelPartBuilder.create()
                .uv(0, 26).cuboid(-1.0F, -1.0F, -10.0F, 2.0F, 2.0F, 7.0F), ModelTransform.of(0.0F, -9.0F, 0.0F, 0.0F, deg(-45.0F), 0.0F));
        root.addChild("arm_br", ModelPartBuilder.create()
                .uv(0, 26).cuboid(-1.0F, -1.0F, -10.0F, 2.0F, 2.0F, 7.0F), ModelTransform.of(0.0F, -9.0F, 0.0F, 0.0F, deg(135.0F), 0.0F));
        root.addChild("arm_bl", ModelPartBuilder.create()
                .uv(0, 26).cuboid(-1.0F, -1.0F, -10.0F, 2.0F, 2.0F, 7.0F), ModelTransform.of(0.0F, -9.0F, 0.0F, 0.0F, deg(-135.0F), 0.0F));
        root.addChild("motor_fr", ModelPartBuilder.create()
                .uv(30, 26).cuboid(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
                .uv(36, 35).cuboid(-0.5F, -2.25F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(-7.0F, -9.5F, -7.0F));
        root.addChild("motor_fl", ModelPartBuilder.create()
                .uv(30, 26).cuboid(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
                .uv(36, 35).cuboid(-0.5F, -2.25F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(7.0F, -9.5F, -7.0F));
        root.addChild("motor_br", ModelPartBuilder.create()
                .uv(30, 26).cuboid(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
                .uv(36, 35).cuboid(-0.5F, -2.25F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(-7.0F, -9.5F, 7.0F));
        root.addChild("motor_bl", ModelPartBuilder.create()
                .uv(30, 26).cuboid(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
                .uv(36, 35).cuboid(-0.5F, -2.25F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(7.0F, -9.5F, 7.0F));
        ModelPartData gimbal = root.addChild("gimbal", ModelPartBuilder.create()
                .uv(16, 35).cuboid(-2.5F, 0.0F, -1.5F, 5.0F, 1.0F, 2.0F)
                .uv(12, 35).cuboid(-2.5F, 1.0F, -1.0F, 1.0F, 3.0F, 1.0F)
                .uv(12, 35).cuboid(1.5F, 1.0F, -1.0F, 1.0F, 3.0F, 1.0F), ModelTransform.pivot(0.0F, -6.0F, -6.5F));
        gimbal.addChild("camera", ModelPartBuilder.create()
                .uv(18, 26).cuboid(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), ModelTransform.pivot(0.0F, 2.5F, -0.5F));
        root.addChild("leg_fr", ModelPartBuilder.create()
                .uv(42, 26).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), ModelTransform.of(-3.0F, -6.0F, -3.5F, 0.0F, 0.0F, deg(15.0F)));
        root.addChild("leg_fl", ModelPartBuilder.create()
                .uv(42, 26).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), ModelTransform.of(3.0F, -6.0F, -3.5F, 0.0F, 0.0F, deg(-15.0F)));
        root.addChild("leg_br", ModelPartBuilder.create()
                .uv(42, 26).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), ModelTransform.of(-3.0F, -6.0F, 3.5F, 0.0F, 0.0F, deg(15.0F)));
        root.addChild("leg_bl", ModelPartBuilder.create()
                .uv(42, 26).cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F), ModelTransform.of(3.0F, -6.0F, 3.5F, 0.0F, 0.0F, deg(-15.0F)));
        root.addChild("skid_right", ModelPartBuilder.create()
                .uv(40, 0).cuboid(-0.5F, -1.0F, -5.5F, 1.0F, 1.0F, 11.0F), ModelTransform.pivot(-4.3F, 0.0F, 0.0F));
        root.addChild("skid_left", ModelPartBuilder.create()
                .uv(40, 0).cuboid(-0.5F, -1.0F, -5.5F, 1.0F, 1.0F, 11.0F), ModelTransform.pivot(4.3F, 0.0F, 0.0F));
        ModelPartData claw = root.addChild("claw", ModelPartBuilder.create(), ModelTransform.pivot(0.0F, -5.0F, 0.0F));
        claw.addChild("claw_front", ModelPartBuilder.create()
                .uv(30, 35).cuboid(-1.0F, 0.0F, -0.5F, 2.0F, 2.0F, 1.0F), ModelTransform.pivot(0.0F, 0.0F, -1.0F));
        claw.addChild("claw_back", ModelPartBuilder.create()
                .uv(30, 35).cuboid(-1.0F, 0.0F, -0.5F, 2.0F, 2.0F, 1.0F), ModelTransform.pivot(0.0F, 0.0F, 1.0F));
        ModelPartData props = root.addChild("props", ModelPartBuilder.create(), ModelTransform.NONE);
        props.addChild("prop_fr", ModelPartBuilder.create()
                .uv(10, 16).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-7.0F, -11.25F, -7.0F));
        props.addChild("prop_fl", ModelPartBuilder.create()
                .uv(10, 16).mirrored(true).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(7.0F, -11.25F, -7.0F));
        props.addChild("prop_br", ModelPartBuilder.create()
                .uv(10, 16).mirrored(true).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-7.0F, -11.25F, 7.0F));
        props.addChild("prop_bl", ModelPartBuilder.create()
                .uv(10, 16).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(7.0F, -11.25F, 7.0F));
        ModelPartData blurs = root.addChild("blurs", ModelPartBuilder.create(), ModelTransform.NONE);
        blurs.addChild("blur_fr", ModelPartBuilder.create()
                .uv(0, 16).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-7.0F, -11.4F, -7.0F));
        blurs.addChild("blur_fl", ModelPartBuilder.create()
                .uv(0, 16).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(7.0F, -11.4F, -7.0F));
        blurs.addChild("blur_br", ModelPartBuilder.create()
                .uv(0, 16).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-7.0F, -11.4F, 7.0F));
        blurs.addChild("blur_bl", ModelPartBuilder.create()
                .uv(0, 16).cuboid(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(7.0F, -11.4F, 7.0F));
        ModelPartData lights = root.addChild("lights", ModelPartBuilder.create(), ModelTransform.NONE);
        lights.addChild("led_right", ModelPartBuilder.create()
                .uv(40, 35).cuboid(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(-7.0F, -7.5F, -7.0F));
        lights.addChild("led_left", ModelPartBuilder.create()
                .uv(44, 35).cuboid(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(7.0F, -7.5F, -7.0F));
        lights.addChild("led_tail", ModelPartBuilder.create()
                .uv(48, 35).cuboid(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(0.0F, -8.0F, 6.0F));
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    protected void poseDetails(DroneEntity drone, float tickDelta, float clawOpen) {
        // The gimbal camera follows the pilot's look pitch. / 云台相机跟随驾驶者俯仰视角。
        camera.pitch = deg(MathHelper.clamp(drone.getPitch(tickDelta), -30.0F, 60.0F));
        clawFront.pitch = -CLAW_OPEN * clawOpen;
        clawBack.pitch = CLAW_OPEN * clawOpen;
    }

    @Override
    public void renderLights(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                             DroneEntity drone, float time, int light, int overlay) {
        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        DroneState state = drone.state();
        boolean powered = drone.charge() > 0 && state != DroneState.FALLING;
        float phase = time % TAIL_STROBE_PERIOD;
        boolean strobe = state.rotorsOn() && (phase < 1.5F || (phase >= 4.0F && phase < 5.5F));
        renderLed(ledRight, matrices, vertices, light, overlay, powered);
        renderLed(ledLeft, matrices, vertices, light, overlay, powered);
        renderLed(ledTail, matrices, vertices, light, overlay, powered && strobe);
    }
}
