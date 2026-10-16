package annina.sparkstrength.block;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.sabotage.BreakerRepairService;
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
import net.minecraft.state.property.BooleanProperty;
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
 * Circuit breaker: plain decoration until a lights sabotage trips it; then any living player can open its repair panel.
 * {@code tripped} is blockstate so every client sees the dropped lever without packets.
 * 电闸：平时是普通装饰，灯光破坏会让它跳闸，此时任何存活玩家都能打开维修面板。tripped 在方块状态里，
 * 所有客户端无需数据包就能看到落下的拉杆。
 */
public final class CircuitBreakerBlock extends HorizontalFacingBlock {
    public static final MapCodec<CircuitBreakerBlock> CODEC = createCodec(CircuitBreakerBlock::new);
    public static final BooleanProperty TRIPPED = BooleanProperty.of("tripped");
    private static final Map<Direction, VoxelShape> SHAPES = WallMountedShapes.build(
            new double[]{2, 1, 13, 14, 15, 16},
            new double[]{10, 2, 10, 14, 14, 13}
    );

    public CircuitBreakerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(TRIPPED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIPPED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();
        Direction facing = side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
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
            BreakerRepairService.onBreakerUse(serverPlayer, pos, state);
        }
        // Accepted on both sides so a held tablet never opens instead. / 两端都视为已处理，手里的平板不会因此被打开。
        return ActionResult.success(world.isClient());
    }
}
