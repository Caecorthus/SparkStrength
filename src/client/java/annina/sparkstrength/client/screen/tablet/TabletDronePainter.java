package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.BUTTON_ICON;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.BUTTON_ICON_GAP;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.CARD_R;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.MIN_NAME_W;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.pulseDot;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.visibleWidth;

/**
 * Stateless painters for the Bomber drone section: one card per drone of the viewer's own, and the pilot controls
 * cheat-sheet in the footer. Each card's connect button is a widget placed by TabletDronePage at
 * {@link #droneButtonRect}.
 * 炸弹客无人机分区的无状态绘制器：查看者自己的每架无人机一张卡片，以及页脚的驾驶操作提示。每张卡片的连接按钮是
 * TabletDronePage 按 droneButtonRect 放置的控件。
 */
final class TabletDronePainter {
    static final int DRONE_CARD_H = 50;
    static final int DRONE_ROW_STEP = 56;

    private static final int DRONE_TILE = 34;
    private static final int DRONE_TILE_INSET = 8;
    private static final int DRONE_GLYPH = 22;
    private static final int DRONE_TEXT_GAP = 10;
    private static final int DRONE_BADGE_R = 6;
    private static final int DRONE_CHIP_H = 12;
    private static final int DRONE_CHIP_PAD = 5;
    private static final int DRONE_CHIP_GLYPH = 7;
    private static final int DRONE_CHIP_GAP = 6;
    private static final int DRONE_BATTERY_W = 34;
    private static final int DRONE_BATTERY_H = 10;
    private static final int DRONE_META_GAP = 12;
    private static final int DRONE_META_GLYPH = 9;
    private static final int DRONE_BUTTON_MIN_W = 84;
    private static final int DRONE_BUTTON_H = 24;
    private static final int DRONE_BUTTON_INSET = 10;
    private static final int DRONE_BUTTON_TEXT_PAD = 24;
    // Battery tones: green from 50%, amber from 20%, red below. 电量色：50% 起绿色，20% 起琥珀，低于 20% 红色。
    private static final int DRONE_MID_PERCENT = 50;
    private static final int DRONE_LOW_PERCENT = 20;
    private static final long DRONE_CHARGE_PULSE_MS = 1600L;
    private static final long DRONE_LOW_BLINK_MS = 800L;

    // Controls cheat-sheet keycaps. 操作提示键帽。
    private static final int KEYCAP_H = 14;
    private static final int KEYCAP_PAD = 4;
    private static final int KEYCAP_KEY_GAP = 3;
    private static final int KEYCAP_ACTION_GAP = 5;
    private static final int KEYCAP_ITEM_GAP = 14;

    private TabletDronePainter() {
    }

    /**
     * What a drone card's status chip says, derived from the row alone (presentation only).
     * 无人机卡片状态胶囊显示的内容，仅由该行数据推出（纯展示）。
     */
    enum DroneChip { COOLDOWN, COOLDOWN_CHARGING, CHARGING, STOWED, STANDBY, HOVERING, FLYING, PILOTING, FALLING }

