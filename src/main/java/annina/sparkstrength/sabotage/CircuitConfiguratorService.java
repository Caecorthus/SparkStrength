package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.JunctionBoxBlock;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server side of the circuit configurator. A junction box's sabotage is blockstate, so it travels with Wathe's reset
 * template; changing one copy also changes the other (template ↔ pasted train, found through {@code resetPasteOffset}).
 * 线路配置器的服务端。接线箱的破坏类型在方块状态里，会随 Wathe 的重置模板一起复制；改动其中一份时，另一份
 * （模板 ↔ 粘贴后的车厢，通过 resetPasteOffset 找到）也会一起改。
 */
public final class CircuitConfiguratorService {
    private static final int PERMISSION_LEVEL = 2;

    private CircuitConfiguratorService() {
    }

    public static void use(ServerPlayerEntity player, BlockPos pos, BlockState state, boolean backwards) {
        if (!player.hasPermissionLevel(PERMISSION_LEVEL)) {
            player.sendMessage(Text.translatable("message.sparkstrength.configurator.no_permission")
                    .formatted(Formatting.RED), true);
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (state.isOf(SparkStrengthBlocks.junctionBox())) {
            configureBox(player, world, pos, state, backwards);
        } else if (state.isOf(SparkStrengthBlocks.circuitBreaker())) {
            summarize(player, world, pos);
        }
    }

    private static void configureBox(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state,
                                     boolean backwards) {
        SabotageType type = state.get(JunctionBoxBlock.SABOTAGE).cycle(backwards);
        world.setBlockState(pos, state.with(JunctionBoxBlock.SABOTAGE, type), Block.NOTIFY_ALL);
        BlockPos twin = twinOf(world, pos);
        boolean mirrored = false;
        if (twin != null) {
            BlockState twinState = world.getBlockState(twin);
            if (twinState.isOf(SparkStrengthBlocks.junctionBox())) {
                world.setBlockState(twin, twinState.with(JunctionBoxBlock.SABOTAGE, type), Block.NOTIFY_ALL);
                mirrored = true;
            }
        }
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.6F, 1.4F);
        player.sendMessage(Text.translatable(mirrored
                        ? "message.sparkstrength.configurator.box_set_mirrored"
                        : "message.sparkstrength.configurator.box_set",
                pos.getX(), pos.getY(), pos.getZ(), Text.translatable(type.translationKey())), true);
    }

    private static void summarize(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        AtomicInteger boxes = new AtomicInteger();
        AtomicInteger breakers = new AtomicInteger();
        SabotageBlockScan.scan(world, MapVariablesWorldComponent.KEY.get(world).getPlayArea(), (found, state) -> {
            if (state.isOf(SparkStrengthBlocks.junctionBox())) {
                boxes.incrementAndGet();
            } else {
                breakers.incrementAndGet();
            }
        });
        player.sendMessage(Text.translatable("message.sparkstrength.configurator.summary",
                pos.getX(), pos.getY(), pos.getZ(), boxes.get(), breakers.get()), false);
    }

    /**
     * The same block on the other side of Wathe's train copy: template → pasted train, or pasted train → template.
     * 同一个方块在 Wathe 车厢复制另一侧的位置：模板 → 粘贴后的车厢，或粘贴后的车厢 → 模板。
     */
    static @Nullable BlockPos twinOf(ServerWorld world, BlockPos pos) {
        MapVariablesWorldComponent areas = MapVariablesWorldComponent.KEY.get(world);
        Box template = areas.getResetTemplateArea();
        Vec3i offset = areas.getResetPasteOffset();
        if (template == null || offset == null || offset.equals(Vec3i.ZERO)) {
            return null;
        }
        BlockBox templateBlocks = BlockBox.create(BlockPos.ofFloored(template.getMinPos()),
                BlockPos.ofFloored(template.getMaxPos()));
        if (templateBlocks.contains(pos)) {
            return pos.add(offset);
        }
        BlockPos back = pos.subtract(offset);
        return templateBlocks.contains(back) ? back : null;
    }
}
