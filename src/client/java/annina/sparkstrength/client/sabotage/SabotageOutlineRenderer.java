package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.CircuitBreakerBlock;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.sabotage.JunctionBoxState;
import annina.sparkstrength.sabotage.OutageMode;
import annina.sparkstrength.sabotage.SabotageType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.client.WatheClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/**
 * See-through block outlines: tripped breakers pulse red for everyone during a repair-mode outage; junction boxes not
 * yet hacked show for saboteurs while the instinct key is held; an admin holding the configurator sees every fixture
 * with its sabotage label. Drawn at {@code LAST} (after Fabulous' composite) with the fog-free position_color shader,
 * so neither walls nor darkness hide them.
 * 透视方块描边：维修模式熄灯期间，跳闸的电闸对所有人红色呼吸闪烁；按住本能键时，可以破坏的人能看到尚未入侵的接线箱；
 * 手持配置器的管理员能看到全部设施及其破坏类型标签。在 LAST 阶段（Fabulous 合成之后）用无雾的 position_color
 * 着色器绘制，墙体与黑暗都挡不住。
 */
final class SabotageOutlineRenderer {
    private static final int BREAKER_RED = 0xFF3A30;
    private static final int BOX_CLOSED = 0xF5A524;
    private static final int BOX_OPEN = 0xFF8A3D;
    private static final int ADMIN_BREAKER = 0x4C9BFF;
    private static final double LABEL_RANGE_SQ = 24.0 * 24.0;

    private SabotageOutlineRenderer() {
    }

    static void register() {
        WorldRenderEvents.LAST.register(SabotageOutlineRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null || SabotageClientState.fixtures().isEmpty()) {
            return;
        }
        boolean admin = SabotageClient.holdsConfigurator(player);
        boolean breakers = SabotageClientState.lightsOut() && SabotageClientState.outageMode() == OutageMode.REPAIR;
        boolean boxes = SabotageClientState.canSabotage() && WatheClient.isInstinctEnabled();
        if (!admin && !breakers && !boxes) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        float pulse = 0.55F + 0.45F * (float) Math.sin(Util.getMeasuringTimeMs() / 1000.0 * Math.PI * 2.0);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(3.0F);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES,
                VertexFormats.POSITION_COLOR);
        boolean drew = false;
        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (SabotageClientState.Fixture fixture : SabotageClientState.fixtures()) {
            BlockState state = world.getBlockState(fixture.pos());
            int color;
            float alpha;
            if (state.isOf(SparkStrengthBlocks.circuitBreaker())) {
                if (admin) {
                    color = ADMIN_BREAKER;
                    alpha = 0.9F;
                } else if (breakers && state.get(CircuitBreakerBlock.TRIPPED)) {
                    color = BREAKER_RED;
                    alpha = pulse;
                } else {
                    continue;
                }
            } else if (state.isOf(SparkStrengthBlocks.junctionBox())) {
                JunctionBoxState look = state.get(JunctionBoxBlock.STATE);
                if (admin) {
                    color = typeColor(state.get(JunctionBoxBlock.SABOTAGE));
                    alpha = 0.9F;
                } else if (boxes && look != JunctionBoxState.HACKED) {
                    color = look == JunctionBoxState.CLOSED ? BOX_CLOSED : BOX_OPEN;
                    alpha = 0.85F;
                } else {
                    continue;
                }
            } else {
                continue;
            }
            outline(buffer, matrix, world, fixture.pos(), state, camera, color, alpha);
            drew = true;
        }
        matrices.pop();
        if (drew) {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } else {
            buffer.end().close();
        }
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        if (admin) {
            renderLabels(client, world, player, matrices, camera);
        }
    }

    private static void outline(BufferBuilder buffer, Matrix4f matrix, ClientWorld world, BlockPos pos,
                                BlockState state, Vec3d camera, int rgb, float alpha) {
        VoxelShape shape = state.getOutlineShape(world, pos);
        Box bounds = shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox();
        float inflate = 0.02F;
        float x1 = (float) (pos.getX() + bounds.minX - camera.x) - inflate;
        float y1 = (float) (pos.getY() + bounds.minY - camera.y) - inflate;
        float z1 = (float) (pos.getZ() + bounds.minZ - camera.z) - inflate;
        float x2 = (float) (pos.getX() + bounds.maxX - camera.x) + inflate;
        float y2 = (float) (pos.getY() + bounds.maxY - camera.y) + inflate;
        float z2 = (float) (pos.getZ() + bounds.maxZ - camera.z) + inflate;
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        line(buffer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
        line(buffer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float z1,
                             float x2, float y2, float z2, int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }

    private static int typeColor(SabotageType type) {
        return switch (type) {
            case LIGHTS -> 0xFFD84A;
        };
    }

    /** Admin labels above junction boxes: the sabotage they unlock. / 管理员视图：接线箱上方显示它解锁的破坏类型。 */
    private static void renderLabels(MinecraftClient client, ClientWorld world, ClientPlayerEntity player,
                                     MatrixStack matrices, Vec3d camera) {
        TextRenderer textRenderer = client.textRenderer;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        for (SabotageClientState.Fixture fixture : SabotageClientState.fixtures()) {
            BlockState state = world.getBlockState(fixture.pos());
            if (!state.isOf(SparkStrengthBlocks.junctionBox())
                    || player.squaredDistanceTo(Vec3d.ofCenter(fixture.pos())) > LABEL_RANGE_SQ) {
                continue;
            }
            Text label = Text.translatable(state.get(JunctionBoxBlock.SABOTAGE).translationKey());
            matrices.push();
            matrices.translate(fixture.pos().getX() + 0.5 - camera.x, fixture.pos().getY() + 1.05 - camera.y,
                    fixture.pos().getZ() + 0.5 - camera.z);
            matrices.multiply(client.gameRenderer.getCamera().getRotation());
            matrices.scale(0.025F, -0.025F, 0.025F);
            float x = -textRenderer.getWidth(label) / 2.0F;
            textRenderer.draw(label, x, 0, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0x80000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }
        consumers.draw();
    }
}