    /**
     * One drone card: kind tile (glyph, payload/bomb badge, glow while airborne), then "name [status]" over
     * "battery % · payload · distance"; the right side stays free for the connect widget ({@link #droneButtonRect}).
     * Meta items that do not fit are dropped from the right (distance first). Only the viewer's own drones ever reach
     * here (server-filtered).
     * 一张无人机卡片：型号图块（图形、挂载/炸弹角标、升空时的光晕），右侧上行“名称 [状态]”，下行“电量 % · 挂载 · 距离”；
     * 最右侧留给连接按钮控件（droneButtonRect）。放不下的信息从右往左依次省略（先省略距离）。这里只会收到查看者自己的
     * 无人机（服务端已过滤）。
     */
    static void droneCard(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.DroneRow row,
                          DroneKind kind, long now) {
        int h = DRONE_CARD_H;
        if (w <= 0) {
            return;
        }
        DroneChip chip = droneChip(row, kind);
        int tone = droneTone(kind);
        boolean airborne = chip == DroneChip.HOVERING || chip == DroneChip.FLYING || chip == DroneChip.PILOTING;
        boolean falling = chip == DroneChip.FALLING;
        int centerY = y + h / 2;
        // Opaque blend so the badge cut-out ring matches the card. 不透明混合，使角标描边与卡片底色一致。
        int fill = falling ? TabletTheme.mix(TabletTheme.SURFACE, TabletTheme.DANGER, 0.07f) : TabletTheme.SURFACE;
        c.roundRect(x, y, w, h, CARD_R, fill);
        if (airborne || falling) {
            c.roundRectOutline(x, y, w, h, CARD_R, 1f,
                    TabletTheme.withAlpha(falling ? TabletTheme.DANGER : TabletTheme.DRONE.base(), 0x4D));
        }

        int tileX = x + DRONE_TILE_INSET;
        int tileY = centerY - DRONE_TILE / 2;
        float tileCx = tileX + DRONE_TILE / 2f;
        float tileCy = tileY + DRONE_TILE / 2f;
        int glyphColor = falling ? TabletTheme.DANGER : tone;
        if (airborne) {
            // Radius stays inside the card's tile inset. 半径不超出卡片内边距。
            c.glow(tileCx, tileCy, DRONE_TILE * 0.7f, TabletTheme.withAlpha(tone, 0x2E));
        }
        c.roundRect(tileX, tileY, DRONE_TILE, DRONE_TILE, 9f, TabletTheme.withAlpha(glyphColor, row.placed() ? 0x2E : 0x1F));
        TabletIcons.drone(c, tileCx - DRONE_GLYPH / 2f, tileCy - DRONE_GLYPH / 2f, DRONE_GLYPH,
                row.placed() ? glyphColor : TabletTheme.multiplyAlpha(glyphColor, 0.7f));
        // Bottom-right badge: the bomb drone IS the bomb; a grenade drone shows it only while an M67 is bound.
        // 右下角标：炸弹无人机本身就是炸弹；投弹无人机仅在挂载 M67 时显示。
        boolean bombBadge = kind == DroneKind.BOMB;
        if (bombBadge || row.payload()) {
            float badgeCx = tileX + DRONE_TILE - 3f;
            float badgeCy = tileY + DRONE_TILE - 3f;
            c.circle(badgeCx, badgeCy, DRONE_BADGE_R + 1f, fill);
            c.circle(badgeCx, badgeCy, DRONE_BADGE_R, bombBadge ? TabletTheme.DANGER : TabletTheme.DRONE.base());
            float glyph = DRONE_BADGE_R * 1.4f;
            if (bombBadge) {
                TabletIcons.burst(c, badgeCx - glyph / 2f, badgeCy - glyph / 2f, glyph, TabletTheme.WHITE);
            } else {
                TabletIcons.grenade(c, badgeCx - glyph / 2f, badgeCy - glyph / 2f, glyph, TabletTheme.WHITE);
            }
        }

        int textX = tileX + DRONE_TILE + DRONE_TEXT_GAP;
        int contentRight = droneButtonRect(tr, x, y, w).x() - 10;
        int room = contentRight - textX;
        if (room <= 0) {
            return;
        }
        // Two lines centred as a block: name/chip (8) + gap 10 + battery line (10). 两行整体居中：名称行、间距、电量行。
        int nameY = centerY - 13;
        int metaCenterY = centerY + 9;

        String name = Text.translatable(KEY + "drone.kind." + kind.id()).getString();
        String chipText = droneChipText(row, chip);
        boolean chipGlyph = chip != DroneChip.STOWED;
        int chipGlyphSpace = chipGlyph ? DRONE_CHIP_GLYPH + 3 : 0;
        int chipMax = Math.max(0, room - Math.min(tr.getWidth(name), MIN_NAME_W) - DRONE_CHIP_GAP);
        String chipVisible = TabletCanvas.trim(tr, chipText, chipMax - 2 * DRONE_CHIP_PAD - chipGlyphSpace);
        int chipW = chipVisible.isEmpty() ? 0 : 2 * DRONE_CHIP_PAD + chipGlyphSpace + visibleWidth(tr, chipVisible);
        int nameW = c.text(tr, TabletCanvas.trim(tr, name, room - (chipW > 0 ? chipW + DRONE_CHIP_GAP : 0)), textX, nameY,
                TabletTheme.TEXT);
        if (chipW > 0) {
            int chipX = textX + nameW + DRONE_CHIP_GAP - 1;
            droneStatusChip(c, tr, chipX, nameY + 4 - DRONE_CHIP_H / 2, chipW, chip, chipVisible, chipGlyph, now);
        }

        // Battery meter + percent. 电量条与百分比。
        int percent = row.chargePercent();
        boolean charging = chip == DroneChip.CHARGING || chip == DroneChip.COOLDOWN_CHARGING;
        boolean lowBlink = airborne && percent < DRONE_LOW_PERCENT;
        int metaX = textX;
        if (contentRight - metaX < DRONE_BATTERY_W + 3) {
            return;
        }
        batteryMeter(c, metaX, metaCenterY - DRONE_BATTERY_H / 2, percent, charging, lowBlink, now);
        metaX += DRONE_BATTERY_W + 3 + 5;
        String percentText = Text.translatable(KEY + "drone.percent", percent).getString();
        if (contentRight - metaX < tr.getWidth(percentText)) {
            return;
        }
        metaX += c.text(tr, percentText, metaX, metaCenterY - 4,
                percent < DRONE_LOW_PERCENT ? TabletTheme.DANGER : TabletTheme.TEXT_2);

        // Payload: bound M67 for the grenade drone, "single use" for the bomb drone. 挂载：投弹无人机的 M67；炸弹无人机为“一次性”。
        String payloadText;
        int payloadInk;
        int payloadGlyphColor;
        if (kind == DroneKind.BOMB) {
            payloadText = Text.translatable(KEY + "drone.payload.bomb").getString();
            payloadInk = TabletTheme.TEXT_3;
            payloadGlyphColor = TabletTheme.multiplyAlpha(TabletTheme.DANGER, 0.85f);
        } else if (row.payload()) {
            payloadText = Text.translatable(KEY + "drone.payload.loaded").getString();
            payloadInk = TabletTheme.TEXT_2;
            payloadGlyphColor = TabletTheme.DRONE.pale();
        } else {
            payloadText = Text.translatable(KEY + "drone.payload.empty").getString();
            payloadInk = TabletTheme.TEXT_3;
            payloadGlyphColor = TabletTheme.TEXT_3;
        }
        metaX = metaItem(c, tr, metaX, contentRight, metaCenterY, payloadText, payloadInk, payloadGlyphColor,
                kind == DroneKind.BOMB ? MetaGlyph.BURST : MetaGlyph.GRENADE);
        if (metaX < 0 || !row.placed() || row.distance() < 0) {
            return;
        }
        metaItem(c, tr, metaX, contentRight, metaCenterY,
                Text.translatable(KEY + "drone.distance", row.distance()).getString(), TabletTheme.TEXT_3,
                TabletTheme.TEXT_3, MetaGlyph.LOCATE);
    }

