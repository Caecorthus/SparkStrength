package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.role.attendant.DoorLogKind;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.CARD_R;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.MIN_NAME_W;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.trimStyled;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.visibleWidth;

/**
 * Stateless painters for the Attendant door log section: one card per entry, newest first.
 * 乘务员房门记录分区的无状态绘制器：每条记录一张卡片，最新在前。
 */
final class TabletDoorLogPainter {
    static final int DOOR_ROW_H = 26;
    static final int DOOR_ROW_STEP = 30;

    private static final int DOOR_TILE = 18;
    private static final int DOOR_TILE_INSET = 4;
    private static final int DOOR_ICON = 12;
    private static final int DOOR_AVATAR = 16;
    private static final int DOOR_COUNT_H = 12;
    private static final int DOOR_COUNT_PAD = 5;
    // Ages shown at most: "59 s" and two-digit minutes (a round lasts well under 100 minutes).
    // 最宽的时间文本：59 秒与两位数分钟（一回合远短于 100 分钟）。
    private static final int DOOR_AGE_MAX_SECONDS = 59;
    private static final int DOOR_AGE_MAX_MINUTES = 99;

    private TabletDoorLogPainter() {
    }

    /**
     * One door-log card: kind tile, then either "head name predicate" (KEY_OPENED) or the event sentence, then an
     * optional ×N badge and the age, right-aligned in a fixed {@code ageColumn} so badges line up as ages tick.
     * The door name is the sentence's only bright run. Anonymous actors get a "?" disc and "???", never skin or uuid data.
     * 一张房门记录卡片：类型图块，随后是“头像 名字 谓语”（KEY_OPENED）或事件句子，再是可选的 ×N 徽章与时间；
     * 时间在固定宽度 ageColumn 内右对齐，时间变化时徽章仍对齐。门名是句中唯一的高亮部分。
     * 匿名操作者显示“?”圆盘与“???”，绝不绘制皮肤或任何 uuid 数据。
     */
    static void doorLogRow(TabletCanvas c, TextRenderer tr, int x, int y, int w, TabletSnapshot.DoorLogRow row,
                           DoorLogKind kind, String age, int ageColumn) {
        int h = DOOR_ROW_H;
        if (w <= 0) {
            return;
        }
        int centerY = y + h / 2;
        int textY = centerY - 4;
        int tone = doorTone(kind);
        boolean neutral = kind == DoorLogKind.KEY_OPENED;
        // Opaque blend so the avatar corner mask matches the card. 不透明混合，使头像圆角遮罩与卡片一致。
        int fill = doorAlarm(kind) ? TabletTheme.mix(TabletTheme.SURFACE, tone, 0.07f) : TabletTheme.SURFACE;
        c.roundRect(x, y, w, h, CARD_R, fill);

        int tileX = x + DOOR_TILE_INSET;
        int tileY = centerY - DOOR_TILE / 2;
        c.roundRect(tileX, tileY, DOOR_TILE, DOOR_TILE, 5f, TabletTheme.withAlpha(tone, neutral ? 0x1F : 0x2E));
        float iconOffset = (DOOR_TILE - DOOR_ICON) / 2f;
        doorKindIcon(c, kind, tileX + iconOffset, tileY + iconOffset, DOOR_ICON, tone);

        int right = x + w - 8;
        int ageWidth = Math.min(ageColumn, Math.max(0, right - tileX - DOOR_TILE - 16));
        c.textRight(tr, TabletCanvas.trim(tr, age, ageWidth), right, textY, TabletTheme.TEXT_3);
        int contentRight = right - ageWidth - 8;
        int textX = tileX + DOOR_TILE + 8;
        if (row.count() > 1) {
            String count = Text.translatable(KEY + "door_log.count", row.count()).getString();
            int badgeW = visibleWidth(tr, count) + 2 * DOOR_COUNT_PAD;
            if (contentRight - badgeW - 6 - textX >= MIN_NAME_W) {
                int badgeX = contentRight - badgeW;
                c.roundRect(badgeX, centerY - DOOR_COUNT_H / 2, badgeW, DOOR_COUNT_H, DOOR_COUNT_H / 2f,
                        neutral ? TabletTheme.FAINT : TabletTheme.withAlpha(tone, 0x33));
                c.text(tr, count, badgeX + DOOR_COUNT_PAD, textY, neutral ? TabletTheme.TEXT_2 : tone);
                contentRight = badgeX - 6;
            }
        }
        if (contentRight - textX <= 0) {
            return;
        }

        Text sentence = Text.translatable(kind.translationKey(),
                Text.literal(row.doorName()).withColor(TabletTheme.TEXT & 0xFFFFFF));
        if (!kind.namesActor()) {
            c.text(tr, trimStyled(tr, sentence, contentRight - textX), textX, textY, TabletTheme.TEXT_2);
            return;
        }
        boolean anonymous = row.actorUuid() == null;
        if (contentRight - textX >= DOOR_AVATAR + 5 + MIN_NAME_W) {
            int avatarY = centerY - DOOR_AVATAR / 2;
            if (anonymous) {
                anonymousAvatar(c, tr, textX, avatarY, DOOR_AVATAR);
            } else {
                c.avatar(row.actorUuid(), row.actorName(), textX, avatarY, DOOR_AVATAR, 4f, fill);
            }
            textX += DOOR_AVATAR + 5;
        }
        String name = anonymous ? Text.translatable(KEY + "door_log.anonymous").getString() : row.actorName();
        int room = contentRight - textX;
        int nameWidth = tr.getWidth(name);
        int sentenceWidth = tr.getWidth(sentence);
        // The name keeps its full width when everything fits; otherwise it yields down to MIN_NAME_W first.
        // 全部放得下时名字保持完整；否则名字先让出空间，最少保留 MIN_NAME_W。
        int nameRoom = nameWidth + 3 + sentenceWidth <= room ? nameWidth
                : Math.min(nameWidth, Math.max(MIN_NAME_W, room - 3 - sentenceWidth));
        textX += c.text(tr, TabletCanvas.trim(tr, name, Math.min(nameRoom, room)), textX, textY,
                anonymous ? TabletTheme.TEXT_3 : TabletTheme.TEXT) + 3;
        if (contentRight - textX > 0) {
            c.text(tr, trimStyled(tr, sentence, contentRight - textX), textX, textY, TabletTheme.TEXT_2);
        }
    }

