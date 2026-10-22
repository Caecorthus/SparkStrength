package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonIcon;
import annina.sparkstrength.client.screen.tablet.TabletSectionPainter.ButtonStyle;
import annina.sparkstrength.component.undercover.UndercoverPlayerComponent;
import annina.sparkstrength.network.sabotage.TriggerSabotageC2SPacket;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.undercover.UndercoverRoles;
import annina.sparkstrength.sabotage.SabotageRowState;
import annina.sparkstrength.tablet.TabletFeature;
import annina.sparkstrength.tablet.TabletLayout.Rect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;

/**
 * Sabotage section: one card per sabotage type with its trigger button, then the team log (cards and log are painted by
 * {@link TabletSabotagePainter}).
 * 破坏分区：每种破坏一张带触发按钮的卡片，下方为团队日志（卡片与日志由 TabletSabotagePainter 绘制）。
 */
final class TabletSabotagePage implements TabletPage {
    private final TabletScreen screen;
    private List<Integer> lastSabotageButtons = List.of();
    private int pendingTriggerTicks;

    TabletSabotagePage(TabletScreen screen) {
        this.screen = screen;
    }

    @Override
    public void prepare(TabletSnapshot snapshot) {
        lastSabotageButtons = sabotageButtonKeys(snapshot);
    }

    @Override
    public boolean needsRebuild(TabletSnapshot snapshot) {
        return !sabotageButtonKeys(snapshot).equals(lastSabotageButtons);
    }

    @Override
    public void tick(boolean open) {
        if (pendingTriggerTicks > 0 && --pendingTriggerTicks == 0 && open) {
            screen.refresh();
        }
    }

    /**
     * One trigger button per sabotage card; only a READY card's button is active. The server re-validates every
     * trigger, and a short pending window stops a double click from sending twice.
     * 每张破坏卡片一个触发按钮；只有可触发的卡片按钮可用。服务端会重新校验每次触发，短暂的等待期防止双击重复发送。
     */
    @Override
    public void init(TabletSnapshot snapshot) {
        TextRenderer textRenderer = screen.textRenderer();
        List<TabletSnapshot.SabotageRow> rows = snapshot.hasFeature(TabletFeature.SABOTAGE) ? snapshot.sabotage() : List.of();
        Rect area = screen.layout().bodyNoFooter();
        for (int index = 0; index < rows.size(); index++) {
            int y = area.y() + index * TabletSabotagePainter.CARD_STEP;
            if (y + TabletSabotagePainter.CARD_H > area.bottom()) {
                break;
            }
            TabletSnapshot.SabotageRow row = rows.get(index);
            SabotageRowState state = TabletSabotagePainter.state(row);
            // A stage-1 Undercover watches the team's cards for cover; its own trigger needs the CQCM mask.
            // 第一阶段卧底为了伪装能看团队卡片；它自己的触发需要 CQCM 面具。
            boolean needsMask = state == SabotageRowState.READY && needsMaskToTrigger();
            boolean ready = state == SabotageRowState.READY && !needsMask && pendingTriggerTicks == 0;
            String labelKey = needsMask ? "button.needs_mask" : switch (state) {
                case READY -> "button.trigger";
                case LOCKED -> "button.locked";
                case ARMING -> "button.arming";
                case COOLDOWN -> "button.cooldown";
                case ACTIVE -> "button.active";
                case BLOCKED -> "button.blocked";
            };
            ButtonStyle style = needsMask ? ButtonStyle.LOCKED : switch (state) {
                case READY -> ButtonStyle.DANGER;
                case ACTIVE -> ButtonStyle.DANGER_SOFT;
                default -> ButtonStyle.LOCKED;
            };
            ButtonIcon icon = needsMask ? ButtonIcon.LOCK : switch (state) {
                case LOCKED -> ButtonIcon.LOCK;
                case ARMING, COOLDOWN -> ButtonIcon.CLOCK;
                case ACTIVE -> ButtonIcon.SPINNER;
                default -> ButtonIcon.NONE;
            };
            Rect rect = TabletSabotagePainter.buttonRect(textRenderer, area.x(), y, area.width());
            screen.addButton("sabotage:" + row.typeWire(), rect, Text.translatable(KEY + "sabotage." + labelKey), style,
                    icon, TabletTheme.SABOTAGE, () -> triggerSabotage(row))
                    .active = ready;
        }
    }

