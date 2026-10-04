package annina.sparkstrength.client.renderer;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import annina.sparkstrength.role.bomber.drone.DroneState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders both Bomber drones for every viewer (drones are not hidden; only held drone items are). The pilot never sees
 * its own drone: it is the camera entity, which vanilla skips in first person. Animation is cosmetic and client-only:
 * rotor spin and hover bob come from {@link DroneEntity}; banking into the direction of travel, the falling tumble and
 * the claw are smoothed here per entity.
 * 为所有观察者渲染两种无人机（无人机本身不隐藏，只隐藏手持的无人机物品）。驾驶者看不到自己的无人机：它是相机实体，
 * 原版在第一人称下不渲染。动画仅为客户端外观：旋翼转速与悬停浮动来自 DroneEntity；随运动方向倾斜、下坠翻滚与挂爪在此逐实体平滑。
 */
public final class DroneEntityRenderer extends EntityRenderer<DroneEntity> {
    /** Matches DroneEntity's cosmetic rotor top speed (radians per tick). / 与 DroneEntity 旋翼外观最高转速一致（弧度/刻）。 */
    private static final float FULL_ROTOR_SPEED = 1.6F;
    private static final float MAX_TILT_DEGREES = 14.0F;
    private static final float TILT_TIME_CONSTANT = 3.0F;
    private static final float CLAW_SPEED = 0.25F;
    /** M67 sprite size and its centre height above the drone origin (claw grip). / M67 贴图尺寸及其中心离无人机原点高度（挂爪处）。 */
    private static final float PAYLOAD_SCALE = 0.18F;
    private static final float PAYLOAD_HEIGHT = 2.6F / 32.0F;

    private final DroneKind kind;
    private final DroneModel model;
    private final Identifier texture;
    private final ItemRenderer itemRenderer;
    private final Map<DroneEntity, Motion> motions = new WeakHashMap<>();
    private ItemStack payloadStack;

    private DroneEntityRenderer(EntityRendererFactory.Context context, DroneKind kind, DroneModel model,
                                Identifier texture) {
        super(context);
        this.kind = kind;
        this.model = model;
        this.texture = texture;
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.3F;
        this.shadowOpacity = 0.6F;
    }

    public static DroneEntityRenderer grenade(EntityRendererFactory.Context context) {
        return new DroneEntityRenderer(context, DroneKind.GRENADE,
                new GrenadeDroneModel(context.getPart(GrenadeDroneModel.LAYER)), GrenadeDroneModel.TEXTURE);
    }

    public static DroneEntityRenderer bomb(EntityRendererFactory.Context context) {
        return new DroneEntityRenderer(context, DroneKind.BOMB,
                new BombDroneModel(context.getPart(BombDroneModel.LAYER)), BombDroneModel.TEXTURE);
    }

    @Override
    public Identifier getTexture(DroneEntity drone) {
        return texture;
    }

    @Override
    public void render(DroneEntity drone, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, drone.prevYaw, drone.getYaw());
        float time = drone.age + tickDelta;
        Motion motion = motions.computeIfAbsent(drone, ignored -> new Motion());
        motion.update(drone, kind, bodyYaw, time);
        float spin = MathHelper.clamp((drone.rotorAngle(1.0F) - drone.rotorAngle(0.0F)) / FULL_ROTOR_SPEED, 0.0F, 1.0F);
        float blur = spin * spin * (3.0F - 2.0F * spin);
        float center = drone.getHeight() * 0.5F;