    /** Widest age label, so every row reserves the same age column. 最宽的时间文本宽度，所有行预留同宽的时间列。 */
    static int doorAgeColumn(TextRenderer tr) {
        // Advance widths (not inked): TabletCanvas.trim compares advances. 使用字宽（非着墨宽度），与 trim 的比较一致。
        return Math.max(tr.getWidth(doorAge(0)),
                Math.max(tr.getWidth(doorAge(DOOR_AGE_MAX_SECONDS)), tr.getWidth(doorAge(DOOR_AGE_MAX_MINUTES * 60))));
    }

    /** "just now" under 5 s, then seconds under a minute, then whole minutes. 5 秒内为“刚刚”，1 分钟内按秒，其后按分钟。 */
    static String doorAge(int seconds) {
        int age = Math.max(0, seconds);
        if (age < 5) {
            return Text.translatable(KEY + "door_log.age.now").getString();
        }
        if (age < 60) {
            return Text.translatable(KEY + "door_log.age.seconds", age).getString();
        }
        return Text.translatable(KEY + "door_log.age.minutes", age / 60).getString();
    }

    /** Severity tone: break-ins red, jams amber, fixes green, key opens neutral. 严重度色：闯入红、堵门琥珀、修复绿、钥匙开门中性。 */
    static int doorTone(DoorLogKind kind) {
        return switch (kind) {
            case KEY_OPENED -> TabletTheme.TEXT_2;
            case PICKED, BROKEN, FORCED -> TabletTheme.DANGER;
            case JAMMED -> TabletTheme.WARNING;
            case REPAIRED, UNJAMMED -> TabletTheme.SUCCESS;
        };
    }

    private static boolean doorAlarm(DoorLogKind kind) {
        return kind == DoorLogKind.PICKED || kind == DoorLogKind.BROKEN || kind == DoorLogKind.FORCED;
    }

    static void doorKindIcon(TabletCanvas c, DoorLogKind kind, float x, float y, float size, int color) {
        switch (kind) {
            case KEY_OPENED -> TabletIcons.key(c, x, y, size, color);
            case PICKED -> TabletIcons.lockpick(c, x, y, size, color);
            case BROKEN -> TabletIcons.burst(c, x, y, size, color);
            case REPAIRED -> TabletIcons.wrench(c, x, y, size, color);
            case JAMMED -> TabletIcons.lock(c, x, y, size, color);
            case UNJAMMED -> TabletIcons.unlock(c, x, y, size, color);
            case FORCED -> TabletIcons.doorOpen(c, x, y, size, color);
        }
    }

    /** Stand-in for a hidden actor: "?" on a disc, like anonymous chat rows. 隐藏操作者的替代头像：圆盘上的“?”，与匿名聊天一致。 */
    private static void anonymousAvatar(TabletCanvas c, TextRenderer tr, int x, int y, int size) {
        c.circle(x + size / 2f, y + size / 2f, size / 2f, TabletTheme.ANONYMOUS_DISC);
        int glyphWidth = visibleWidth(tr, "?");
        c.text(tr, "?", x + (size - glyphWidth + 1) / 2, y + (size - 7) / 2, TabletTheme.TEXT_2);
    }
}
