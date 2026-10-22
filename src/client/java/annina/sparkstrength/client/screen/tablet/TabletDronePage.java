package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletScreen.Grid;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonStyle;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.EmptyIcon;
import annina.sparkstrength.network.drone.DronePilotStartC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.tablet.TabletFeature;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import annina.sparkstrength.tablet.TabletUiSession;
import annina.sparkstrength.tablet.TabletUiSession.Section;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Bomber drone section: one card per drone of the viewer's own with a connect button, above the pilot controls
 * cheat-sheet. The page only asks to connect ({@link DronePilotStartC2SPacket}); the server re-validates every request.
 * 炸弹客无人机分区：查看者自己的每架无人机一张带连接按钮的卡片，下方为驾驶操作提示。本页只发出连接请求，服务端会重新校验每个请求。
 */
final class TabletDronePage implements TabletPage {
    // How long a sent drone connect request shows "connecting" before the button re-arms. 连接请求显示“连接中”的时长。
    private static final int PENDING_PILOT_TICKS = 40;

    private final TabletScreen screen;
    // Drone button states of the current build; a snapshot that changes them rebuilds the widgets. 当前构建的无人机按钮状态。
    private List<DroneButtonKey> lastDroneButtons = List.of();
    private int pendingPilotEntityId = -1;
    private int pendingPilotTicks;
    // First visible drone row when the latest snapshot arrived, before it was clamped. 最新快照到达时（限制范围前）的首个可见行。
    private int snapshotFirstRow;

    TabletDronePage(TabletScreen screen) {
        this.screen = screen;
    }

    @Override
    public void prepare(TabletSnapshot snapshot) {
        clampRows(snapshot);
        lastDroneButtons = droneButtonKeys(snapshot);
    }

    @Override
    public void onSnapshot(TabletSnapshot snapshot, boolean laidOut) {
        snapshotFirstRow = screen.session().droneFirstRow();
        if (laidOut) {
            clampRows(snapshot);
        }
        if (pendingPilotEntityId >= 0 && !pendingPilotStillConnectable(snapshot)) {
            // Linked, lost or gone: the request is settled either way. 已连接、失控或已消失：请求已有结果。
            pendingPilotEntityId = -1;
            pendingPilotTicks = 0;
        }
    }

    private void clampRows(TabletSnapshot snapshot) {
        screen.session().clampDrones(droneRows(snapshot).size(), droneGrid().visibleRows());
    }

    @Override
    public boolean needsRebuild(TabletSnapshot snapshot) {
        return snapshotFirstRow != screen.session().droneFirstRow()
                || !droneButtonKeys(snapshot).equals(lastDroneButtons);
    }

    @Override
    public void tick(boolean open) {
        if (pendingPilotTicks > 0 && --pendingPilotTicks == 0) {
            // No answer (refused, e.g. body not on the ground): re-arm the connect button. 未获响应（被拒绝）：恢复连接按钮。
            pendingPilotEntityId = -1;
            if (open) {
                screen.refresh();
            }
        }
    }

    /**
     * One connect button per visible drone card. Only placed, controllable drones get an active button; the others
     * show why not (stowed, cooling down, falling, already linked, another link busy). The server re-validates every
     * request.
     * 每张可见无人机卡片一个连接按钮。只有已放置且可操控的无人机按钮可用；其余显示原因（在背包、冷却、坠落、已连接、
     * 链路占线）。服务端会重新校验每个请求。
     */
    @Override
    public void init(TabletSnapshot snapshot) {
        TextRenderer textRenderer = screen.textRenderer();
        Grid grid = droneGrid();
        List<TabletSnapshot.DroneRow> rows = droneRows(snapshot);
        boolean linkBusy = droneLinkBusy(rows);
        int first = screen.session().droneFirstRow();
        for (int row = 0; row < grid.visibleRows() && first + row < rows.size(); row++) {
            TabletSnapshot.DroneRow drone = rows.get(first + row);
            DroneAction action = droneAction(drone, linkBusy);
            Rect rect = TabletDronePainter.droneButtonRect(textRenderer, grid.area().x(), grid.rowY(row),
                    grid.area().width());
            String focusKey = drone.placed() ? "drone:" + drone.entityId() : "drone:item:" + (first + row);
            // Drone accent whatever the channel, like the door monitor's live chip. 与房门监控相同，无论频道都用无人机强调色。
            screen.addButton(focusKey, rect, Text.translatable(KEY + action.labelKey()), action.style(), action.icon(),
                    TabletTheme.DRONE, () -> connectDrone(drone))
                    .active = action == DroneAction.CONNECT;
        }
    }