    /**
     * Where the screen must place a drone card's connect TabletPressable (right-aligned, vertically centred). Its width
     * fits the widest label the button can ever show, identical on every row, so states never shift the layout.
     * 界面放置无人机卡片“连接”按钮的位置（右对齐、垂直居中）。宽度取按钮可能显示的最宽文字，每行相同，状态变化时布局不动。
     */
    static TabletLayout.Rect droneButtonRect(TextRenderer tr, int x, int y, int w) {
        int labelW = 0;
        for (String key : new String[]{"drone.connect", "drone.linked", "drone.connecting", "drone.cooling"}) {
            labelW = Math.max(labelW, tr.getWidth(Text.translatable(KEY + key)) + BUTTON_ICON + BUTTON_ICON_GAP);
        }
        for (String key : new String[]{"drone.lost", "drone.place_first", "drone.busy"}) {
            labelW = Math.max(labelW, tr.getWidth(Text.translatable(KEY + key)));
        }
        int bw = Math.max(DRONE_BUTTON_MIN_W, labelW + DRONE_BUTTON_TEXT_PAD);
        // Upper clamp never drops below the minimum, like suspectButtonRect. 上限不低于最小宽度，与 suspectButtonRect 相同。
        bw = Math.min(bw, Math.max(DRONE_BUTTON_MIN_W, w / 3));
        bw = Math.min(bw, Math.max(0, w - 2 * DRONE_BUTTON_INSET));
        return new TabletLayout.Rect(x + w - DRONE_BUTTON_INSET - bw, y + (DRONE_CARD_H - DRONE_BUTTON_H) / 2, bw,
                DRONE_BUTTON_H);
    }

