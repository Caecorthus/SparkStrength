package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.SparkStrengthItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the skateboard item under a riding player's feet, at real size with its nose (model +Z) along the body yaw
 * and its wheels (model y = 0) on the ground. Other players get it from a player-renderer feature; the camera's own
 * player, which first person never renders, gets it in world space. Both paths end in the same frame: origin at the
 * entity's feet, +Y up, +Z = facing, scaled by the entity scale.
 * 在滑行玩家脚下绘制滑板物品：真实尺寸，板头（模型 +Z）朝向身体朝向，轮子（模型 y = 0）贴地。其他玩家由玩家渲染器特征
 * 绘制；第一人称不渲染的镜头玩家本人改在世界空间绘制。两条路径最终坐标系一致：原点在实体脚下，+Y 向上，+Z 为朝向，
 * 按实体缩放比例缩放。
 */
public final class SkateboardRenderer {
    /**
     * Height of the deck top in the item model (model units / 16); the rider is lifted by exactly this much.
     * 物品模型中板面顶部高度（模型单位 / 16）；骑手被抬高的高度与此相同。
     */
    public static final float DECK_TOP_HEIGHT = 3.0F / 16.0F;

    /** {@code PlayerEntityRenderer#scale}. / 玩家渲染器的模型缩放。 */
    private static final float PLAYER_MODEL_SCALE = 0.9375F;
    /** {@code LivingEntityRenderer#render} lowers the model by this after scaling. / 生物渲染器缩放后下移模型的距离。 */
    private static final float MODEL_FOOT_OFFSET = 1.501F;

    private static ItemStack boardStack;

    private SkateboardRenderer() {
    }

    /**
     * Riding and in a pose whose body stays upright; swimming, gliding, sleeping or dying tilt the body frame.
     * 正在滑行且身体保持直立的姿态；游泳、滑翔、睡觉或死亡会使身体坐标系倾斜。
     */
    public static boolean standsOnBoard(PlayerEntity player) {
        EntityPose pose = player.getPose();
        return (pose == EntityPose.STANDING || pose == EntityPose.CROUCHING) && SkateboardClient.isRiding(player);
    }

    /**
     * First person never renders the camera's player, so its board is drawn here in world space; third person is left
     * to {@link Feature}. Covers spectating a rider too, since it follows the camera entity.
     * 第一人称从不渲染镜头所属玩家，因此其滑板在此以世界坐标绘制；第三人称交给 {@link Feature}。由于跟随镜头实体，
     * 旁观骑手时同样适用。
     */
    static void renderCameraBoard(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.camera().isThirdPerson()
                || !(client.getCameraEntity() instanceof AbstractClientPlayerEntity player)
                || !standsOnBoard(player)) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        float scale = player.getScale();
        if (matrices == null || consumers == null || scale <= 0.0F) {
            return;
        }
        // Same interpolation as WorldRenderer#renderEntity, so the board stays glued to the camera's feet.
        // 与 WorldRenderer#renderEntity 相同的插值，滑板紧贴镜头玩家的脚。
        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d camera = context.camera().getPos();
        double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - camera.x;
        double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - camera.y;
        double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - camera.z;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        int light = client.getEntityRenderDispatcher().getLight(player, tickDelta);

        matrices.push();
        matrices.translate(x, y, z);
        // rotY(-yaw) sends +Z to (-sin yaw, 0, cos yaw), Minecraft's facing vector for that yaw.
        // rotY(-yaw) 把 +Z 转到 (-sin yaw, 0, cos yaw)，即该偏航角的朝向向量。
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        matrices.scale(scale, scale, scale);
        drawBoard(matrices, consumers, light, player);
        matrices.pop();
    }

    /** Expects the feet frame described on the class. / 需要类注释所述的脚下坐标系。 */
    private static void drawBoard(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntity player) {
        if (boardStack == null) {
            boardStack = new ItemStack(SparkStrengthItems.skateboard());
        }
        matrices.push();
        // ItemRenderer applies the identity NONE transform, then recentres by (-0.5, -0.5, -0.5): undo the Y part so the
        // wheels sit at y = 0; X/Z stay centred because the model is centred on (8, 8).
        // ItemRenderer 先应用恒等的 NONE 变换，再平移 (-0.5, -0.5, -0.5)：抵消 Y 分量使轮子落在 y = 0；模型以 (8, 8)
        // 为中心，X/Z 保持居中。
        matrices.translate(0.0F, 0.5F, 0.0F);
        MinecraftClient.getInstance().getItemRenderer().renderItem(boardStack, ModelTransformationMode.NONE, light,
                OverlayTexture.DEFAULT_UV, matrices, consumers, player.getWorld(), player.getId());
        matrices.pop();
    }

    /**
     * Board under other players (and the own player in third person or inventory previews).
     * 其他玩家脚下的滑板（以及第三人称或背包预览中的本人）。
     */
    static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        private final PlayerEntityRenderer renderer;

        Feature(PlayerEntityRenderer renderer) {
            super(renderer);
            this.renderer = renderer;
        }

        @Override
        public void render(
                MatrixStack matrices,
                VertexConsumerProvider vertexConsumers,
                int light,
                AbstractClientPlayerEntity player,
                float limbAngle,
                float limbDistance,
                float tickDelta,
                float animationProgress,
                float headYaw,
                float headPitch
        ) {
            float scale = player.getScale();
            if (player.isInvisible() || scale <= 0.0F || !standsOnBoard(player)) {
                return;
            }
            // Feature space = T(feet + positionOffset) * S(scale) * rotY(180 - bodyYaw) * S(-1, -1, 1) * S(0.9375)
            // * T(0, -1.501, 0). Peel it back to the feet frame:
            // 特征空间 = T(脚 + 位置偏移) * S(缩放) * rotY(180 - 身体偏航) * S(-1, -1, 1) * S(0.9375) * T(0, -1.501, 0)，
            // 逐步还原到脚下坐标系：
            matrices.push();
            matrices.translate(0.0F, MODEL_FOOT_OFFSET, 0.0F);
            matrices.scale(1.0F / PLAYER_MODEL_SCALE, 1.0F / PLAYER_MODEL_SCALE, 1.0F / PLAYER_MODEL_SCALE);
            // S(-1, -1, 1) * rotY(180) = rotX(180): one proper rotation undoes the flip and turns the +Z nose from the
            // model's -Z forward to the facing direction (no mirroring); the frame becomes rotY(-bodyYaw).
            // S(-1, -1, 1) * rotY(180) = rotX(180)：一次正交旋转同时抵消翻转并把 +Z 板头从模型的 -Z 前方转向朝向（不镜像）；
            // 坐标系变为 rotY(-身体偏航)。
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
            // The origin still carries getPositionOffset (our deck lift, vanilla's sneak drop, other mods) in world
            // units; drop it so the wheels rest on the entity's feet, i.e. the ground.
            // 原点仍包含 getPositionOffset（我们的板面抬高、原版潜行下沉、其他模组）的世界单位偏移；减去它让轮子落在实体脚下，即地面。
            matrices.translate(0.0D, -renderer.getPositionOffset(player, tickDelta).y / scale, 0.0D);
            drawBoard(matrices, vertexConsumers, light, player);
            matrices.pop();
        }
    }
}