    private void connectDrone(TabletSnapshot.DroneRow drone) {
        // No-op when the server never declared the pilot channel (or there is no connection).
        // 服务端未声明驾驶通道（或无连接）时不发送。
        if (!drone.placed() || pendingPilotEntityId >= 0
                || !ClientPlayNetworking.canSend(DronePilotStartC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new DronePilotStartC2SPacket(drone.entityId()));
        pendingPilotEntityId = drone.entityId();
        pendingPilotTicks = PENDING_PILOT_TICKS;
        // The pilot client closes the tablet for the drone camera; reopening it lands back on the drone list.
        // 驾驶客户端会关闭平板以切换到无人机视角；之后再打开平板时回到无人机列表。
        TabletClientState.resumeOnNextOpen(Section.DRONE);
        screen.refresh();
    }

    @Override
    public boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        TabletUiSession session = screen.session();
        Grid grid = droneGrid();
        if (grid.area().contains(x, y)) {
            // Each visible card owns a button, so a moved window rebuilds like SUSPECTS. 每张可见卡片带按钮，滚动后与嫌疑分区一样重建。
            int previous = session.droneFirstRow();
            session.scrollDrones(verticalAmount, droneRows(snapshot).size(), grid.visibleRows());
            if (session.droneFirstRow() != previous) {
                screen.refresh();
            }
            return true;
        }
        return false;
    }