    static DroneChip droneChip(TabletSnapshot.DroneRow row, DroneKind kind) {
        if (!row.placed() || row.statusWire() == TabletSnapshot.DroneRow.STATUS_ITEM) {
            // Only a carried grenade drone recharges, and it keeps recharging through the loss cooldown.
            // 只有携带中的投弹无人机会回充，损毁冷却期间也照常回充。
            boolean charging = kind == DroneKind.GRENADE && row.chargePercent() < 100;
            if (row.cooldownSeconds() > 0) {
                return charging ? DroneChip.COOLDOWN_CHARGING : DroneChip.COOLDOWN;
            }
            return charging ? DroneChip.CHARGING : DroneChip.STOWED;
        }
        return switch (row.statusWire()) {
            case TabletSnapshot.DroneRow.STATUS_HOVERING -> DroneChip.HOVERING;
            case TabletSnapshot.DroneRow.STATUS_FLYING -> DroneChip.FLYING;
            case TabletSnapshot.DroneRow.STATUS_PILOTING -> DroneChip.PILOTING;
            case TabletSnapshot.DroneRow.STATUS_FALLING -> DroneChip.FALLING;
            default -> DroneChip.STANDBY;
        };
    }

    /** Grenade drone in the drone accent, bomb drone in danger red. 投弹无人机用无人机强调色，炸弹无人机用危险红。 */
    static int droneTone(DroneKind kind) {
        return kind == DroneKind.BOMB ? TabletTheme.DANGER : TabletTheme.DRONE.base();
    }

    private static String droneChipText(TabletSnapshot.DroneRow row, DroneChip chip) {
        return switch (chip) {
            case COOLDOWN -> Text.translatable(KEY + "drone.status.cooldown", row.cooldownSeconds()).getString();
            case COOLDOWN_CHARGING ->
                    Text.translatable(KEY + "drone.status.cooldown_charging", row.cooldownSeconds()).getString();
            case CHARGING -> Text.translatable(KEY + "drone.status.charging").getString();
            case STOWED -> Text.translatable(KEY + "drone.status.stowed").getString();
            case STANDBY -> Text.translatable(KEY + "drone.status.standby").getString();
            case HOVERING -> Text.translatable(KEY + "drone.status.hovering").getString();
            case FLYING -> Text.translatable(KEY + "drone.status.flying").getString();
            case PILOTING -> Text.translatable(KEY + "drone.status.piloting").getString();
            case FALLING -> Text.translatable(KEY + "drone.status.falling").getString();
        };
    }

