package annina.sparkstrength.role.attendant;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.detective.DetectiveIdentityResolver;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * The tablet's door-log page for one viewer who holds the door monitor feature.
 * 拥有房门监控功能的查看者看到的平板房门记录页。
 */
public final class DoorLogTabletSnapshots {
    private DoorLogTabletSnapshots() {
    }

    /**
     * Door log for one viewer, newest first. Entries already store the apparent (disguise-aware) identity, never a
     * disguised player's real uuid; a viewer whose names are hidden sees every actor as anonymous.
     * 单个观看者的房门记录，最新在前。条目只存表面身份（考虑伪装），绝不存伪装者的真实 UUID；名字被隐藏的观看者看到的操作者全部匿名。
     */
    public static List<TabletSnapshot.DoorLogRow> rows(ServerPlayerEntity viewer, TabletWorldComponent tablet) {
        List<DoorLog.Entry> entries = tablet.doorLog().newestFirst();
        if (entries.isEmpty()) {
            return List.of();
        }
        boolean hideNames = DetectiveIdentityResolver.viewerSeesNoNames(viewer);
        return entries.stream()
                .map(entry -> {
                    DoorLog.Actor actor = AttendantRules.actorShownTo(entry.actor(), hideNames);
                    return new TabletSnapshot.DoorLogRow(
                            entry.id(),
                            entry.kind().wire(),
                            entry.doorName(),
                            actor != null,
                            actor == null ? null : actor.displayUuid(),
                            actor == null ? "" : actor.displayName(),
                            entry.lastTick(),
                            entry.count()
                    );
                })
                .toList();
    }
}
