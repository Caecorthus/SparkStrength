package annina.sparkstrength.client.renderer;

import annina.sparkstrength.entity.TaotieHeadEntity;
import annina.sparkstrength.role.taotie.TaotieHeadRules;
import net.minecraft.block.SkullBlock;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders the Taotie head as a real 3D player skull (hat layer included) whose face points along the flight direction.
 * 把饕餮头颅渲染为真实的 3D 玩家头颅（含帽子层），脸朝飞行方向。
 *
 * <p>The skin comes from the synced head stack's {@code PROFILE} component through vanilla's skull render layer, so a
 * missing profile falls back to the default Steve skin. Trail particles are spawned by the entity's own client tick,
 * not here.
 * 皮肤取自同步头颅物品的 PROFILE 组件，经原版头颅渲染层解析；没有 profile 时回落为默认 Steve 皮肤。
 * 尾迹粒子由实体自身的客户端 tick 生成，不在此处生成。</p>
 */
public final class TaotieHeadEntityRenderer extends EntityRenderer<TaotieHeadEntity> {
    /** Half of the 8-pixel skull cube: the model pivot sits at its bottom face. / 8 像素头颅立方体的一半：模型枢轴位于底面。 */
    private static final float MODEL_HALF_HEIGHT = 4.0F / 16.0F;
    /** Entity origin is the box bottom centre. / 实体原点位于碰撞箱底面中心。 */
    private static final float BOX_CENTRE_HEIGHT = (float) TaotieHeadRules.HEAD_HALF_SIZE;

    private final SkullBlockEntityModel model;

    public TaotieHeadEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new SkullEntityModel(context.getPart(EntityModelLayers.PLAYER_HEAD));
    }

    @Override
    public Identifier getTexture(TaotieHeadEntity entity) {
        return DefaultSkinHelper.getTexture();
    }

    @Override
    public void render(TaotieHeadEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        ProfileComponent profile = entity.getHeadStack().get(DataComponentTypes.PROFILE);
        RenderLayer layer = SkullBlockEntityRenderer.getRenderLayer(SkullBlock.Type.PLAYER, profile);
        // prevYaw/prevPitch are only valid after the first client tick. / prevYaw/prevPitch 在首个客户端 tick 后才有效。
        float delta = entity.age == 0 ? 1.0F : tickDelta;
        float headYaw = MathHelper.lerpAngleDegrees(delta, entity.prevYaw, entity.getYaw());
        float headPitch = MathHelper.lerp(delta, entity.prevPitch, entity.getPitch());

        matrices.push();
        // Rotate about the box centre; the skull face is local -Z after the model flip below. TaotieHeadEntity keeps
        // yaw/pitch in the projectile convention (yaw = atan2(vx, vz), pitch > 0 = up), so yaw + 180 and pitch map -Z
        // onto the velocity.
        // 绕碰撞箱中心旋转；下方模型翻转后脸朝局部 -Z。TaotieHeadEntity 的 yaw/pitch 采用弹射物约定
        // （yaw = atan2(vx, vz)，pitch > 0 为向上），因此 yaw + 180 与 pitch 使 -Z 对准速度方向。
        matrices.translate(0.0F, BOX_CENTRE_HEIGHT, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(headYaw + 180.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(headPitch));
        matrices.translate(0.0F, -MODEL_HALF_HEIGHT, 0.0F);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        model.setHeadRotation(0.0F, 0.0F, 0.0F);
        model.render(matrices, vertexConsumers.getBuffer(layer), light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
}