    /** Status chip (h=12): optional leading glyph/dot, then text. 状态胶囊（高 12）：可选前置图形/圆点，随后文字。 */
    private static void droneStatusChip(TabletCanvas c, TextRenderer tr, int x, int y, int w, DroneChip chip, String text,
                                        boolean glyph, long now) {
        int ink;
        int fill;
        switch (chip) {
            case CHARGING, PILOTING -> {
                ink = TabletTheme.SUCCESS;
                fill = TabletTheme.withAlpha(TabletTheme.SUCCESS, 0x2E);
            }
            case HOVERING, FLYING -> {
                ink = TabletTheme.DRONE.pale();
                fill = TabletTheme.withAlpha(TabletTheme.DRONE.base(), 0x33);
            }
            case FALLING -> {
                ink = TabletTheme.DANGER;
                fill = TabletTheme.withAlpha(TabletTheme.DANGER, 0x2E);
            }
            case STOWED -> {
                ink = TabletTheme.TEXT_3;
                fill = TabletTheme.FAINT;
            }
            default -> {
                ink = TabletTheme.TEXT_2;
                fill = TabletTheme.FAINT;
            }
        }
        c.roundRect(x, y, w, DRONE_CHIP_H, DRONE_CHIP_H / 2f, fill);
        int textX = x + DRONE_CHIP_PAD;
        if (glyph) {
            float gx = textX;
            float gy = y + (DRONE_CHIP_H - DRONE_CHIP_GLYPH) / 2f;
            float half = DRONE_CHIP_GLYPH / 2f;
            switch (chip) {
                case COOLDOWN, COOLDOWN_CHARGING -> TabletIcons.clock(c, gx, gy, DRONE_CHIP_GLYPH, ink);
                case CHARGING -> TabletIcons.bolt(c, gx, gy, DRONE_CHIP_GLYPH, ink);
                case PILOTING -> TabletIcons.link(c, gx, gy, DRONE_CHIP_GLYPH, ink);
                case HOVERING, FLYING -> pulseDot(c, gx + half, gy + half, 2.2f, TabletTheme.DRONE.base(), now);
                case FALLING -> c.circle(gx + half, gy + half, 2.2f, TabletTheme.DANGER);
                default -> c.circle(gx + half, gy + half, 2.2f, TabletTheme.TEXT_3);
            }
            textX += DRONE_CHIP_GLYPH + 3;
        }
        c.text(tr, text, textX, y + (DRONE_CHIP_H - 8) / 2, ink);
    }

    /**
     * Battery-shaped meter (body + nub, inner fill by level). Charging breathes; a low battery in the air blinks.
     * 电池形电量条（机身 + 正极凸起，内部按电量填充）。充电时呼吸闪烁；空中低电量时闪烁警示。
     */
    private static void batteryMeter(TabletCanvas c, int x, int y, int percent, boolean charging, boolean lowBlink,
                                     long now) {
        int w = DRONE_BATTERY_W;
        int h = DRONE_BATTERY_H;
        c.roundRectOutline(x, y, w, h, 2.5f, 1f, 0x40FFFFFF);
        c.roundRect(x + w + 1, y + 3, 2, h - 6, 0.8f, 0x40FFFFFF);
        int inner = w - 4;
        float filled = inner * Math.max(0, Math.min(100, percent)) / 100f;
        if (filled <= 0f) {
            return;
        }
        int color = percent >= DRONE_MID_PERCENT ? TabletTheme.SUCCESS
                : percent >= DRONE_LOW_PERCENT ? TabletTheme.WARNING : TabletTheme.DANGER;
        if (charging) {
            float phase = (float) (0.5 + 0.5 * Math.sin(2.0 * Math.PI * (now % DRONE_CHARGE_PULSE_MS) / DRONE_CHARGE_PULSE_MS));
            color = TabletTheme.multiplyAlpha(color, 0.6f + 0.4f * phase);
        } else if (lowBlink && (now % DRONE_LOW_BLINK_MS) >= DRONE_LOW_BLINK_MS / 2) {
            color = TabletTheme.multiplyAlpha(color, 0.4f);
        }
        c.roundRect(x + 2, y + 2, Math.max(1.5f, filled), h - 4, 1.2f, color);
    }

