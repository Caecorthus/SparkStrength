package annina.sparkstrength.client.renderer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * The Partygoer: the wide player model (so the skin's jacket and sleeve overlays show, which the vanilla zombie model
 * lacks) posed like a zombie, arms reaching forward. Eating bobs the head; the rage stun shakes it.
 * 派对客：宽手臂玩家模型（这样皮肤的外套与袖子外层能显示，原版僵尸模型没有外层），摆成僵尸姿势、双臂前伸。进食时点头，狂怒愣住时摇头。
 */
public final class PartygoerModel extends PlayerEntityModel<PartygoerEntity> {
    /**
     * Its own copy of the wide player layer. Entity Model Features picks a model by layer name, so on
     * {@code minecraft:player} a resource pack's player.jem (Fresh Animations' player extension...) restyled it too and
     * hid parts of it.
     * 宽手臂玩家模型层的独立副本。Entity Model Features 按模型层名挑模型，用 minecraft:player 时资源包的 player.jem
     * （Fresh Animations 的玩家扩展等）也会套到它身上，把部分肢体藏掉。
     */
    public static final EntityModelLayer LAYER = new EntityModelLayer(SparkStrength.id("partygoer"), "main");
    /** Arm pitch while holding a player: hands at their chest. / 抱人时的手臂俯仰：两手在被抱者胸口高度。 */
    private static final float HOLD_PITCH = -1.15F;
    /**
     * Outward arm turn while holding a player (positive yaw turns the right arm outward), so its hands pass outside theirs.
     * 抱人时手臂外张的角度（正 yaw 让右臂向外），让两手从被抱者手臂外侧经过。
     */
    private static final float HOLD_SPREAD = 0.5F;

    public PartygoerModel(ModelPart root) {
        super(root, false);
    }

    /** The vanilla wide player geometry. / 原版宽手臂玩家几何。 */
    public static TexturedModelData getTexturedModelData() {
        return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64);
    }

    @Override
    public void setAngles(PartygoerEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        PartygoerState state = entity.partyState();
        boolean holding = entity.hasPassengers();
        // Arms stay level while holding someone, even in a rage. / 抱着人时手臂保持水平，狂怒时也一样。
        CrossbowPosing.meleeAttack(leftArm, rightArm, state.raging() && !holding, handSwingProgress, animationProgress);
        if (holding) {
            // Arms down to chest height and turned outward, so the held player stands between its hands.
            // 手臂降到胸口高度并向外张开，让被抱的玩家站在它两手之间。
            rightArm.pitch = HOLD_PITCH;
            leftArm.pitch = HOLD_PITCH;
            rightArm.yaw += HOLD_SPREAD;
            leftArm.yaw -= HOLD_SPREAD;
        }
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
