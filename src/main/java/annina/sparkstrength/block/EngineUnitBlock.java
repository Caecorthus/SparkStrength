package annina.sparkstrength.block;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.sabotage.EngineCondition;
import annina.sparkstrength.sabotage.EngineSabotageService;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Map;

/**
 * Engine unit: the repair point of the engine sabotage. A floor-standing cabinet; {@code condition} is blockstate so
 * every client sees intact / damaged / wrecked without packets. Right-click is handled on the server.
 * 动力装置：动力破坏的维修点。落地式机柜；condition 在方块状态里，所有客户端无需数据包就能看到完好 / 受损 / 损毁。
 * 右键交给服务端处理。
 */
public final class EngineUnitBlock extends HorizontalFacingBlock {
    public static final MapCodec<EngineUnitBlock> CODEC = createCodec(EngineUnitBlock::new);
    public static final EnumProperty<EngineCondition> CONDITION = EnumProperty.of("condition", EngineCondition.class);
    private static final Map<Direction, VoxelShape> SHAPES = WallMountedShapes.build(
            new double[]{1, 0, 1, 15, 15, 15}
    );

    public EngineUnitBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(CONDITION, EngineCondition.INTACT));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONDITION);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // The front (gauges) faces whoever places it. / 正面（表盘）朝向放置者。
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                            PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(SparkStrengthItems.circuitConfigurator())) {
            return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            EngineSabotageService.onUnitUse(serverPlayer, pos, state);
        }
        // Accepted on both sides so a held tablet never opens instead. / 两端都视为已处理，手里的平板不会因此被打开。
        return ActionResult.success(world.isClient());
    }
}
