package annina.sparkstrength.record;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.sabotage.EnginePhase;
import annina.sparkstrength.sabotage.EngineSabotageService;
import annina.sparkstrength.sabotage.SabotageType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the engine sabotage record event (S15 engine_sabotage) at each of its edges: the tablet trigger (with the
 * initiator), the wreck, emergency power, and the end (repaired, with the repairer, or aborted). EngineSabotageService
 * already runs each edge exactly once (the trigger, each phase transition, finish), so unlike BlackoutRecorder no
 * per-world state is kept; only tooling that rewinds the phase can repeat an edge. The outage id comes from the world's
 * sabotage state, so every call sits where the engine sabotage is still on. Server thread only. A recording failure is
 * logged and swallowed.
 * 在动力破坏的每个边沿写入记录事件（S15 engine_sabotage）：平板触发（带发起者）、损毁、应急动力，以及结束（修好，带修理者；
 * 或中止）。EngineSabotageService 本身就保证每个边沿只执行一次（触发、每次阶段转换、finish），所以与 BlackoutRecorder 不同，
 * 这里不保存按世界的状态；只有把阶段往回拨的工具会让某个边沿重复出现。破坏实例 id 取自该世界的破坏状态，因此每处调用都位于
 * 动力破坏仍在进行的时刻。仅限服务端线程。记录失败只写日志并吞掉异常。
 */
public final class EngineRecorder {
    private EngineRecorder() {
    }

    /** {@code initiator} just triggered the engine sabotage from the tablet. / 该玩家刚在平板上触发了动力破坏。 */
    public static void triggered(ServerWorld world, ServerPlayerEntity initiator) {
        try {
            record(world, AchievementRecords.ENGINE_STATE_TRIGGERED, initiator, EnginePhase.CRITICAL);
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the engine sabotage trigger", error);
        }
    }

    /**
     * The clock just entered {@code phase} and its side effects are running. CRITICAL is entered by the trigger itself
     * and records nothing here.
     * 时钟刚进入 phase，其副作用正在执行。CRITICAL 由触发本身进入，这里不记录。
     */
    public static void entered(ServerWorld world, EnginePhase phase) {
        try {
            String state = switch (phase) {
                case CRITICAL -> null;
                case WRECKED -> AchievementRecords.ENGINE_STATE_WRECKED;
                case EMERGENCY -> AchievementRecords.ENGINE_STATE_EMERGENCY;
            };
            if (state != null) {
                record(world, state, null, phase);
            }
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the engine sabotage phase {}", phase, error);
        }
    }

    /**
     * The engine sabotage is about to end and is still on; {@code repairer} is credited only for a repair.
     * 动力破坏即将结束、此刻仍在进行；只有修好时才记修理者。
     */
    public static void ended(ServerWorld world, EngineSabotageService.EndReason reason,
                             @Nullable ServerPlayerEntity repairer) {
        try {
            boolean repaired = reason == EngineSabotageService.EndReason.REPAIRED;
            record(world, repaired ? AchievementRecords.ENGINE_STATE_REPAIRED : AchievementRecords.ENGINE_STATE_ABORTED,
                    repaired ? repairer : null, SabotageWorldComponent.KEY.get(world).enginePhase(world.getTime()));
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the engine sabotage end", error);
        }
    }

    private static void record(ServerWorld world, String state, @Nullable ServerPlayerEntity actor,
                               @Nullable EnginePhase phase) {
        SabotageWorldComponent.Outage outage = SabotageWorldComponent.KEY.get(world).outage();
        if (outage == null || outage.type() != SabotageType.ENGINE) {
            return;
        }
        AchievementRecords.engineSabotage(world, state, actor, outage.id(), phaseId(phase));
    }

    /** The binding S15 phase value; empty when unknown. / S15 阶段的固定取值；未知时为空串。 */
    static String phaseId(@Nullable EnginePhase phase) {
        if (phase == null) {
            return "";
        }
        return switch (phase) {
            case CRITICAL -> AchievementRecords.ENGINE_PHASE_CRITICAL;
            case WRECKED -> AchievementRecords.ENGINE_PHASE_WRECKED;
            case EMERGENCY -> AchievementRecords.ENGINE_PHASE_EMERGENCY;
        };
    }
}
