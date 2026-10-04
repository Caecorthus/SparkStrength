package annina.sparkstrength.client.renderer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.EnumSet;

/**
 * Bomb drone (炸弹无人机): compact carbon FPV racer with orange three-blade props, an up-tilted FPV camera, a rear VTX
 * antenna and a red taped explosive brick slung underneath. The detonator's red LED blinks, fast below 20% battery.
 * 炸弹无人机：紧凑的碳纤维 FPV 穿越机，橙色三叶桨、上仰的 FPV 相机、尾部图传天线，机腹绑着缠胶带的红色炸药块。
 * 起爆器红灯闪烁，电量低于 20% 时快闪。
 */
public final class BombDroneModel extends DroneModel {
    public static final EntityModelLayer LAYER = new EntityModelLayer(SparkStrength.id("bomb_drone"), "main");
    public static final Identifier TEXTURE = SparkStrength.id("textures/entity/bomb_drone.png");
    private static final int LOW_BATTERY_CHARGE = DroneRules.CHARGE_MAX / 5;
    private static final float BLINK_PERIOD = 20.0F;
    private static final float FAST_BLINK_PERIOD = 6.0F;
    private static final float BLINK_ON = 3.0F;
    /** Additive glow strength around a lit LED. / 亮灯时外圈叠加辉光强度。 */
    private static final int HALO_COLOR = 0xFF8C8C8C;

    private final ModelPart ledArmed;
    private final ModelPart ledHalo;

