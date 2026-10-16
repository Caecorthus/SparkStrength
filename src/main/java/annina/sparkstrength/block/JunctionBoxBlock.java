package annina.sparkstrength.block;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.sabotage.JunctionBoxService;
import annina.sparkstrength.sabotage.JunctionBoxState;
import annina.sparkstrength.sabotage.SabotageAccess;
import annina.sparkstrength.sabotage.SabotageType;
import com.mojang.serialization.MapCodec;
import dev.doctor4t.wathe.index.WatheItems;
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
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.agmas.noellesroles.ModItems;

import java.util.Map;

/**
 * Junction box: killers pry it open with a lockpick, then hack it with the tablet to unlock its sabotage for the team.
 * Its configured sabotage and its round look are blockstate, so the reset template carries the configuration and
 * clients see the look without packets.
 * 接线箱：杀手先用开锁器撬开，再用平板入侵，为全队解锁其对应的破坏。配置的破坏类型与本局外观都在方块状态里，
 * 因此重置模板会带上配置，客户端也无需数据包就能看到外观。
 *
 * <p>The consume/pass decision is made from facts both sides share (the held item, the box look and the actor's own
 * owner-synced identity), so a tablet click that is not a hack falls through to opening the tablet on both sides.
 * 消耗/放行的判断只用两端都知道的事实（手持物品、箱子外观、本人同步的身份），所以不是入侵的平板点击在两端都会落到打开平板。</p>
 */
public final class JunctionBoxBlock extends HorizontalFacingBlock {
    public static final MapCodec<JunctionBoxBlock> CODEC = createCodec(JunctionBoxBlock::new);
    public static final EnumProperty<JunctionBoxState> STATE = EnumProperty.of("state", JunctionBoxState.class);
    public static final EnumProperty<SabotageType> SABOTAGE = EnumProperty.of("sabotage", SabotageType.class);
    private static final Map<Direction, VoxelShape> SHAPES = WallMountedShapes.build(
            new double[]{3, 3, 12, 13, 13, 16}
    );

    public JunctionBoxBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(STATE, JunctionBoxState.CLOSED)
                .with(SABOTAGE, SabotageType.LIGHTS));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE, SABOTAGE);
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
        if (hand != Hand.MAIN_HAND) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isOf(SparkStrengthItems.circuitConfigurator())) {
            // Straight to the configurator's useOnBlock. / 直接交给配置器的 useOnBlock。
            return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        Interaction interaction = interactionFor(stack, state, player);
        if (interaction == Interaction.NONE) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            switch (interaction) {
                case PRY -> JunctionBoxService.onPry(serverPlayer, pos, state);
                case HACK -> JunctionBoxService.onHack(serverPlayer, pos, state);
                case REPAIR -> JunctionBoxService.onRepair(serverPlayer, pos, state);
                default -> {
                }
            }
        }
        // CONSUME on both sides: no arm swing per heartbeat; the server swings while prying. / 两端都 CONSUME：心跳不挥手；撬的时候由服务端挥手。
        return ItemActionResult.CONSUME;
    }

    private enum Interaction { NONE, PRY, HACK, REPAIR }

    private static Interaction interactionFor(ItemStack stack, BlockState state, PlayerEntity player) {
        if (stack.isOf(WatheItems.LOCKPICK)) {
            // Always consumed so a lockpick never falls through; the server explains a refusal. / 总是消耗，开锁器不会落到别处；拒绝原因由服务端说明。
            return Interaction.PRY;
        }
        if (stack.isOf(ModItems.REPAIR_TOOL)) {
            return Interaction.REPAIR;
        }
        if (stack.isOf(SparkStrengthItems.tablet())
                && state.get(STATE) == JunctionBoxState.BROKEN
                && SabotageAccess.hasSabotageIdentity(player)) {
            return Interaction.HACK;
        }
        return Interaction.NONE;
    }
}
