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

import java.util.UUID;

/**
 * Tablet highlights use Wathe's outline event without requiring the instinct key.
 * 平板高亮使用 Wathe 描边事件，但不要求按下本能键。
 *
 * <p>Only the police network outlines members ({@link TabletChannel#outlinesMembers()}); killer and witch viewers get
 * no tablet outline and fall back to their own instinct/cohort. A killer outline cannot beat NoellesRoles' Undercover
 * masquerade (PRIORITY_HIGH) without also beating every {@code skip()} (also PRIORITY_HIGH), so it would mark exactly
 * the linked partners who are real killers. The snapshot is already redacted server-side, so non-police viewers never
 * receive suspects; the POLICE check here is a second guard. Dead viewers get nothing so Wathe's spectator role
 * colours stay intact.
 * 只有义警网络描边成员（见 TabletChannel#outlinesMembers）；杀手与魔女观看者没有平板描边，回到各自的本能与同伙标记。
 * 杀手描边若要压过 NoellesRoles 的卧底伪装（PRIORITY_HIGH）就必然同时压过所有 skip()（同为 PRIORITY_HIGH），
 * 于是会恰好标出已互认同伴中的真杀手。快照已在服务端裁剪，非义警观看者收不到嫌疑人；这里的 POLICE 判断是第二道防线。
 * 死亡观看者不返回结果，以保留 Wathe 旁观者身份颜色。</p>
 */
public final class TabletClientHighlights {
    private static final int TABLET_PRIORITY = 70;
    private static final int SUSPECT_PRIORITY = 80;

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
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer) || !TabletAccess.hasTabletInHotbar(viewer)) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(targetPlayer)) {
            return null;
        }

        TabletSnapshot snapshot = TabletClientState.snapshot();
        TabletChannel channel = snapshot.channel();
        if (!snapshot.localHasTablet() || channel == null) {
            return null;
        }

        UUID targetUuid = targetPlayer.getUuid();
        boolean targetConnected = snapshot.connections().stream().anyMatch(row -> row.uuid().equals(targetUuid));
        boolean targetSuspect = channel == TabletChannel.POLICE
                && snapshot.suspects().stream().anyMatch(row -> row.uuid().equals(targetUuid));

        // Periodic suspect reveals are intentional through-wall tablet ESP (police network only).
        // 嫌疑人的周期透视不受墙体视线阻挡（仅义警网络）。
        if (targetSuspect && isSuspectRevealWindow(viewer)) {
            return GetInstinctHighlight.HighlightResult.always(
                    TabletShopRules.SUSPECT_HIGHLIGHT_COLOR,
                    SUSPECT_PRIORITY
            );
        }
        if (!viewer.canSee(targetPlayer)) {
            return null;
        }
        if (targetSuspect && targetConnected) {
            return GetInstinctHighlight.HighlightResult.always(
                    TabletShopRules.SUSPECT_HIGHLIGHT_COLOR,
                    SUSPECT_PRIORITY
            );
        }
        if (targetConnected && channel.outlinesMembers()) {
            return GetInstinctHighlight.HighlightResult.always(TabletShopRules.TABLET_HIGHLIGHT_COLOR, TABLET_PRIORITY);
        }
        return null;
    }

    private static boolean isSuspectRevealWindow(PlayerEntity viewer) {
        int tick = viewer.age % TabletRules.SUSPECT_REVEAL_INTERVAL_TICKS;
        return tick < TabletRules.SUSPECT_REVEAL_TICKS;
    }
}
