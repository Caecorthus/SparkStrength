package annina.sparkstrength.client.renderer;

import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The Partygoer: the wide player model (so the skin's jacket and sleeve overlays show, which the vanilla zombie model
 * lacks) posed like a zombie, arms reaching forward. Eating bobs the head; the rage stun shakes it.
 * 派对客：宽手臂玩家模型（这样皮肤的外套与袖子外层能显示，原版僵尸模型没有外层），摆成僵尸姿势、双臂前伸。进食时点头，狂怒愣住时摇头。
 */
public final class PartygoerModel extends PlayerEntityModel<PartygoerEntity> {
    public PartygoerModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(PartygoerEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        PartygoerState state = entity.partyState();
        CrossbowPosing.meleeAttack(leftArm, rightArm, state.raging(), handSwingProgress, animationProgress);
        if (state.eating()) {
            head.pitch = 0.35F + MathHelper.sin(animationProgress * 0.9F) * 0.25F;
        } else if (state == PartygoerState.RAGE_STUN) {
            head.yaw += MathHelper.sin(animationProgress * 2.6F) * 0.35F;
        }
        hat.copyTransform(head);
        leftSleeve.copyTransform(leftArm);
        rightSleeve.copyTransform(rightArm);
    }
}
