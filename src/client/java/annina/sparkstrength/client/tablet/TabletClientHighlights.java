package annina.sparkstrength.client.tablet;

import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletAccess;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletRules;
import annina.sparkstrength.tablet.TabletShopRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tablet suspect highlights use Wathe's outline event without requiring the instinct key.
 * 平板嫌疑人高亮使用 Wathe 描边事件，但不要求按下本能键。
 *
 * <p>Tablets never outline channel members: police members identify each other only through the task-gated member
 * list ({@link annina.sparkstrength.tablet.TabletIdentityRules}), killer and witch viewers fall back to their own
 * instinct/cohort. A killer outline cannot beat NoellesRoles' Undercover masquerade (PRIORITY_HIGH) without also
 * beating every {@code skip()} (also PRIORITY_HIGH), so it would mark exactly the linked partners who are real killers.
 * Only police suspects are highlighted: through walls during the periodic reveal window, and in sight when the
 * suspect is also a member row the server sent this viewer (so a viewer whose police identities are still locked
 * never gets that in-sight marker). The snapshot is already redacted server-side, so non-police viewers never receive
 * suspects; the POLICE check here is a second guard. Dead viewers get nothing so Wathe's spectator role colours stay
 * intact.
 * 平板从不描边频道成员：义警成员只能通过按任务解锁的成员列表互相识别（见 TabletIdentityRules），杀手与魔女观看者回到各自的
 * 本能与同伙标记。杀手描边若要压过 NoellesRoles 的卧底伪装（PRIORITY_HIGH）就必然同时压过所有 skip()（同为
 * PRIORITY_HIGH），于是会恰好标出已互认同伴中的真杀手。只高亮义警嫌疑人：周期透视窗口内可穿墙；在视线内时，仅当该嫌疑人
 * 也是服务端下发给此观看者的成员行（因此义警身份尚未解锁的观看者不会得到这种视线内标记）。快照已在服务端裁剪，
 * 非义警观看者收不到嫌疑人；这里的 POLICE 判断是第二道防线。死亡观看者不返回结果，以保留 Wathe 旁观者身份颜色。</p>
 */
public final class TabletClientHighlights {
    private static final int SUSPECT_PRIORITY = 80;

    /** UUID sets of the snapshot below, rebuilt only when a new snapshot arrives. / 下方快照的 UUID 集合，只在收到新快照时重建。 */
    private static TabletSnapshot indexedSnapshot;
    private static Set<UUID> suspectIds = Set.of();
    private static Set<UUID> connectionIds = Set.of();

    private TabletClientHighlights() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(TabletClientHighlights::highlight);
    }

    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer) || targetPlayer == viewer) {
            return null;
        }
        // Cheapest first: this runs for every rendered entity several times a frame, and only police suspects answer.
        // 最便宜的判断在前：每帧会对每个渲染实体调用多次，而只有义警嫌疑人会作答。
        TabletSnapshot snapshot = TabletClientState.snapshot();
        if (!snapshot.localHasTablet() || snapshot.channel() != TabletChannel.POLICE) {
            return null;
        }
        index(snapshot);
        UUID targetUuid = targetPlayer.getUuid();
        boolean targetSuspect = suspectIds.contains(targetUuid);
        if (!targetSuspect) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer) || !TabletAccess.hasTabletInHotbar(viewer)) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(targetPlayer)) {
            return null;
        }
        boolean targetConnected = connectionIds.contains(targetUuid);

        // Periodic suspect reveals are intentional through-wall tablet ESP (police network only).
        // 嫌疑人的周期透视不受墙体视线阻挡（仅义警网络）。
        if (targetSuspect && isSuspectRevealWindow(viewer)) {
            return GetInstinctHighlight.HighlightResult.always(
                    TabletShopRules.SUSPECT_HIGHLIGHT_COLOR,
                    SUSPECT_PRIORITY
            );
        }
        // Member rows exist only for identities this viewer may see, so a locked police viewer never matches here.
        // The line-of-sight raycast runs last, only for a suspect that is also a member row.
        // 成员行只包含此观看者可见的身份，因此身份尚未解锁的义警观看者不会命中此分支。视线射线检测放在最后，
        // 只对同时也是成员行的嫌疑人执行。
        if (targetSuspect && targetConnected) {
            return viewer.canSee(targetPlayer)
                    ? GetInstinctHighlight.HighlightResult.always(
                            TabletShopRules.SUSPECT_HIGHLIGHT_COLOR,
                            SUSPECT_PRIORITY
                    )
                    : null;
        }
        return null;
    }

    private static void index(TabletSnapshot snapshot) {
        if (snapshot == indexedSnapshot) {
            return;
        }
        indexedSnapshot = snapshot;
        suspectIds = uuids(snapshot.suspects(), TabletSnapshot.SuspectRow::uuid);
        connectionIds = uuids(snapshot.connections(), TabletSnapshot.PlayerRow::uuid);
    }

    private static <T> Set<UUID> uuids(List<T> rows, Function<T, UUID> uuid) {
        Set<UUID> ids = new HashSet<>(rows.size() * 2);
        for (T row : rows) {
            ids.add(uuid.apply(row));
        }
        return ids;
    }

    private static boolean isSuspectRevealWindow(PlayerEntity viewer) {
        int tick = viewer.age % TabletRules.SUSPECT_REVEAL_INTERVAL_TICKS;
        return tick < TabletRules.SUSPECT_REVEAL_TICKS;
    }
}
