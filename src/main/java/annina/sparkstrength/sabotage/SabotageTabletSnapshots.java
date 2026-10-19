package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The tablet's sabotage page for one viewer who may sabotage: one card per type and the log, newest first.
 * 可以破坏的查看者看到的平板破坏页：每种破坏一张卡片，以及最新在前的日志。
 */
public final class SabotageTabletSnapshots {
    private SabotageTabletSnapshots() {
    }

    public static List<TabletSnapshot.SabotageRow> rows(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.Outage outage = sabotage.outage();
        long now = world.getTime();
        List<TabletSnapshot.SabotageRow> rows = new ArrayList<>();
        for (SabotageType type : SabotageType.values()) {
            if (!type.availableOn(sabotage.hasEngineUnits())) {
                // No engine units on this map: no engine card at all. / 本图没有动力装置：根本不显示动力卡片。
                continue;
            }
            boolean thisActive = outage != null && outage.type() == type;
            SabotageRowState state = SabotageRowState.of(thisActive, outage != null && !thisActive,
                    sabotage.isUnlocked(type), now, sabotage.readyTick(type), sabotage.teamCooldownEndTick());
            long seconds = switch (state) {
                // Engine: the repair window counts down, then how long the train has been halted counts up.
                // 动力：抢修窗口向下数，之后已停驶时间向上数。
                case ACTIVE -> type == SabotageType.ENGINE
                        ? EngineRules.cardSeconds(now, sabotage.engineTriggeredAt())
                        : (now - outage.startTick()) / 20;
                case ARMING -> ceilSeconds(sabotage.readyTick(type) - now);
                case COOLDOWN -> ceilSeconds(sabotage.teamCooldownEndTick() - now);
                default -> 0;
            };
            int boxes = 0;
            int opened = 0;
            int hacked = 0;
            for (BlockPos pos : sabotage.junctionBoxes()) {
                BlockState box = world.getBlockState(pos);
                if (!box.isOf(SparkStrengthBlocks.junctionBox()) || JunctionBoxBlock.typeOf(box) != type) {
                    continue;
                }
                boxes++;
                switch (box.get(JunctionBoxBlock.STATE)) {
                    case BROKEN -> opened++;
                    case HACKED -> hacked++;
                    default -> {
                    }
                }
            }
            // (typeWire, modeWire) read together: lights send their OutageMode, the engine its EnginePhase.
            // (typeWire, modeWire) 合起来解释：灯光发 OutageMode，动力发 EnginePhase。
            int modeWire = !thisActive ? 0 : type == SabotageType.ENGINE
                    ? EnginePhase.wireOf(sabotage.enginePhase(now)) : outage.mode().wire();
            rows.add(new TabletSnapshot.SabotageRow(type.wire(), state.wire(), (int) Math.min(Integer.MAX_VALUE, seconds),
                    modeWire, boxes, opened, hacked));
        }
        return rows;
    }

    /**
     * The log as {@code viewer} may read it: names of actors they have not linked with are sent empty, which the
     * client shows as "???" (the wire format is unchanged). Actor uuids never leave the server.
     * 查看者能读到的日志：未互认操作者的名字以空串下发，客户端显示为「???」（线上格式不变）。操作者 uuid 绝不离开服务端。
     */
    public static List<TabletSnapshot.SabotageLogRow> log(ServerWorld world, UUID viewer, Set<UUID> viewerLinks) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        List<SabotageWorldComponent.LogEntry> entries = sabotage.logEntries();
        List<TabletSnapshot.SabotageLogRow> rows = new ArrayList<>(entries.size());
        for (int i = entries.size() - 1; i >= 0; i--) {
            SabotageWorldComponent.LogEntry entry = entries.get(i);
            String actor = SabotageActorVisibility.revealsActor(entry.actorUuid(), viewer, viewerLinks)
                    ? entry.actor() : "";
            rows.add(new TabletSnapshot.SabotageLogRow(entry.kind().wire(), entry.type().wire(), actor,
                    (int) Math.max(0, (entry.tick() - sabotage.roundStartTick()) / 20)));
        }
        return rows;
    }

    private static long ceilSeconds(long ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }
}