    public BombDroneModel(ModelPart root) {
        super(root);
        this.ledArmed = root.getChild("lights").getChild("led_armed");
        this.ledHalo = root.getChild("halo").getChild("led_halo");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        // Generated with the texture (units 1/32 block, +y down, nose toward -z).
        // 与贴图一同生成（单位 1/32 格，+y 向下，机头朝 -z）。
        root.addChild("frame", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-3.0F, -4.0F, -7.0F, 6.0F, 1.0F, 11.0F)
                .uv(22, 12).cuboid(-2.0F, -7.0F, -4.0F, 4.0F, 1.0F, 8.0F)
                .uv(36, 22).cuboid(-1.5F, -6.0F, -2.0F, 3.0F, 2.0F, 4.0F)
                .uv(0, 22).cuboid(-2.0F, -9.0F, -4.0F, 4.0F, 2.0F, 6.0F)
                .uv(36, 30).cuboid(-2.0F, -6.0F, -4.0F, 1.0F, 2.0F, 1.0F)
                .uv(36, 30).cuboid(1.0F, -6.0F, -4.0F, 1.0F, 2.0F, 1.0F)
                .uv(36, 30).cuboid(-2.0F, -6.0F, 3.0F, 1.0F, 2.0F, 1.0F)
                .uv(36, 30).cuboid(1.0F, -6.0F, 3.0F, 1.0F, 2.0F, 1.0F)
                .uv(0, 30).cuboid(-2.5F, -7.0F, -7.0F, 1.0F, 3.0F, 3.0F)
                .uv(0, 30).mirrored(true).cuboid(1.5F, -7.0F, -7.0F, 1.0F, 3.0F, 3.0F), ModelTransform.NONE);
        root.addChild("payload", ModelPartBuilder.create()
                .uv(34, 0).cuboid(-2.5F, -3.0F, -3.5F, 5.0F, 3.0F, 7.0F)
                .uv(24, 30).cuboid(-1.0F, -2.5F, -4.5F, 2.0F, 2.0F, 1.0F), ModelTransform.NONE);
        root.addChild("arm_fr", ModelPartBuilder.create()
                .uv(0, 12).cuboid(-1.0F, -0.5F, -10.0F, 2.0F, 1.0F, 9.0F), ModelTransform.of(0.0F, -3.5F, 0.0F, 0.0F, deg(45.0F), 0.0F));
        root.addChild("arm_fl", ModelPartBuilder.create()
                .uv(0, 12).cuboid(-1.0F, -0.5F, -10.0F, 2.0F, 1.0F, 9.0F), ModelTransform.of(0.0F, -3.5F, 0.0F, 0.0F, deg(-45.0F), 0.0F));
        root.addChild("arm_br", ModelPartBuilder.create()
                .uv(0, 12).cuboid(-1.0F, -0.5F, -10.0F, 2.0F, 1.0F, 9.0F), ModelTransform.of(0.0F, -3.5F, 0.0F, 0.0F, deg(135.0F), 0.0F));
        root.addChild("arm_bl", ModelPartBuilder.create()
                .uv(0, 12).cuboid(-1.0F, -0.5F, -10.0F, 2.0F, 1.0F, 9.0F), ModelTransform.of(0.0F, -3.5F, 0.0F, 0.0F, deg(-135.0F), 0.0F));
        root.addChild("motor_fr", ModelPartBuilder.create()
                .uv(8, 30).cuboid(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F)
                .uv(44, 30).cuboid(-0.5F, -1.75F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(-6.5F, -5.0F, -6.5F));
        root.addChild("motor_fl", ModelPartBuilder.create()
                .uv(8, 30).cuboid(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F)
                .uv(44, 30).cuboid(-0.5F, -1.75F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(6.5F, -5.0F, -6.5F));
        root.addChild("motor_br", ModelPartBuilder.create()
                .uv(8, 30).cuboid(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F)
                .uv(44, 30).cuboid(-0.5F, -1.75F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(-6.5F, -5.0F, 6.5F));
        root.addChild("motor_bl", ModelPartBuilder.create()
                .uv(8, 30).cuboid(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F)
                .uv(44, 30).cuboid(-0.5F, -1.75F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(6.5F, -5.0F, 6.5F));
        root.addChild("fpv_camera", ModelPartBuilder.create()
                .uv(50, 22).cuboid(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F)
                .uv(30, 30).cuboid(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 1.0F), ModelTransform.of(0.0F, -5.5F, -5.5F, deg(-20.0F), 0.0F, 0.0F));
        root.addChild("antenna", ModelPartBuilder.create()
                .uv(20, 30).cuboid(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                .uv(40, 30).cuboid(-0.5F, -4.0F, -0.5F, 1.0F, 1.0F, 1.0F, new Dilation(0.2F)), ModelTransform.of(0.0F, -7.0F, 3.5F, deg(-20.0F), 0.0F, 0.0F));
        ModelPartData props = root.addChild("props", ModelPartBuilder.create(), ModelTransform.NONE);
        props.addChild("prop_fr", ModelPartBuilder.create()
                .uv(20, 22).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-6.5F, -6.25F, -6.5F));
        props.addChild("prop_fl", ModelPartBuilder.create()
                .uv(20, 22).mirrored(true).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(6.5F, -6.25F, -6.5F));
        props.addChild("prop_br", ModelPartBuilder.create()
                .uv(20, 22).mirrored(true).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-6.5F, -6.25F, 6.5F));
        props.addChild("prop_bl", ModelPartBuilder.create()
                .uv(20, 22).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(6.5F, -6.25F, 6.5F));
        ModelPartData blurs = root.addChild("blurs", ModelPartBuilder.create(), ModelTransform.NONE);
        blurs.addChild("blur_fr", ModelPartBuilder.create()
                .uv(12, 22).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-6.5F, -6.4F, -6.5F));
        blurs.addChild("blur_fl", ModelPartBuilder.create()
                .uv(12, 22).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(6.5F, -6.4F, -6.5F));
        blurs.addChild("blur_br", ModelPartBuilder.create()
                .uv(12, 22).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(-6.5F, -6.4F, 6.5F));
        blurs.addChild("blur_bl", ModelPartBuilder.create()
                .uv(12, 22).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 0.0F, 8.0F, EnumSet.of(Direction.DOWN)), ModelTransform.pivot(6.5F, -6.4F, 6.5F));
        ModelPartData lights = root.addChild("lights", ModelPartBuilder.create(), ModelTransform.NONE);
        lights.addChild("led_armed", ModelPartBuilder.create()
                .uv(48, 30).cuboid(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F), ModelTransform.pivot(0.0F, -1.5F, -5.0F));
        ModelPartData halo = root.addChild("halo", ModelPartBuilder.create(), ModelTransform.NONE);
        halo.addChild("led_halo", ModelPartBuilder.create()
                .uv(48, 30).cuboid(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new Dilation(0.45F)), ModelTransform.pivot(0.0F, -1.5F, -5.0F));
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    protected void poseDetails(DroneEntity drone, float tickDelta, float clawOpen) {
    }

    @Override
    public void renderLights(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture,
                             DroneEntity drone, float time, int light, int overlay) {
        boolean low = drone.charge() < LOW_BATTERY_CHARGE;
        boolean lit = time % (low ? FAST_BLINK_PERIOD : BLINK_PERIOD) < BLINK_ON;
        renderLed(ledArmed, matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), light,
                overlay, lit);
        if (lit) {
            ledHalo.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEyes(texture)), FULL_BRIGHT, overlay,
                    HALO_COLOR);
        }
    }
}
