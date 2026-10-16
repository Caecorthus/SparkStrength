package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.block.CircuitBreakerBlock;
import annina.sparkstrength.compat.SparkTraitsSabotageCompat;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.mixin.sabotage.BlackoutDetailsAccessor;
import annina.sparkstrength.mixin.sabotage.WorldBlackoutComponentAccessor;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.record.BlackoutRecorder;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The lights sabotage, built on Wathe's own blackout so every existing reader of {@code isBlackoutActive()}, every
 * {@code BlackoutEffect.BEFORE} listener and SparkWitch's Saboteur lamp leases keep working.
 * 灯光破坏，建立在 Wathe 自己的停电之上，因此所有读取 isBlackoutActive() 的代码、BlackoutEffect.BEFORE 监听器以及
 * SparkWitch 破坏者的灯具租约都照常工作。
 *
 * <p>Repair mode stretches every lamp and the world timer to {@link SabotageRules#OUTAGE_CAP_TICKS}; a breaker repair
 * gives each lamp a short staggered time (nearest first) and zeroes the world timer, so Wathe ends the effects on its
 * next tick and lights the lamps itself. Every way an outage can end funnels into {@link #finish} exactly once.
 * 维修模式把每盏灯与世界计时都拉长到 OUTAGE_CAP_TICKS；修好电闸时给每盏灯一个错开的短时间（近的先亮）并清零世界计时，
 * Wathe 下一 tick 就会自己结束效果并点亮灯。熄灯的每一种结束方式都只经 finish 收尾一次。</p>
 */
public final class LightsSabotageService {
    public enum TriggerResult { OK, BUSY, NO_LAMPS }

    public enum EndReason { REPAIRED, POWER_RESTORED, EXPIRED, ROUND_END }

    private LightsSabotageService() {
    }

    /** Called by Wathe's effect pass: no Blindness during a sabotage outage. / 由 Wathe 的效果循环调用：破坏熄灯期间不施加失明。 */
    public static boolean suppressesBlindness(ServerWorld world) {
        SabotageWorldComponent.Outage outage = SabotageWorldComponent.KEY.get(world).outage();
        return outage != null && outage.type() == SabotageType.LIGHTS;
    }

    /** Repair mode shows its own hint instead of Wathe's "back in N s". / 维修模式用自己的提示代替 Wathe 的「N 秒后恢复」。 */
    public static boolean replacesCountdown(ServerWorld world) {
        SabotageWorldComponent.Outage outage = SabotageWorldComponent.KEY.get(world).outage();
        return outage != null && outage.type() == SabotageType.LIGHTS && outage.mode() == OutageMode.REPAIR;
    }

    public static TriggerResult trigger(ServerWorld world, SabotageWorldComponent sabotage,
                                        ServerPlayerEntity initiator) {
        WorldBlackoutComponent blackout = WorldBlackoutComponent.KEY.get(world);
        if (blackout.isBlackoutActive() || sabotage.outage() != null) {
            return TriggerResult.BUSY;
        }
        OutageMode mode = SabotageRules.outageMode(sabotage.hasBreakers());
        // Started before Wathe's trigger: its first effect pass already runs inside triggerBlackout.
        // 在 Wathe 触发之前登记：它的第一轮效果就在 triggerBlackout 内部执行。
        sabotage.startOutage(SabotageType.LIGHTS, mode, world.getTime(), initiator.getUuid(),
                initiator.getGameProfile().getName(), SparkTraitsSabotageCompat.isMasterSaboteur(initiator));
        boolean started;
        try {
            // Wathe also returns true on a map without lamps, where nothing goes dark. / 没有灯的地图上 Wathe 同样返回 true，但不会熄灯。
            started = blackout.triggerBlackout() && blackout.isBlackoutActive();
        } catch (RuntimeException error) {
            started = false;
        }
        if (!started) {
            sabotage.endOutage();
            return TriggerResult.NO_LAMPS;
        }
        if (mode == OutageMode.REPAIR) {
            WorldBlackoutComponentAccessor accessor = (WorldBlackoutComponentAccessor) blackout;
            for (WorldBlackoutComponent.BlackoutDetails detail : accessor.sparkstrength$getBlackouts()) {
                ((BlackoutDetailsAccessor) detail).sparkstrength$setTime(SabotageRules.OUTAGE_CAP_TICKS);
            }
            accessor.sparkstrength$setTicks(SabotageRules.OUTAGE_CAP_TICKS);
            setBreakersTripped(world, sabotage, true);
        }
        BlackoutRecorder.sabotaged(initiator);
        return TriggerResult.OK;
    }

    /** A finished repair panel at {@code breakerPos}. / 在 breakerPos 完成了维修面板。 */
    public static boolean repair(ServerWorld world, SabotageWorldComponent sabotage, ServerPlayerEntity repairer,
                                 BlockPos breakerPos) {
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage == null || outage.type() != SabotageType.LIGHTS || outage.mode() != OutageMode.REPAIR) {
            return false;
        }
        WorldBlackoutComponent blackout = WorldBlackoutComponent.KEY.get(world);
        WorldBlackoutComponentAccessor accessor = (WorldBlackoutComponentAccessor) blackout;
        List<WorldBlackoutComponent.BlackoutDetails> details = accessor.sparkstrength$getBlackouts();
        Vec3d origin = Vec3d.ofCenter(breakerPos);
        double farthest = 0.0;
        for (WorldBlackoutComponent.BlackoutDetails detail : details) {
            farthest = Math.max(farthest, Math.sqrt(
                    ((BlackoutDetailsAccessor) detail).sparkstrength$getPos().getSquaredDistance(origin)));
        }
        for (WorldBlackoutComponent.BlackoutDetails detail : details) {
            BlackoutDetailsAccessor lamp = (BlackoutDetailsAccessor) detail;
            double distance = Math.sqrt(lamp.sparkstrength$getPos().getSquaredDistance(origin));
            lamp.sparkstrength$setTime(SabotageRules.restoreDelay(distance, farthest));
        }
        // Zero with the "was active" flag still set: Wathe's next tick ends Night Vision / Blindness at once.
        // 清零但保留「曾经激活」标记：Wathe 下一 tick 立即结束夜视与失明。
        accessor.sparkstrength$setTicks(0);
        world.playSound(null, breakerPos, SparkStrengthSounds.CIRCUIT_BREAKER_RESTORE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        finish(world, sabotage, EndReason.REPAIRED, repairer);
        return true;
    }

    /** End-of-outage watcher: anything that turned Wathe's blackout off ends ours. / 熄灯结束的观察者：任何关掉 Wathe 停电的事都会结束我们的熄灯。 */
    public static void tick(ServerWorld world, SabotageWorldComponent sabotage) {
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage == null || outage.type() != SabotageType.LIGHTS) {
            return;
        }
        if (!WorldBlackoutComponent.KEY.get(world).isBlackoutActive()) {
            finish(world, sabotage, EndReason.EXPIRED, null);
            return;
        }
        if (outage.mode() == OutageMode.REPAIR && world.getTime() % 20 == 0) {
            long seconds = (world.getTime() - outage.startTick()) / 20;
            Text hint = Text.translatable("message.sparkstrength.sabotage.lights_out_hint",
                    String.format("%d:%02d", seconds / 60, seconds % 60)).formatted(Formatting.RED);
            for (ServerPlayerEntity player : world.getPlayers()) {
                player.sendMessage(hint, true);
            }
        }
    }

    public static void finish(ServerWorld world, SabotageWorldComponent sabotage, EndReason reason,
                              @Nullable ServerPlayerEntity actor) {
        SabotageWorldComponent.Outage outage = sabotage.endOutage();
        if (outage == null) {
            return;
        }
        setBreakersTripped(world, sabotage, false);
        BreakerRepairService.closeAll(world, CloseBreakerRepairS2CPacket.REASON_POWER_BACK);
        long now = world.getTime();
        switch (reason) {
            case REPAIRED -> {
                sabotage.setTeamCooldownEndTick(now + SabotageRules.TEAM_COOLDOWN_TICKS);
                sabotage.log(now, SabotageWorldComponent.LogKind.REPAIRED, "", outage.type());
                Text restored = Text.translatable("message.sparkstrength.sabotage.power_back").formatted(Formatting.GREEN);
                for (ServerPlayerEntity player : world.getPlayers()) {
                    player.sendMessage(restored, true);
                }
                if (actor != null) {
                    BlackoutRecorder.breakerRepaired(actor);
                }
            }
            case POWER_RESTORED -> {
                sabotage.setTeamCooldownEndTick(now + SabotageRules.POWER_RESTORATION_COOLDOWN_TICKS);
                sabotage.log(now, SabotageWorldComponent.LogKind.POWER_RESTORED, "", outage.type());
            }
            case EXPIRED -> {
                sabotage.setTeamCooldownEndTick(now + SabotageRules.TEAM_COOLDOWN_TICKS);
                sabotage.log(now, SabotageWorldComponent.LogKind.EXPIRED, "", outage.type());
            }
            case ROUND_END -> {
            }
        }
        SabotageService.markStatusDirty(world);
    }

    private static void setBreakersTripped(ServerWorld world, SabotageWorldComponent sabotage, boolean tripped) {
        for (BlockPos pos : sabotage.breakers()) {
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(SparkStrengthBlocks.circuitBreaker()) || state.get(CircuitBreakerBlock.TRIPPED) == tripped) {
                continue;
            }
            world.setBlockState(pos, state.with(CircuitBreakerBlock.TRIPPED, tripped), Block.NOTIFY_ALL);
            if (tripped) {
                world.playSound(null, pos, SparkStrengthSounds.CIRCUIT_BREAKER_TRIP, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}
