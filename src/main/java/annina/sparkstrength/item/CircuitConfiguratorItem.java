package annina.sparkstrength.item;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.sabotage.CircuitConfiguratorService;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

/**
 * Admin tool: right-click a junction box to choose which sabotage it unlocks (sneak to go backwards); right-click a
 * circuit breaker or an engine unit for a map summary. Every decision, including the permission check, is made on the
 * server.
 * 管理员工具：右键接线箱选择它解锁哪种破坏（潜行时反向切换）；右键电闸或动力装置查看本图概况。包括权限在内的所有判定
 * 都在服务端。
 */
public final class CircuitConfiguratorItem extends Item {
    public CircuitConfiguratorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockState state = world.getBlockState(context.getBlockPos());
        if (!SparkStrengthBlocks.isSabotageFixture(state)) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        PlayerEntity player = context.getPlayer();
        if (player instanceof ServerPlayerEntity serverPlayer) {
            CircuitConfiguratorService.use(serverPlayer, context.getBlockPos(), state, serverPlayer.isSneaking());
        }
        return ActionResult.CONSUME;
    }
}
