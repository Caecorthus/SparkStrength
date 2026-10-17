package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The engine sabotage: units damaged, then wrecked (the train halts and the round timer stops running), then emergency
 * power. Server thread only.
 * 动力破坏：装置受损，随后损毁（列车停驶、回合倒计时暂停），再到应急动力。仅限服务端线程。
 */
public final class EngineSabotageService {
    private EngineSabotageService() {
    }

    /**
     * Move the engine sabotage to {@code phase} ({@code null} = over), with the units' looks to match. The status packet
     * goes out before the round timer is synced on the same connection, so a client applies the new phase first and is
     * then corrected to the authoritative time.
     * 把动力破坏切到 phase（null 表示结束），装置外观随之改变。状态包在同一连接上先于回合倒计时同步发出，客户端先应用新阶段，
     * 再用权威时间校正。
     */
    public static void setPhase(ServerWorld world, @Nullable EnginePhase phase) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        EnginePhase previous = sabotage.enginePhase();
        if (previous == phase) {
            return;
        }
        sabotage.setEnginePhase(phase, world.getTime());
        setUnitLooks(world, sabotage.engineUnits(), EngineCondition.of(phase));
        // Re-syncs the round timer itself when the frozen state changes. / 冻结状态改变时，它会自己重新同步回合倒计时。
        SabotageService.syncStatusNow(world);
    }

    /** Ends the engine sabotage without a repair: no cooldown, nobody credited. / 不经维修结束动力破坏：不开始冷却，不记功劳。 */
    public static void abort(ServerWorld world) {
        setPhase(world, null);
    }

    public static void setUnitLooks(ServerWorld world, List<BlockPos> units, EngineCondition condition) {
        for (BlockPos pos : units) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(SparkStrengthBlocks.engineUnit()) && state.get(EngineUnitBlock.CONDITION) != condition) {
                world.setBlockState(pos, state.with(EngineUnitBlock.CONDITION, condition), Block.NOTIFY_ALL);
            }
        }
    }

    /**
     * Right-click on an engine unit. Until the repair panel lands this only reports the unit's state.
     * 右键动力装置。维修面板接入之前，这里只报告装置状态。
     */
    public static void onUnitUse(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        EngineCondition condition = state.get(EngineUnitBlock.CONDITION);
        player.sendMessage(Text.translatable("message.sparkstrength.engine.unit_" + condition.asString()), true);
    }
}