    private enum MetaGlyph { GRENADE, BURST, LOCATE }

    /**
     * One "· glyph text" meta item after {@code x}; returns the new x, or -1 when it did not fit (nothing drawn).
     * 在 x 之后绘制一个“· 图形 文字”信息项；返回新的 x，放不下时返回 -1（不绘制）。
     */
    private static int metaItem(TabletCanvas c, TextRenderer tr, int x, int right, int centerY, String text, int ink,
                                int glyphColor, MetaGlyph glyph) {
        int width = DRONE_META_GAP + DRONE_META_GLYPH + 3 + visibleWidth(tr, text);
        if (x + width > right) {
            return -1;
        }
        c.circle(x + DRONE_META_GAP / 2f, centerY, 1f, TabletTheme.TEXT_3);
        float gx = x + DRONE_META_GAP;
        float gy = centerY - DRONE_META_GLYPH / 2f;
        switch (glyph) {
            case GRENADE -> TabletIcons.grenade(c, gx, gy, DRONE_META_GLYPH, glyphColor);
            case BURST -> TabletIcons.burst(c, gx, gy, DRONE_META_GLYPH, glyphColor);
            case LOCATE -> TabletIcons.locate(c, gx, gy, DRONE_META_GLYPH, glyphColor);
        }
        int textX = x + DRONE_META_GAP + DRONE_META_GLYPH + 3;
        return textX + c.text(tr, text, textX, centerY - 4, ink);
    }

    /**
     * Pilot controls cheat-sheet centred in {@code area}: keycaps plus action labels. Key names follow the player's
     * bindings (the same KeyBindings the pilot client reads). When the keycaps do not fit, default bindings switch to
     * their short names (LMB, Shift...); if that still does not fit, one trimmed plain line.
     * 在 area 中居中绘制驾驶操作提示：键帽加动作说明。键名随玩家的按键绑定变化（与驾驶客户端读取的 KeyBinding 相同）。
     * 键帽放不下时，默认绑定改用简称（左键、Shift 等）；仍放不下时退化为一行截断文字。
     */
    static void droneControls(TabletCanvas c, TextRenderer tr, TabletLayout.Rect area, GameOptions options) {
        if (area.width() <= 0 || area.height() < 8) {
            return;
        }
        List<ControlItem> items = controlItems(options, false);
        int total = controlsWidth(tr, items);
        if (total > area.width()) {
            items = controlItems(options, true);
            total = controlsWidth(tr, items);
        }
        int centerY = area.centerY();
        int textY = centerY - 4;
        if (total > area.width() || area.height() < KEYCAP_H) {
            c.textCentered(tr, TabletCanvas.trim(tr, controlsLine(controlItems(options, true)), area.width()),
                    area.centerX(), textY, TabletTheme.TEXT_3);
            return;
        }
        int x = area.x() + (area.width() - total) / 2;
        int capY = centerY - KEYCAP_H / 2;
        for (int i = 0; i < items.size(); i++) {
            ControlItem item = items.get(i);
            if (i > 0) {
                x += KEYCAP_ITEM_GAP;
            }
            for (int k = 0; k < item.keys().size(); k++) {
                if (k > 0) {
                    x += KEYCAP_KEY_GAP;
                    x += c.text(tr, "/", x, textY, TabletTheme.TEXT_3) - 1 + KEYCAP_KEY_GAP;
                }
                String key = item.keys().get(k);
                int capW = visibleWidth(tr, key) + 2 * KEYCAP_PAD;
                // Keycap: raised face over a 1 px darker lip. 键帽：凸起键面下方 1 px 深色边。
                c.roundRect(x, capY + 1, capW, KEYCAP_H, 3f, 0x66000000);
                c.roundRect(x, capY, capW, KEYCAP_H, 3f, TabletTheme.SURFACE_HOVER);
                c.roundRectOutline(x, capY, capW, KEYCAP_H, 3f, 1f, TabletTheme.HAIRLINE);
                c.text(tr, key, x + KEYCAP_PAD, textY, TabletTheme.TEXT_2);
                x += capW;
            }
            x += KEYCAP_ACTION_GAP;
            x += c.text(tr, item.action(), x, textY, TabletTheme.TEXT_3) - 1;
        }
    }

