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
            boolean thisActive = outage != null && outage.type() == type;
            SabotageRowState state = SabotageRowState.of(thisActive, outage != null && !thisActive,
                    sabotage.isUnlocked(type), now, sabotage.readyTick(type), sabotage.teamCooldownEndTick());
            long seconds = switch (state) {
                case ACTIVE -> (now - outage.startTick()) / 20;
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
            rows.add(new TabletSnapshot.SabotageRow(type.wire(), state.wire(), (int) Math.min(Integer.MAX_VALUE, seconds),
                    thisActive ? outage.mode().wire() : 0, boxes, opened, hacked));
        }
        return rows;
    }

    public static List<TabletSnapshot.SabotageLogRow> log(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        List<SabotageWorldComponent.LogEntry> entries = sabotage.logEntries();
        List<TabletSnapshot.SabotageLogRow> rows = new ArrayList<>(entries.size());
        for (int i = entries.size() - 1; i >= 0; i--) {
            SabotageWorldComponent.LogEntry entry = entries.get(i);
            rows.add(new TabletSnapshot.SabotageLogRow(entry.kind().wire(), entry.type().wire(), entry.actor(),
                    (int) Math.max(0, (entry.tick() - sabotage.roundStartTick()) / 20)));
        }
        return rows;
    }

    private static long ceilSeconds(long ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }
}
