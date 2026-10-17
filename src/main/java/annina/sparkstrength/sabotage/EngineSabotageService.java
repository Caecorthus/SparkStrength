package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.compat.SparkTraitsSabotageCompat;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The engine sabotage: units damaged (60 s to repair one), then wrecked (the train halts and the round timer stops
 * running), then emergency power after the halt cap. It holds the one outage slot from the trigger until it is
 * repaired or aborted. Server thread only.
 * 动力破坏：装置受损（60 秒内修好任意一台），随后损毁（列车停驶、回合倒计时暂停），停驶到上限后启用应急动力。从触发直到修好
 * 或中止，它都占着唯一的破坏名额。仅限服务端线程。
 *
 * <p>The phase is never stored: {@link SabotageWorldComponent#enginePhase(long)} derives it from the trigger tick, so
 * the round timer, panels and packets agree on it to the tick. {@link #advance} only runs each transition's side effects
 * once, in order; it is called at every entry point because packets can be handled before the end-of-tick update.
 * Repair and abort both end in {@link #finish}, which runs once per sabotage.
 * 阶段从不保存：SabotageWorldComponent#enginePhase(long) 由触发 tick 推导，所以回合倒计时、面板与数据包对阶段的看法精确到
 * tick 一致。advance 只负责按顺序把每次转换的副作用各执行一次；每个入口都会先调用它，因为数据包可能在 tick 末尾的更新之前被处理。
 * 修好与中止都走 finish，每次破坏只执行一次。</p>
 */
public final class EngineSabotageService {
    public enum TriggerResult { OK, BUSY, NO_ENGINE_UNITS }

    public enum EndReason {
        /** Someone finished a repair panel: cooldown starts, the train runs again. / 有人完成了维修面板：开始冷却，列车恢复行驶。 */
        REPAIRED,
        /** Round over, map copied again, last unit gone: no cooldown, nobody credited. / 对局结束、重新复制地图、最后一台装置没了：不冷却，不记功劳。 */
        ABORTED
    }

    /** The alarm is heard this far, from the nearest damaged unit only. / 警报的可闻距离，只从最近的受损装置发出。 */
    private static final double ALARM_RANGE = 16.0;
    private static final float ALARM_PITCH = 0.5F;
    /** Wathe's horn block plays at 100; this is a soft "engine back" toot. / Wathe 的汽笛方块用 100；这里只是轻轻一声「动力恢复」。 */
    private static final float HORN_VOLUME = 0.5F;

    private EngineSabotageService() {
    }

    /** The derived phase right now, null when no engine sabotage is on. / 当前推导出的阶段，没有动力破坏时为 null。 */
    public static @Nullable EnginePhase phase(ServerWorld world) {
        return SabotageWorldComponent.KEY.get(world).enginePhase(world.getTime());
    }

    /**
     * Tablet trigger, after the shared checks (unlocked, armed, cooldown, nothing else on) passed.
     * 平板触发，在通用检查（已解锁、已就绪、冷却、没有其他破坏）通过之后调用。
     */
    public static TriggerResult trigger(ServerWorld world, SabotageWorldComponent sabotage,
                                        ServerPlayerEntity initiator) {
        if (sabotage.outage() != null || WorldBlackoutComponent.KEY.get(world).isBlackoutActive()) {
            return TriggerResult.BUSY;
        }
        if (!sabotage.hasEngineUnits()) {
            return TriggerResult.NO_ENGINE_UNITS;
        }
        start(world, sabotage, world.getTime(), initiator.getUuid(), initiator.getGameProfile().getName(),
                SparkTraitsSabotageCompat.isMasterSaboteur(initiator));
        feedback(world, sabotage);
        return TriggerResult.OK;
    }

    /** Takes the outage slot and damages every unit; logging and the tablet are the caller's. / 占用破坏名额并让所有装置受损；日志与平板由调用方处理。 */
    private static void start(ServerWorld world, SabotageWorldComponent sabotage, long triggeredAt,
                              @Nullable UUID initiator, String initiatorName, boolean masterSaboteur) {
        sabotage.startOutage(SabotageType.ENGINE, OutageMode.REPAIR, triggeredAt, initiator, initiatorName,
                masterSaboteur);
        sabotage.setEngineTriggeredAt(triggeredAt);
        sabotage.setAppliedEnginePhase(EnginePhase.CRITICAL);
        setUnitLooks(world, sabotage.engineUnits(), EngineCondition.DAMAGED);
        SabotageService.syncStatusNow(world);
    }

    /**
     * Runs the side effects of every phase the clock has passed since the last call, each exactly once and in order
     * (CRITICAL, WRECKED, EMERGENCY; both when ticks were skipped). Idempotent; safe to call from any entry point.
     * 执行自上次调用以来时钟走过的每个阶段的副作用，每个只执行一次、按顺序（CRITICAL、WRECKED、EMERGENCY；跳过了 tick 时两次
     * 都执行）。可以重复调用，结果一样；任何入口都可以安全调用。
     */
    public static void advance(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        EnginePhase derived = sabotage.enginePhase(world.getTime());
        if (derived == null) {
            return;
        }
        for (EnginePhase next : EngineRules.pendingTransitions(sabotage.appliedEnginePhase(), derived)) {
            // Marked applied first, so nothing these side effects call can run the same transition again.
            // 先标记为已应用，这些副作用调用到的任何代码都不会再执行同一次转换。
            sabotage.setAppliedEnginePhase(next);
            switch (next) {
                case CRITICAL -> {
                    // Entered by the trigger itself. / 由触发本身进入。
                }
                case WRECKED -> onWrecked(world, sabotage);
                case EMERGENCY -> onEmergency(world, sabotage);
            }
        }
    }

    /** The 60 s ran out: units wrecked, the train halts, quick panels close. / 60 秒已过：装置损毁，列车停驶，抢修面板关闭。 */
    private static void onWrecked(ServerWorld world, SabotageWorldComponent sabotage) {
        long now = world.getTime();
        setUnitLooks(world, sabotage.engineUnits(), EngineCondition.WRECKED);
        for (BlockPos pos : sabotage.engineUnits()) {
            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.4F,
                    0.9F + world.random.nextFloat() * 0.2F);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8F, 0.8F);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                    12, 0.25, 0.2, 0.25, 0.02);
        }
        EngineRepairService.onPhaseChanged(world);
        sabotage.log(now, SabotageWorldComponent.LogKind.ENGINE_WRECKED, "", SabotageType.ENGINE);
        // Re-syncs the round timer itself as the frozen state changes. / 冻结状态改变时，它会自己重新同步回合倒计时。
        SabotageService.syncStatusNow(world);
        TabletStateService.syncToTabletHolders(world);
    }

    /** Halted for the cap: half speed, the timer runs again, the units stay wrecked. / 停驶到上限：半速行驶，倒计时恢复，装置仍是损毁。 */
    private static void onEmergency(ServerWorld world, SabotageWorldComponent sabotage) {
        setUnitLooks(world, sabotage.engineUnits(), EngineCondition.WRECKED);
        // Overhaul panels stay open: WRECKED and EMERGENCY need the same repair. / 大修面板不关：两个阶段要做的是同一种维修。
        EngineRepairService.onPhaseChanged(world);
        sabotage.log(world.getTime(), SabotageWorldComponent.LogKind.ENGINE_EMERGENCY, "", SabotageType.ENGINE);
        SabotageService.syncStatusNow(world);
        TabletStateService.syncToTabletHolders(world);
    }

    /** End of every world tick in an active round. / 进行中对局里每个世界 tick 的末尾。 */
    public static void tick(ServerWorld world, SabotageWorldComponent sabotage) {
        if (!sabotage.isEngineOn()) {
            return;
        }
        advance(world);
        long since = world.getTime() - sabotage.engineTriggeredAt();
        boolean alarm = since % EngineRules.ALARM_INTERVAL_TICKS == 0;
        boolean second = since % 20 == 0;
        if (alarm || second) {
            EnginePhase phase = sabotage.enginePhase(world.getTime());
            if (alarm && phase == EnginePhase.CRITICAL) {
                alarm(world, sabotage);
            }
            if (second && phase != null) {
                actionBar(world, sabotage, phase);
            }
        }
    }

    /** Alarm and action bar right away, without waiting for the next whole second. / 立即发出警报与动作栏，不等下一个整秒。 */
    private static void feedback(ServerWorld world, SabotageWorldComponent sabotage) {
        EnginePhase phase = sabotage.enginePhase(world.getTime());
        if (phase == null) {
            return;
        }
        if (phase == EnginePhase.CRITICAL) {
            alarm(world, sabotage);
        }
        actionBar(world, sabotage, phase);
    }

    private static void actionBar(ServerWorld world, SabotageWorldComponent sabotage, EnginePhase phase) {
        long now = world.getTime();
        long triggeredAt = sabotage.engineTriggeredAt();
        Text message = switch (phase) {
            case CRITICAL -> Text.translatable("message.sparkstrength.engine.critical",
                            clock(EngineRules.ceilSeconds(EngineRules.remainingTicks(now, triggeredAt))))
                    .formatted(EngineRules.urgent(now, triggeredAt) ? Formatting.RED : Formatting.GOLD);
            case WRECKED -> Text.translatable("message.sparkstrength.engine.wrecked",
                    clock(EngineRules.haltedTicks(now, triggeredAt) / 20)).formatted(Formatting.RED);
            case EMERGENCY -> Text.translatable("message.sparkstrength.engine.emergency").formatted(Formatting.GOLD);
        };
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.sendMessage(message, true);
        }
    }

    /**
     * A low bass note from the damaged units. Each player hears only the nearest unit within range, so several units
     * never stack into a din.
     * 受损装置发出的低沉贝斯音。每个玩家只听到范围内最近的一台，几台同时响也不会叠成噪音。
     */
    private static void alarm(ServerWorld world, SabotageWorldComponent sabotage) {
        List<BlockPos> units = sabotage.engineUnits();
        if (units.isEmpty()) {
            return;
        }
        long seed = world.random.nextLong();
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlockPos nearest = null;
            double best = ALARM_RANGE * ALARM_RANGE;
            for (BlockPos pos : units) {
                double distance = player.squaredDistanceTo(Vec3d.ofCenter(pos));
                if (distance <= best) {
                    best = distance;
                    nearest = pos;
                }
            }
            if (nearest != null) {
                player.networkHandler.sendPacket(new PlaySoundS2CPacket(SoundEvents.BLOCK_NOTE_BLOCK_BASS,
                        SoundCategory.BLOCKS, nearest.getX() + 0.5, nearest.getY() + 0.5, nearest.getZ() + 0.5,
                        1.0F, ALARM_PITCH, seed));
            }
        }
    }

    /**
     * A finished repair panel at {@code unit} for the outage {@code outageId}; false when that outage is already over
     * (someone else was first).
     * 在 unit 上为 outageId 那次破坏完成了维修面板；那次破坏已经结束（别人先修好了）时返回 false。
     */
    static boolean repair(ServerWorld world, int outageId, ServerPlayerEntity repairer, BlockPos unit) {
        SabotageWorldComponent.Outage outage = SabotageWorldComponent.KEY.get(world).outage();
        if (outage == null || outage.type() != SabotageType.ENGINE || outage.id() != outageId) {
            return false;
        }
        finish(world, EndReason.REPAIRED, repairer, unit);
        return true;
    }

    /** Ends the engine sabotage without a repair: no cooldown, nobody credited. / 不经维修结束动力破坏：不开始冷却，不记功劳。 */
    public static void abort(ServerWorld world) {
        finish(world, EndReason.ABORTED, null, null);
    }

    /**
     * The one way an engine sabotage ends; runs once, since the outage is gone afterwards. Units back to intact, every
     * engine panel closed, the round timer resynced. A repair also starts the shared team cooldown and celebrates.
     * Only an engine outage ends here; a lights outage belongs to {@link LightsSabotageService}.
     * 动力破坏唯一的结束方式；只执行一次，因为之后破坏名额已经空了。装置恢复完好，所有动力面板关闭，回合倒计时重新同步。修好时
     * 还会开始团队共享冷却并播放庆祝效果。这里只结束动力破坏；灯光熄灯归 LightsSabotageService 处理。
     *
     * @param actor the repairer (null for an abort), kept for the replay record / 修理人（中止时为 null），留给回放记录
     * @param unit  the repaired unit, where the big burst goes / 被修好的装置，大团闪光出现的位置
     */
    public static void finish(ServerWorld world, EndReason reason, @Nullable ServerPlayerEntity actor,
                              @Nullable BlockPos unit) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        if (!sabotage.isEngineOn()) {
            return;
        }
        long now = world.getTime();
        EnginePhase phase = sabotage.enginePhase(now);
        sabotage.endOutage();
        sabotage.clearEngine();
        setUnitLooks(world, sabotage.engineUnits(), EngineCondition.INTACT);
        EngineRepairService.closeAll(world, reason == EndReason.REPAIRED
                ? CloseBreakerRepairS2CPacket.REASON_POWER_BACK : CloseBreakerRepairS2CPacket.REASON_OTHER);
        if (reason == EndReason.REPAIRED) {
            sabotage.setTeamCooldownEndTick(now + SabotageRules.TEAM_COOLDOWN_TICKS);
            sabotage.log(now, SabotageWorldComponent.LogKind.REPAIRED, "", SabotageType.ENGINE);
            celebrate(world, sabotage.engineUnits(), unit, phase != null && phase != EnginePhase.CRITICAL);
            Text restored = Text.translatable("message.sparkstrength.engine.repaired").formatted(Formatting.GREEN);
            for (ServerPlayerEntity player : world.getPlayers()) {
                player.sendMessage(restored, true);
            }
        }
        SabotageService.syncStatusNow(world);
        TabletStateService.syncToTabletHolders(world);
    }

    /**
     * A big green burst at the repaired unit's panel, a small one at every other unit; the train sounds its horn when
     * it was halted or limping on emergency power.
     * 被修好的那台装置面板处冒出一大团绿色闪光，其他每台各冒一小团；列车之前停驶或靠应急动力行驶时，还会鸣一声汽笛。
     */
    private static void celebrate(ServerWorld world, List<BlockPos> units, @Nullable BlockPos repaired, boolean horn) {
        for (BlockPos pos : units) {
            boolean main = pos.equals(repaired);
            Vec3d panel = panelPoint(world, pos);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, panel.x, panel.y, panel.z, main ? 40 : 8,
                    main ? 0.45 : 0.25, main ? 0.4 : 0.2, main ? 0.45 : 0.25, 0.0);
        }
        if (repaired != null) {
            world.playSound(null, repaired, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        if (horn) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                player.playSoundToPlayer(WatheSounds.AMBIENT_TRAIN_HORN, SoundCategory.AMBIENT, HORN_VOLUME, 1.0F);
            }
        }
    }

    /** Just in front of a unit's gauge panel, which faces the unit's {@code facing}. / 装置仪表面板正前方一点，面板朝向即装置的 facing。 */
    private static Vec3d panelPoint(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Direction facing = state.isOf(SparkStrengthBlocks.engineUnit()) ? state.get(EngineUnitBlock.FACING)
                : Direction.NORTH;
        return new Vec3d(pos.getX() + 0.5 + facing.getOffsetX() * 0.55, pos.getY() + 0.5,
                pos.getZ() + 0.5 + facing.getOffsetZ() * 0.55);
    }

    /**
     * Tooling (dev harnesses, admin checks): put the engine sabotage exactly at the start of {@code phase}, or end it
     * ({@code null}, same as an abort). Starts an engine sabotage when none is on, ending any lights outage first, and
     * backdates the trigger tick so the derived phase is the requested one; then runs the transitions like the clock
     * would. Going back to an earlier phase skips the side effects. No log line, no cooldown, no initiator.
     * 工具用（开发 harness、管理员检查）：把动力破坏放到 phase 恰好开始的时刻，或结束它（null，等同中止）。没有动力破坏时会开始
     * 一个（先结束可能存在的灯光熄灯），并把触发 tick 往前拨，让推导出的阶段正好是所要的阶段；然后像时钟走到那里一样执行阶段转换。
     * 退回更早的阶段时不执行副作用。不记日志，不开始冷却，没有发起人。
     */
    public static void forcePhase(ServerWorld world, @Nullable EnginePhase phase) {
        if (phase == null) {
            abort(world);
            return;
        }
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        long now = world.getTime();
        if (sabotage.enginePhase(now) == phase) {
            return;
        }
        long triggeredAt = EngineRules.triggerTickFor(phase, now);
        if (!sabotage.isEngineOn()) {
            if (sabotage.outage() != null) {
                SabotageService.finishActive(world);
            }
            start(world, sabotage, triggeredAt, null, "", false);
        } else {
            sabotage.setEngineTriggeredAt(triggeredAt);
            EnginePhase applied = sabotage.appliedEnginePhase();
            if (applied != null && EngineRules.isBefore(phase, applied)) {
                sabotage.setAppliedEnginePhase(phase);
                setUnitLooks(world, sabotage.engineUnits(), EngineCondition.of(phase));
                EngineRepairService.onPhaseChanged(world);
                SabotageService.syncStatusNow(world);
            }
        }
        advance(world);
        feedback(world, sabotage);
        TabletStateService.syncToTabletHolders(world);
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
     * Right-click on an engine unit. A living player in the round gets the repair panel for the current phase on a unit
     * the map scan registered; everyone else (spectators, no sabotage on, a decorative unit outside the play area) gets
     * the read-only status panel.
     * 右键动力装置。本局存活的玩家对地图扫描登记过的装置打开当前阶段对应的维修面板；其他人（旁观者、没有破坏进行、对局区外的
     * 装饰装置）打开只读的状态面板。
     */
    public static void onUnitUse(ServerPlayerEntity player, BlockPos pos) {
        if (SabotageActionLocks.isLocked(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        advance(world);
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        EnginePhase phase = sabotage.enginePhase(world.getTime());
        if (phase == null || !SabotageAccess.canRepairBreaker(player) || !sabotage.engineUnits().contains(pos)) {
            EngineRepairService.openStatus(player, pos);
            return;
        }
        EngineRepairService.open(player, world, sabotage, pos, phase);
    }

    private static String clock(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