    /** One cheat-sheet entry: keycaps (joined by "/") then the action label. 操作提示的一项：键帽（以“/”分隔）与动作说明。 */
    private record ControlItem(List<String> keys, String action) {
    }

    private static List<ControlItem> controlItems(GameOptions options, boolean compact) {
        return List.of(
                new ControlItem(moveKeys(options), Text.translatable(KEY + "drone.control.move").getString()),
                new ControlItem(List.of(keyName(options.jumpKey, "drone.key.up", compact),
                        keyName(options.sneakKey, "drone.key.down", compact)),
                        Text.translatable(KEY + "drone.control.lift").getString()),
                new ControlItem(List.of(keyName(options.attackKey, "drone.key.fire", compact)),
                        Text.translatable(KEY + "drone.control.fire").getString()),
                new ControlItem(List.of(keyName(options.useKey, "drone.key.exit", compact)),
                        Text.translatable(KEY + "drone.control.exit").getString()));
    }

    /**
     * Forward/left/back/right as one keycap when every name is a single character ("WASD", "ZQSD" on AZERTY, a rebind
     * such as "ESDF"), else four keycaps.
     * 前/左/后/右：每个键名都是单个字符时合为一个键帽（“WASD”、AZERTY 键盘上的“ZQSD”、改键后的“ESDF”等），否则为四个键帽。
     */
    private static List<String> moveKeys(GameOptions options) {
        KeyBinding[] bindings = {options.forwardKey, options.leftKey, options.backKey, options.rightKey};
        List<String> names = new ArrayList<>(bindings.length);
        StringBuilder joined = new StringBuilder();
        boolean single = true;
        for (KeyBinding binding : bindings) {
            String name = binding.getBoundKeyLocalizedText().getString();
            names.add(name);
            joined.append(name);
            single &= name.codePointCount(0, name.length()) == 1;
        }
        return single ? List.of(joined.toString()) : names;
    }

    /**
     * Compact form shows a default binding by its short name (e.g. "LMB" for Left Button); otherwise the bound key's
     * own localized name. 紧凑模式下默认绑定显示简称（如“左键”）；否则显示所绑定按键的本地化名称。
     */
    private static String keyName(KeyBinding binding, String shortKey, boolean compact) {
        return compact && binding.isDefault() ? Text.translatable(KEY + shortKey).getString()
                : binding.getBoundKeyLocalizedText().getString();
    }

    private static int controlsWidth(TextRenderer tr, List<ControlItem> items) {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            ControlItem item = items.get(i);
            if (i > 0) {
                total += KEYCAP_ITEM_GAP;
            }
            for (int k = 0; k < item.keys().size(); k++) {
                total += visibleWidth(tr, item.keys().get(k)) + 2 * KEYCAP_PAD
                        + (k > 0 ? 2 * KEYCAP_KEY_GAP + visibleWidth(tr, "/") : 0);
            }
            total += KEYCAP_ACTION_GAP + visibleWidth(tr, item.action());
        }
        return total;
    }

    private static String controlsLine(List<ControlItem> items) {
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            ControlItem item = items.get(i);
            if (i > 0) {
                plain.append(" · ");
            }
            plain.append(String.join("/", item.keys())).append(' ').append(item.action());
        }
        return plain.toString();
    }
}