    private static boolean needsMaskToTrigger() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && UndercoverRoles.isUndercover(player)
                && !UndercoverPlayerComponent.KEY.get(player).isStage2();
    }

    private void triggerSabotage(TabletSnapshot.SabotageRow row) {
        if (pendingTriggerTicks > 0 || !ClientPlayNetworking.canSend(TriggerSabotageC2SPacket.ID)) {
            return;
        }
        ClientPlayNetworking.send(new TriggerSabotageC2SPacket(row.typeWire()));
        pendingTriggerTicks = 20;
        screen.refresh();
    }

    /**
     * A sabotage on (lights out, engine damaged or halted) as a red live chip, ready as a pulsing yellow one, else the
     * cooldown or nothing.
     * 破坏进行中（熄灯、动力受损或停驶）为红色实时胶囊，可触发为黄色脉冲胶囊，否则为冷却或不显示。
     */
    @Override
    public int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now) {
        int since = TabletClientState.secondsSinceSnapshot();
        for (TabletSnapshot.SabotageRow row : snapshot.sabotage()) {
            SabotageRowState state = TabletSabotagePainter.state(row);
            if (state == SabotageRowState.ACTIVE) {
                return screen.paintPill(right, centerY, maxWidth, TabletSabotagePainter.activeChip(row, since),
                        TabletTheme.KILLER.pale(), TabletTheme.withAlpha(TabletTheme.DANGER, 0x33), 0,
                        (canvas, x, y, size, color) -> screen.paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                        TabletTheme.DANGER);
            }
        }
        for (TabletSnapshot.SabotageRow row : snapshot.sabotage()) {
            SabotageRowState state = TabletSabotagePainter.state(row);
            if (state == SabotageRowState.READY) {
                return screen.paintPill(right, centerY, maxWidth, Text.translatable(KEY + "sabotage.chip.ready").getString(),
                        TabletTheme.SABOTAGE.pale(), TabletTheme.withAlpha(TabletTheme.SABOTAGE.base(), 0x2E), 0,
                        (canvas, x, y, size, color) -> screen.paintPulseDot(x + size / 2f, y + size / 2f, 3f, color, now),
                        TabletTheme.SABOTAGE.base());
            }
            if (state == SabotageRowState.COOLDOWN || state == SabotageRowState.ARMING) {
                return screen.paintPill(right, centerY, maxWidth,
                        TabletSabotagePainter.clock(TabletSabotagePainter.liveSeconds(row, since)),
                        TabletTheme.TEXT_2, TabletTheme.SURFACE, 0, TabletIcons::clock, TabletTheme.TEXT_2);
            }
        }
        return right;
    }

    @Override
    public void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
        // Ready to trigger pulses; an outage in progress is a red dot. 可触发时脉冲；熄灯进行中为红点。
        SabotageRowState alert = sabotageAlert(snapshot);
        if (alert == SabotageRowState.READY) {
            screen.paintBadgePulse(icon, TabletTheme.SABOTAGE.base(), now);
        } else if (alert == SabotageRowState.ACTIVE) {
            screen.paintBadgeDot(icon, TabletTheme.DANGER);
        }
    }

    /** Cards on top, then the newest log lines that still fit. / 上方为卡片，下方为放得下的最新日志。 */
    @Override
    public void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now) {
        TabletCanvas c = screen.canvas();
        TextRenderer textRenderer = screen.textRenderer();
        Rect area = screen.layout().bodyNoFooter();
        List<TabletSnapshot.SabotageRow> rows = snapshot.sabotage();
        int since = TabletClientState.secondsSinceSnapshot();
        int y = area.y();
        for (TabletSnapshot.SabotageRow row : rows) {
            if (y + TabletSabotagePainter.CARD_H > area.bottom()) {
                break;
            }
            TabletSabotagePainter.card(c, textRenderer, area.x(), y, area.width(), row, since, now);
            y += TabletSabotagePainter.CARD_STEP;
        }
        if (y + TabletSabotagePainter.LOG_HEADER_H + TabletSabotagePainter.LOG_ROW_H > area.bottom()) {
            return;
        }
        TabletSabotagePainter.logHeader(c, textRenderer, area.x(), y, area.width());
        y += TabletSabotagePainter.LOG_HEADER_H;
        List<TabletSnapshot.SabotageLogRow> log = snapshot.sabotageLog();
        if (log.isEmpty()) {
            c.text(textRenderer, TabletCanvas.trim(textRenderer,
                            Text.translatable(KEY + "sabotage.log.empty").getString(), area.width() - 4),
                    area.x() + 2, y + 5, TabletTheme.TEXT_3);
            return;
        }
        for (TabletSnapshot.SabotageLogRow entry : log) {
            if (y + TabletSabotagePainter.LOG_ROW_H > area.bottom()) {
                break;
            }
            TabletSabotagePainter.logRow(c, textRenderer, area.x(), y, area.width(), entry);
            y += TabletSabotagePainter.LOG_ROW_H;
        }
    }

    private static List<Integer> sabotageButtonKeys(TabletSnapshot snapshot) {
        if (!snapshot.hasFeature(TabletFeature.SABOTAGE)) {
            return List.of();
        }
        ArrayList<Integer> keys = new ArrayList<>();
        for (TabletSnapshot.SabotageRow row : snapshot.sabotage()) {
            keys.add(row.typeWire() * 16 + row.stateWire());
        }
        return keys;
    }

    /** The most urgent sabotage state for the rail badge: ACTIVE, then READY. / 侧栏徽标用的最紧急状态：先进行中，再可触发。 */
    private static @Nullable SabotageRowState sabotageAlert(TabletSnapshot snapshot) {
        if (!snapshot.hasFeature(TabletFeature.SABOTAGE)) {
            return null;
        }
        SabotageRowState alert = null;
        for (TabletSnapshot.SabotageRow row : snapshot.sabotage()) {
            SabotageRowState state = TabletSabotagePainter.state(row);
            if (state == SabotageRowState.ACTIVE) {
                return state;
            }
            if (state == SabotageRowState.READY) {
                alert = state;
            }
        }
        return alert;
    }
}
