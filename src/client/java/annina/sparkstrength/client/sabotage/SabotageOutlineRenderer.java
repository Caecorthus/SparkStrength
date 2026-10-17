package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.CircuitBreakerBlock;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.sabotage.EngineHalt;
import annina.sparkstrength.sabotage.JunctionBoxState;
import annina.sparkstrength.sabotage.OutageMode;
import annina.sparkstrength.sabotage.JunctionBoxCircuit;
import annina.sparkstrength.sabotage.SabotageType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * See-through block outlines: tripped breakers pulse red for everyone during a repair-mode outage, engine units pulse
 * orange-red for everyone in the round during an engine sabotage (drawn from the status packet's coordinates, so units
 * beyond the render distance show too); junction boxes not yet hacked show for saboteurs while the instinct key is held
 * (amber for lights, teal for the engine); an admin holding the configurator sees every fixture with its sabotage
 * label. Drawn at {@code LAST} (after Fabulous' composite) with the fog-free position_color shader,
 * so neither walls nor darkness hide them.
 * 透视方块描边：维修模式熄灯期间，跳闸的电闸对所有人红色呼吸闪烁，动力破坏期间动力装置对本局所有人橙红色闪烁（按状态包下发的
 * 坐标绘制，渲染距离外的装置也有描边）；按住本能键时，可以破坏的人
 * 能看到尚未入侵的接线箱（灯光为琥珀色，动力为青色）；
 * 手持配置器的管理员能看到全部设施及其破坏类型标签。在 LAST 阶段（Fabulous 合成之后）用无雾的 position_color
 * 着色器绘制，墙体与黑暗都挡不住。
 */
final class SabotageOutlineRenderer {
    private static final int BREAKER_RED = 0xFF3A30;
    private static final int BOX_CLOSED = 0xF5A524;
    private static final int BOX_OPEN = 0xFF8A3D;
    /** Engine boxes are teal so saboteurs can tell them from lights boxes. / 动力接线箱用青色，让破坏者能和灯光接线箱区分开。 */
    private static final int ENGINE_BOX_CLOSED = 0x3FD0C9;
    private static final int ENGINE_BOX_OPEN = 0x2FA8A2;
    private static final int ENGINE_UNIT = 0xFF6A1F;
    private static final int ADMIN_BREAKER = 0x4C9BFF;
    private static final double LABEL_RANGE_SQ = 24.0 * 24.0;
    private static final float LINE_WIDTH = 3.5F;

    private SabotageOutlineRenderer() {
    }

    static void register() {
        WorldRenderEvents.LAST.register(SabotageOutlineRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }
        boolean inRound = GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator();
        List<BlockPos> engineUnits = SabotageClientState.engineUnits();
        boolean engine = inRound && EngineHalt.clientPhase() != null;
        if (SabotageClientState.fixtures().isEmpty() && (!engine || engineUnits.isEmpty())) {
            return;
        }
        boolean admin = SabotageClient.holdsConfigurator(player);
        boolean breakers = SabotageClientState.lightsOut() && SabotageClientState.outageMode() == OutageMode.REPAIR;
        boolean boxes = SabotageClientState.canSabotage() && WatheClient.isInstinctEnabled();
        if (!admin && !breakers && !boxes && !engine) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        float pulse = 0.75F + 0.25F * (float) Math.sin(Util.getMeasuringTimeMs() / 1000.0 * Math.PI * 2.0);

        // rendertype_lines turns each line into a screen-space quad, so the width holds on core-profile GL (macOS) where
        // plain GL lines are always one pixel. Depth test off: the outline shows through walls.
        // rendertype_lines 会把每条线变成屏幕空间的四边形，因此在核心模式 GL（macOS）下宽度依然有效；普通 GL 线在那里永远只有一像素。
        // 关闭深度测试：描边可以穿墙显示。
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(LINE_WIDTH);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        matrices.push();
        Set<BlockPos> outlinedUnits = new HashSet<>();
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
                SabotageType type = JunctionBoxBlock.typeOf(state);
                if (admin) {
                    color = circuitColor(state.get(JunctionBoxBlock.SABOTAGE));
                    alpha = 0.9F;
                } else if (boxes && look != JunctionBoxState.HACKED && type != null) {
                    boolean closed = look == JunctionBoxState.CLOSED;
                    color = type == SabotageType.ENGINE
                            ? (closed ? ENGINE_BOX_CLOSED : ENGINE_BOX_OPEN)
                            : (closed ? BOX_CLOSED : BOX_OPEN);
                    alpha = 0.85F;
                } else {
                    continue;
                }
            } else if (state.isOf(SparkStrengthBlocks.engineUnit())) {
                if (admin) {
                    color = ADMIN_BREAKER;
                    alpha = 0.9F;
                } else if (engine) {
                    color = ENGINE_UNIT;
                    alpha = pulse;
                } else {
                    continue;
                }
            } else {
                continue;
            }
            if (state.isOf(SparkStrengthBlocks.engineUnit())) {
                outlinedUnits.add(fixture.pos());
            }
            outline(buffer, matrices, world, fixture.pos(), state, camera, color, alpha);
        }
        if (engine) {
            // Every unit the server registered, loaded chunk or not, as a fixed box; the scan above already drew some.
            // 服务端登记的每一台装置，不管区块是否已加载，都画成固定的包围盒；上面的扫描已经画过的跳过。
            for (BlockPos pos : engineUnits) {
                if (!outlinedUnits.contains(pos)) {
                    box(buffer, matrices, EngineUnitBlock.BOUNDS, pos, camera, ENGINE_UNIT, pulse);
                }
            }
        }
        matrices.pop();
        // endNullable: an empty builder (nothing qualified this frame) must not be ended with end(), which throws.
        // endNullable：空的构建器（本帧没有需要描边的方块）不能用 end() 结束，否则会抛异常。
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        if (admin) {
            renderLabels(client, world, player, matrices, camera);
        }
    }

    private static void outline(BufferBuilder buffer, MatrixStack matrices, ClientWorld world, BlockPos pos,
                                BlockState state, Vec3d camera, int rgb, float alpha) {
        VoxelShape shape = state.getOutlineShape(world, pos);
        box(buffer, matrices, shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox(), pos, camera, rgb,
                alpha);
    }

    /** {@code local} is in block space (0-1). / local 为方块内坐标（0-1）。 */
    private static void box(BufferBuilder buffer, MatrixStack matrices, Box local, BlockPos pos, Vec3d camera, int rgb,
                            float alpha) {
        Box bounds = local.offset(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z).expand(0.02);
        WorldRenderer.drawBox(matrices, buffer, bounds, ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
    }

    private static int circuitColor(JunctionBoxCircuit circuit) {
        return switch (circuit) {
            case LIGHTS -> 0xFFD84A;
            case ENGINE -> ENGINE_BOX_CLOSED;
            case NONE -> 0x8A8F98;
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