    /**
     * Drones flying under control (hovering, flying, piloted) as a live drone-accent chip; with none, falling drones as
     * a red alarm chip; else the drone count.
     * 有受控飞行的无人机（悬停、飞行、驾驶中）时显示无人机强调色的实时胶囊；没有时若有坠落中的无人机则显示红色警报胶囊；
     * 否则显示无人机数量。
     */
    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        List<TabletSnapshot.DroneRow> rows = droneRows(snapshot);
        int airborne = 0;
        int falling = 0;
        for (TabletSnapshot.DroneRow row : rows) {
            if (isAirborne(row)) {
                airborne++;
            } else if (row.placed() && row.statusWire() == TabletSnapshot.DroneRow.STATUS_FALLING) {
                falling++;
            }
        }
        if (airborne > 0) {
            return screen.paintPill(right, centerY, maxWidth, Text.translatable(KEY + "drone.airborne", airborne).getString(),
                    TabletTheme.DRONE.pale(), TabletTheme.withAlpha(TabletTheme.DRONE.base(), 0x2E), 0,
                    (canvas, x, y, size, color) -> screen.paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                    TabletTheme.DRONE.base());
        }
        if (falling > 0) {
            return screen.paintPill(right, centerY, maxWidth, Text.translatable(KEY + "drone.falling_count", falling).getString(),
                    TabletTheme.KILLER.pale(), TabletTheme.withAlpha(TabletTheme.DANGER, 0x33), 0,
                    (canvas, x, y, size, color) -> screen.paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                    TabletTheme.DANGER);
        }
        return screen.paintPill(right, centerY, maxWidth, String.valueOf(rows.size()), TabletTheme.TEXT_2, TabletTheme.SURFACE,
                0, TabletIcons::drone, TabletTheme.TEXT_2);
    }

    @Override
    public void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
        // A falling drone is an alarm; one in the air without its pilot (draining) pulses.
        // 坠落中的无人机为警报红点；无人驾驶仍在空中（持续耗电）的无人机显示脉冲点。
        DroneAlert alert = droneAlert(snapshot);
        if (alert == DroneAlert.FALLING) {
            screen.paintBadgeDot(icon, TabletTheme.DANGER);
        } else if (alert == DroneAlert.UNATTENDED) {
            screen.paintBadgePulse(icon, TabletTheme.DRONE.base(), now);
        }
    }

    /**
     * One card per drone (connect buttons are widgets, see init) above the pilot controls cheat-sheet; an empty
     * list centres its empty state in the whole body and hides the cheat-sheet.
     * 每架无人机一张卡片（连接按钮是控件，见 init），下方为驾驶操作提示；没有无人机时空状态在整个正文区居中，且不显示操作提示。
     */
    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        TabletLayout layout = screen.layout();
        List<TabletSnapshot.DroneRow> rows = droneRows(snapshot);
        if (rows.isEmpty()) {
            TabletSectionPainter.emptyState(c, textRenderer, layout.bodyNoFooter(), EmptyIcon.DRONE,
                    Text.translatable(KEY + "drone.empty"), Text.translatable(KEY + "drone.empty_hint"));
            return;
        }
        Grid grid = droneGrid();
        int first = screen.session().droneFirstRow();
        for (int row = 0; row < grid.visibleRows() && first + row < rows.size(); row++) {
            TabletSnapshot.DroneRow drone = rows.get(first + row);
            TabletDronePainter.droneCard(c, textRenderer, grid.area().x(), grid.rowY(row), grid.area().width(), drone,
                    Objects.requireNonNull(DroneKind.fromWire(drone.kindWire())), now);
        }
        TabletSectionPainter.scrollThumb(c, screen.scrollTrack(grid.area()), first, grid.visibleRows(), rows.size());
        int padX = layout.mode().padX();
        TabletDronePainter.droneControls(c, textRenderer, layout.footer().inset(padX, 0, padX, 0), screen.client().options);
    }

    /** Above the controls footer. 位于操作提示页脚之上。 */
    private Grid droneGrid() {
        return Grid.of(screen.layout().list(), 1, TabletDronePainter.DRONE_CARD_H, TabletDronePainter.DRONE_ROW_STEP);
    }

    /**
     * The viewer's drone rows this client can show, in server order; empty without the feature. Rows of a kind this
     * client does not know (newer server) are skipped.
     * 本客户端可显示的查看者无人机行（服务端顺序）；无此功能时为空。跳过本客户端不认识的型号（更新的服务端）。
     */
    private static List<TabletSnapshot.DroneRow> droneRows(TabletSnapshot snapshot) {
        if (!snapshot.hasFeature(TabletFeature.DRONE) || snapshot.drones().isEmpty()) {
            return List.of();
        }
        List<TabletSnapshot.DroneRow> rows = snapshot.drones();
        for (TabletSnapshot.DroneRow row : rows) {
            if (DroneKind.fromWire(row.kindWire()) == null) {
                return rows.stream().filter(candidate -> DroneKind.fromWire(candidate.kindWire()) != null).toList();
            }
        }
        return rows;
    }

    /** In the air under control; a falling drone has lost control and is not counted. 受控飞行中；坠落中的无人机已失控，不计入。 */
    private static boolean isAirborne(TabletSnapshot.DroneRow row) {
        if (!row.placed()) {
            return false;
        }
        return switch (row.statusWire()) {
            case TabletSnapshot.DroneRow.STATUS_HOVERING, TabletSnapshot.DroneRow.STATUS_FLYING,
                 TabletSnapshot.DroneRow.STATUS_PILOTING -> true;
            default -> false;
        };
    }

    private static DroneAlert droneAlert(TabletSnapshot snapshot) {
        DroneAlert alert = DroneAlert.NONE;
        for (TabletSnapshot.DroneRow row : droneRows(snapshot)) {
            if (!row.placed()) {
                continue;
            }
            if (row.statusWire() == TabletSnapshot.DroneRow.STATUS_FALLING) {
                return DroneAlert.FALLING;
            }
            if (row.statusWire() == TabletSnapshot.DroneRow.STATUS_HOVERING
                    || row.statusWire() == TabletSnapshot.DroneRow.STATUS_FLYING) {
                alert = DroneAlert.UNATTENDED;
            }
        }
        return alert;
    }

    /**
     * Presentation-only: what a card's button offers. Placed drones on the ground or in the air can be linked; the
     * server re-validates (owner, state, the pilot's body on the ground).
     * 仅用于展示：卡片按钮提供的操作。已放置且在地面或空中的无人机可以连接；服务端会重新校验（归属、状态、驾驶者本体着地）。
     */
    private DroneAction droneAction(TabletSnapshot.DroneRow row, boolean linkBusy) {
        if (!row.placed() || row.statusWire() == TabletSnapshot.DroneRow.STATUS_ITEM) {
            return row.cooldownSeconds() > 0 ? DroneAction.COOLDOWN : DroneAction.PLACE_FIRST;
        }
        return switch (row.statusWire()) {
            case TabletSnapshot.DroneRow.STATUS_PILOTING -> DroneAction.LINKED;
            case TabletSnapshot.DroneRow.STATUS_FALLING -> DroneAction.LOST;
            default -> row.entityId() == pendingPilotEntityId ? DroneAction.CONNECTING
                    : linkBusy ? DroneAction.BUSY : DroneAction.CONNECT;
        };
    }

    /**
     * One pilot link at a time: while a drone is piloted or a connect request is pending, other cards cannot connect.
     * 同一时间只有一条驾驶链路：有无人机正在驾驶或连接请求未决时，其它卡片不能连接。
     */
    private boolean droneLinkBusy(List<TabletSnapshot.DroneRow> rows) {
        if (pendingPilotEntityId >= 0) {
            return true;
        }
        for (TabletSnapshot.DroneRow row : rows) {
            if (row.placed() && row.statusWire() == TabletSnapshot.DroneRow.STATUS_PILOTING) {
                return true;
            }
        }
        return false;
    }

    private List<DroneButtonKey> droneButtonKeys(TabletSnapshot snapshot) {
        List<TabletSnapshot.DroneRow> rows = droneRows(snapshot);
        if (rows.isEmpty()) {
            return List.of();
        }
        boolean linkBusy = droneLinkBusy(rows);
        ArrayList<DroneButtonKey> keys = new ArrayList<>(rows.size());
        for (TabletSnapshot.DroneRow row : rows) {
            keys.add(new DroneButtonKey(row.entityId(), row.kindWire(), droneAction(row, linkBusy)));
        }
        return keys;
    }

    private boolean pendingPilotStillConnectable(TabletSnapshot snapshot) {
        for (TabletSnapshot.DroneRow row : droneRows(snapshot)) {
            if (row.entityId() == pendingPilotEntityId) {
                return row.statusWire() == TabletSnapshot.DroneRow.STATUS_GROUNDED
                        || row.statusWire() == TabletSnapshot.DroneRow.STATUS_HOVERING
                        || row.statusWire() == TabletSnapshot.DroneRow.STATUS_FLYING;
            }
        }
        return false;
    }

    // ============================================================================================ types

    private enum DroneAction {
        CONNECT("drone.connect", ButtonStyle.PRIMARY, ButtonIcon.LINK),
        CONNECTING("drone.connecting", ButtonStyle.SECONDARY, ButtonIcon.SPINNER),
        LINKED("drone.linked", ButtonStyle.SECONDARY, ButtonIcon.LINK),
        LOST("drone.lost", ButtonStyle.DANGER_SOFT, ButtonIcon.NONE),
        BUSY("drone.busy", ButtonStyle.SECONDARY, ButtonIcon.NONE),
        COOLDOWN("drone.cooling", ButtonStyle.SECONDARY, ButtonIcon.CLOCK),
        PLACE_FIRST("drone.place_first", ButtonStyle.SECONDARY, ButtonIcon.NONE);

        private final String labelKey;
        private final ButtonStyle style;
        private final ButtonIcon icon;

        DroneAction(String labelKey, ButtonStyle style, ButtonIcon icon) {
            this.labelKey = labelKey;
            this.style = style;
            this.icon = icon;
        }

        String labelKey() {
            return labelKey;
        }

        ButtonStyle style() {
            return style;
        }

        ButtonIcon icon() {
            return icon;
        }
    }

    private enum DroneAlert {
        NONE,
        UNATTENDED,
        FALLING
    }

    /** Everything about a drone row that changes its button. 无人机行中会改变其按钮的全部信息。 */
    private record DroneButtonKey(int entityId, int kindWire, DroneAction action) {
    }
}