        matrices.push();
        matrices.translate(0.0F, drone.bobOffset(tickDelta), 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
        // Bank around the hull centre. / 绕机身中心倾斜。
        matrices.translate(0.0F, center, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(motion.tiltPitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(motion.tiltRoll));
        matrices.translate(0.0F, -center, 0.0F);

        if (kind == DroneKind.GRENADE && drone.hasPayload()) {
            renderPayload(drone, bodyYaw, motion, matrices, vertexConsumers, light);
        }

        matrices.scale(-DroneModel.MODEL_SCALE, -DroneModel.MODEL_SCALE, DroneModel.MODEL_SCALE);
        int overlay = OverlayTexture.DEFAULT_UV;
        model.pose(drone, tickDelta, drone.rotorAngle(tickDelta), motion.clawOpen);
        model.render(matrices, vertexConsumers.getBuffer(model.getLayer(texture)), light, overlay, -1);
        model.renderLights(matrices, vertexConsumers, texture, drone, time, light, overlay);
        model.renderRotors(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), light,
                overlay, 1.0F - 0.5F * blur, 0.9F * blur);
        matrices.pop();
        super.render(drone, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * The bound M67 hangs plumb in the claw and turns to face the viewer, so the flat item sprite always reads.
     * 挂载的 M67 在挂爪中竖直悬挂并转向观察者，使扁平物品贴图始终清晰可辨。
     */
    private void renderPayload(DroneEntity drone, float bodyYaw, Motion motion, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int light) {
        if (payloadStack == null) {
            payloadStack = new ItemStack(SparkStrengthItems.m67());
        }
        matrices.push();
        matrices.translate(0.0F, PAYLOAD_HEIGHT, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-motion.tiltRoll));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-motion.tiltPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - 180.0F - dispatcher.camera.getYaw()));
        matrices.scale(PAYLOAD_SCALE, PAYLOAD_SCALE, PAYLOAD_SCALE);
        itemRenderer.renderItem(payloadStack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, matrices,
                vertexConsumers, drone.getWorld(), drone.getId());
        matrices.pop();
    }

    /** Per-entity smoothed pose, render thread only. / 逐实体平滑姿态，仅渲染线程使用。 */
    private static final class Motion {
        private float lastTime = Float.NaN;
        private float tiltPitch;
        private float tiltRoll;
        private float clawOpen;

        private void update(DroneEntity drone, DroneKind kind, float bodyYaw, float time) {
            float dt;
            if (Float.isNaN(lastTime)) {
                dt = 0.0F;
                clawOpen = drone.hasPayload() ? 0.0F : 1.0F;
            } else {
                dt = MathHelper.clamp(time - lastTime, 0.0F, 10.0F);
            }
            lastTime = time;

            float targetPitch = 0.0F;
            float targetRoll = 0.0F;
            DroneState state = drone.state();
            if (state == DroneState.FALLING) {
                // Unpowered tumble. / 断电翻滚。
                targetPitch = MathHelper.cos(time * 0.37F) * 14.0F;
                targetRoll = MathHelper.sin(time * 0.5F) * 20.0F;
            } else if (state.rotorsOn()) {
                // Nose dips into forward motion, banks into sideways motion. / 前进时机头下压，横移时侧倾。
                float yawRad = bodyYaw * MathHelper.RADIANS_PER_DEGREE;
                float sin = MathHelper.sin(yawRad);
                float cos = MathHelper.cos(yawRad);
                float dx = (float) (drone.getX() - drone.prevX);
                float dz = (float) (drone.getZ() - drone.prevZ);
                float forward = -dx * sin + dz * cos;
                float right = -dx * cos - dz * sin;
                float max = (float) DroneRules.horizontalSpeed(kind);
                targetPitch = -MathHelper.clamp(forward / max, -1.0F, 1.0F) * MAX_TILT_DEGREES;
                targetRoll = -MathHelper.clamp(right / max, -1.0F, 1.0F) * MAX_TILT_DEGREES;
            }
            float k = 1.0F - (float) Math.exp(-dt / TILT_TIME_CONSTANT);
            tiltPitch += (targetPitch - tiltPitch) * k;
            tiltRoll += (targetRoll - tiltRoll) * k;

            float clawTarget = drone.hasPayload() ? 0.0F : 1.0F;
            float step = dt * CLAW_SPEED;
            clawOpen += MathHelper.clamp(clawTarget - clawOpen, -step, step);
        }
    }
}
