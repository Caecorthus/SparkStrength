package annina.sparkstrength.client.role.attendant.flashlight;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.item.FlashlightItem;
import annina.sparkstrength.role.attendant.FlashlightHeldPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Third-person aiming pose of a lit flashlight, shared by the arm mixin and the light tracker so the beam leaves the
 * lens the player model actually draws. Client-only and visual.
 * 已开启手电的第三人称瞄准姿态，由手臂 mixin 与光源追踪共用，使光束从玩家模型实际绘制的镜片射出。仅客户端视觉效果。
 */
public final class FlashlightAim {
    private static @Nullable BakedModel cachedModel;
    private static FlashlightHeldPose.Display rightDisplay = FlashlightHeldPose.Display.FLASHLIGHT;
    private static FlashlightHeldPose.Display leftDisplay = FlashlightHeldPose.Display.FLASHLIGHT;

    private FlashlightAim() {
    }

    /**
     * Standing or crouching holders aim; swimming, crawling, gliding and sleeping keep vanilla arms (and the light
     * falls back to the eye). 站立或潜行时瞄准；游泳、爬行、滑翔与睡眠保持原版手臂（光源退回眼睛位置）。
     */
    public static boolean canAim(LivingEntity entity) {
        EntityPose pose = entity.getPose();
        return (pose == EntityPose.STANDING || pose == EntityPose.CROUCHING) && !entity.isFallFlying();
    }

    /**
     * Arm holding the lit flashlight the light tracker follows: main hand first, as {@link FlashlightItem#isHeldOn}.
     * 光源追踪所跟随的已开启手电所在手臂：优先主手，与 isHeldOn 一致。
     */
    public static Arm litArm(PlayerEntity player) {
        Arm mainArm = player.getMainArm();
        return FlashlightItem.isOn(player.getMainHandStack()) ? mainArm : mainArm.getOpposite();
    }

    /**
     * BipedEntityModel#setAngles tail: every arm holding a lit flashlight rotates rigidly with the head
     * (pitch = head pitch + display X rotation, yaw = head yaw, no walk swing, idle sway or roll).
     * BipedEntityModel#setAngles 末尾：每只持已开启手电的手臂随头部刚性转动（俯仰 = 头部俯仰 + 展示 X 旋转，
     * 偏航 = 头部偏航，无行走摆动、待机晃动与翻滚）。
     */
    public static void poseArms(BipedEntityModel<?> model, LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return;
        }
        boolean mainLit = FlashlightItem.isOn(player.getMainHandStack());
        boolean offLit = FlashlightItem.isOn(player.getOffHandStack());
        if ((!mainLit && !offLit) || !canAim(player)) {
            return;
        }
        Arm mainArm = player.getMainArm();
        if (mainLit) {
            aim(model, mainArm);
        }
        if (offLit) {
            aim(model, mainArm.getOpposite());
        }
    }

    private static void aim(BipedEntityModel<?> model, Arm arm) {
        boolean right = arm == Arm.RIGHT;
        ModelPart part = right ? model.rightArm : model.leftArm;
        part.pitch = (float) FlashlightHeldPose.aimArmPitch(model.head.pitch, display(right));
        part.yaw = model.head.yaw;
        part.roll = 0.0F;
    }

    /**
     * Frame-interpolated lens position of the lit flashlight, mirroring LivingEntityRenderer's interpolation.
     * 已开启手电镜片的逐帧插值位置，与 LivingEntityRenderer 的插值方式一致。
     */
    public static Vec3d lensPosition(PlayerEntity player, float tickDelta, double[] scratch) {
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        boolean right = litArm(player) == Arm.RIGHT;
        FlashlightHeldPose.lensOffset(bodyYaw, MathHelper.wrapDegrees(headYaw - bodyYaw), pitch, right,
                player.isInSneakingPose(), player.getScale(), display(right), scratch);
        Vec3d feet = player.getLerpedPos(tickDelta);
        return new Vec3d(feet.x + scratch[0], feet.y + scratch[1], feet.z + scratch[2]);
    }

    /** Frame-interpolated head look, the axis the aimed item points along. / 逐帧插值的头部朝向，即瞄准后物品的轴线。 */
    public static Vec3d lookDirection(PlayerEntity player, float tickDelta) {
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        return player.getRotationVector(pitch, headYaw);
    }

    /**
     * Third-person display transform read from the baked lit model, so retuning flashlight.json moves the light with
     * the item. Cached per baked model (a resource reload swaps it).
     * 从烘焙后的开启模型读取第三人称展示变换，调整 flashlight.json 时光源随物品移动。按烘焙模型缓存（资源重载会替换它）。
     */
    static FlashlightHeldPose.Display display(boolean rightArm) {
        BakedModel model = MinecraftClient.getInstance().getItemRenderer().getModels()
                .getModel(SparkStrengthItems.flashlight());
        if (model != cachedModel) {
            cachedModel = model;
            ModelTransformation transformation = litModel(model).getTransformation();
            rightDisplay = toDisplay(transformation.thirdPersonRightHand);
            leftDisplay = toDisplay(transformation.thirdPersonLeftHand);
        }
        return rightArm ? rightDisplay : leftDisplay;
    }

    private static BakedModel litModel(BakedModel model) {
        ItemStack lit = new ItemStack(SparkStrengthItems.flashlight());
        FlashlightItem.setOn(lit, true);
        BakedModel resolved = model.getOverrides().apply(model, lit, null, null, 0);
        return resolved != null ? resolved : model;
    }

    private static FlashlightHeldPose.Display toDisplay(Transformation transformation) {
        return new FlashlightHeldPose.Display(
                transformation.rotation.x(), transformation.rotation.y(), transformation.rotation.z(),
                transformation.translation.x(), transformation.translation.y(), transformation.translation.z(),
                transformation.scale.x(), transformation.scale.y(), transformation.scale.z());
    }
}
