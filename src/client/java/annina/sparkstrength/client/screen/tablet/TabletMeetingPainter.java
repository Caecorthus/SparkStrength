package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletLayout;
import annina.sparkstrength.tablet.TabletRules;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.BUTTON_ICON;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.CARD_R;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.KEY;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.focusRing;
import static annina.sparkstrength.client.screen.tablet.TabletSectionPainter.visibleWidth;

/**
 * Stateless painters for the meeting section: the call-meeting hero card, the remaining-time bar, vote cards and the
 * footer status line. The call button and the vote cards are widgets placed by TabletMeetingPage.
 * 会议分区的无状态绘制器：召开会议英雄卡、剩余时间条、投票卡片与页脚状态行。召开按钮与投票卡片是 TabletMeetingPage 放置的控件。
 */
final class TabletMeetingPainter {
    static final int VOTE_MIN_W = 160;
    static final int VOTE_CARD_H = 32;
    static final int VOTE_ROW_STEP = 38;
    static final int ACTION_BUTTON_H = 26;
    static final int PROGRESS_BLOCK_H = 11;

    private static final int HERO_W = 360;
    private static final int HERO_H = 150;
    private static final int HERO_BUTTON_W = 132;
    private static final int HERO_BUTTON_H = 26;
    private static final int HERO_R = 12;
    private static final int HERO_DISC_R = 26;
    // Content stack inside the hero: disc 52, gap 12, status 8, gap 5, chances 8, gap 7, button 26.
    // 英雄卡内的内容堆叠：圆盘 52、间距 12、状态行 8、间距 5、次数行 8、间距 7、按钮 26。
    private static final int HERO_CONTENT_H = 118;
    private static final int HERO_STATUS_Y = 64;
    private static final int HERO_CHANCES_Y = 77;
    private static final int HERO_BUTTON_Y = 92;
    // Without the disc (short areas): status 8, gap 5, chances 8, gap 7, button 26.
    // 无圆盘的紧凑布局（区域过矮时）：状态行 8、间距 5、次数行 8、间距 7、按钮 26。
    private static final int HERO_COMPACT_H = 54;
    private static final int HERO_COMPACT_CHANCES_Y = 13;
    private static final int HERO_COMPACT_BUTTON_Y = 28;

    private static final int VOTE_AVATAR = 20;
    private static final int VOTE_TEXT_X = 32;
    private static final int BADGE_H = 16;
    private static final int BADGE_MIN_W = 20;

    private TabletMeetingPainter() {
    }

    enum HeroState { READY, COOLDOWN, DISABLED }

    /** Hero card centred in {@code area}: min(HERO_W, area.w) x min(HERO_H, area.h). */
    private static TabletLayout.Rect heroRect(TabletLayout.Rect area) {
        int w = Math.min(HERO_W, area.width());
        int h = Math.min(HERO_H, area.height());
        return new TabletLayout.Rect(area.x() + (area.width() - w) / 2, area.y() + (area.height() - h) / 2, w, h);
    }

    /**
     * Where the screen must place the call-meeting TabletPressable; kept in lock-step with {@link #meetingHero}.
     * 界面放置“召开会议”按钮的位置；与 meetingHero 的绘制布局严格一致。
     */
    static TabletLayout.Rect heroButtonRect(TabletLayout.Rect area) {
        TabletLayout.Rect hero = heroRect(area);
        boolean full = hero.height() >= HERO_CONTENT_H;
        int top = heroContentTop(hero, full);
        int w = Math.min(HERO_BUTTON_W, Math.max(0, hero.width() - 24));
        int y = top + (full ? HERO_BUTTON_Y : HERO_COMPACT_BUTTON_Y);
        y = Math.max(hero.y(), Math.min(y, hero.bottom() - HERO_BUTTON_H));
        return new TabletLayout.Rect(hero.x() + (hero.width() - w) / 2, y, w, HERO_BUTTON_H);
    }

    private static int heroContentTop(TabletLayout.Rect hero, boolean full) {
        int contentHeight = full ? HERO_CONTENT_H : HERO_COMPACT_H;
        return hero.y() + Math.max(0, (hero.height() - contentHeight) / 2);
    }

    /**
     * Inactive-meeting hero card (the call button itself is a widget painted via {@link TabletSectionPainter#button}).
     * 未开会时的英雄卡片（召开按钮本身是控件，通过 button 绘制）。
     */
    static void meetingHero(TabletCanvas c, TextRenderer tr, TabletLayout.Rect area, HeroState state,
                            int cooldownSeconds, int callsRemaining, long timeMs) {
        TabletLayout.Rect hero = heroRect(area);
        if (hero.width() <= 0 || hero.height() <= 0) {
            return;
        }
        boolean ready = state == HeroState.READY;
        c.shadow(hero.x(), hero.y() + 2, hero.width(), hero.height(), HERO_R, 10f, 0x55000000);
        c.roundRect(hero.x(), hero.y(), hero.width(), hero.height(), HERO_R, TabletTheme.SURFACE_RAISED);
        // Faint top sheen, tinted red while a meeting can be called. 顶部淡光泽，可召开时带红色调。
        int sheen = ready ? TabletTheme.withAlpha(TabletTheme.DANGER, 0x12) : 0x08FFFFFF;
        c.roundRectGradientV(hero.x(), hero.y(), hero.width(), hero.height(), HERO_R, sheen, sheen & 0x00FFFFFF);
        c.roundRectOutline(hero.x(), hero.y(), hero.width(), hero.height(), HERO_R, 1f, TabletTheme.HAIRLINE);

        boolean full = hero.height() >= HERO_CONTENT_H;
        int top = heroContentTop(hero, full);
        int centerX = hero.x() + hero.width() / 2;
        float cx = hero.x() + hero.width() / 2f;
        int textMax = Math.max(0, hero.width() - 24);

        if (full) {
            float cy = top + HERO_DISC_R;
            if (ready) {
                // Slow 2.4 s breathing glow; the only motion in the hero. 2.4 秒缓慢呼吸光晕，英雄卡唯一的动效。
                double phase = (timeMs % 2400L) / 2400.0 * Math.PI * 2.0;
                int glowAlpha = 0x1C + (int) Math.round(0x10 * (0.5 + 0.5 * Math.sin(phase)));
                c.glow(cx, cy, 40f, TabletTheme.withAlpha(TabletTheme.DANGER, glowAlpha));
                c.circle(cx, cy, HERO_DISC_R, TabletTheme.withAlpha(TabletTheme.DANGER, 0x2E));
                c.ring(cx, cy, HERO_DISC_R, 1f, TabletTheme.withAlpha(TabletTheme.DANGER, 0x40));
            } else {
                c.circle(cx, cy, HERO_DISC_R, TabletTheme.FAINT);
            }
            if (state == HeroState.COOLDOWN && cooldownSeconds > 0) {
                float total = Math.max(1f, TabletRules.MEETING_COOLDOWN_TICKS / 20f);
                float fraction = Math.max(0f, Math.min(1f, cooldownSeconds / total));
                float ringR = HERO_DISC_R + 5f;
                c.ring(cx, cy, ringR, 2f, TabletTheme.FAINT);
                c.arc(cx, cy, ringR, 2f, 0f, 360f * fraction, TabletTheme.TEXT_3);
            }
            int iconColor = switch (state) {
                case READY -> TabletTheme.DANGER;
                case COOLDOWN -> TabletTheme.TEXT_2;
                case DISABLED -> TabletTheme.TEXT_3;
            };
            TabletIcons.megaphone(c, cx - 13f, cy - 13f, 26f, iconColor);
        }

        Text statusText = switch (state) {
            case READY -> Text.translatable(KEY + "meeting.ready");
            case COOLDOWN -> Text.translatable(KEY + "meeting.cooldown", cooldownSeconds);
            case DISABLED -> Text.translatable(KEY + "meeting.disabled");
        };
        int statusColor = switch (state) {
            case READY -> TabletTheme.SUCCESS;
            case COOLDOWN -> TabletTheme.TEXT_2;
            case DISABLED -> TabletTheme.TEXT_3;
        };
        int statusY = top + (full ? HERO_STATUS_Y : 0);
        int chancesY = top + (full ? HERO_CHANCES_Y : HERO_COMPACT_CHANCES_Y);
        c.textCentered(tr, TabletCanvas.trim(tr, statusText.getString(), textMax), centerX, statusY, statusColor);
        String chances = Text.translatable(KEY + "meeting.chances", callsRemaining).getString();
        c.textCentered(tr, TabletCanvas.trim(tr, chances, textMax), centerX, chancesY, TabletTheme.TEXT_3);
    }

    /** 3 px remaining-time bar (track + DANGER fill) at the top of the vote list block. */
    static void meetingProgress(TabletCanvas c, int x, int y, int w, int remainingSeconds) {
        if (w <= 0) {
            return;
        }
        float total = Math.max(1f, TabletRules.MEETING_DURATION_TICKS / 20f);
        float fraction = Math.max(0f, Math.min(1f, remainingSeconds / total));
        c.roundRect(x, y, w, 3f, 1.5f, TabletTheme.FAINT);
        if (fraction > 0f) {
            c.roundRect(x, y, Math.max(3f, w * fraction), 3f, 1.5f, TabletTheme.DANGER);
        }
    }

    /**
     * Vote target card. {@code clickable} must already be participant && selectable && !confirmed (screen-side);
     * hover is only shown when clickable, unselectable targets are dimmed. {@code viewOnly} (spectator) draws the
     * names of selectable targets in TEXT_2 so the grid reads as non-interactive.
     * 投票目标卡片。clickable 须由界面预先算好（参与者 && 可选 && 未确认）；仅可点击时显示悬停，不可选目标变暗。
     * viewOnly（旁观者）时可选目标的名字改用 TEXT_2，使网格呈现为只读。
     */
    static void voteCard(TabletCanvas c, TextRenderer tr, TabletPressable card, TabletSnapshot.VoteTarget target,
                         boolean selected, boolean hovered, boolean clickable, boolean viewOnly, TabletTheme.Accent a) {
        int x = card.getX();
        int y = card.getY();
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean hover = hovered && clickable;
        boolean dim = !target.selectable();
        int base = hover ? TabletTheme.SURFACE_HOVER : TabletTheme.SURFACE;
        // Opaque blend (not a translucent overlay) so the avatar corner mask matches the card exactly.
        // 使用不透明混合色（而非半透明叠加），使头像圆角遮罩与卡片颜色完全一致。
        int fill = selected ? TabletTheme.mix(base, a.base() | 0xFF000000, 0x2E / 255f) : base;
        c.roundRect(x, y, w, h, CARD_R, fill);
        if (selected) {
            c.roundRectOutline(x, y, w, h, CARD_R, 1.5f, a.base());
        }
        focusRing(c, card, CARD_R, a);

        int centerY = y + h / 2;
        int textY = centerY - 4;
        int avatarX = x + 6;
        int avatarY = centerY - VOTE_AVATAR / 2;
        c.avatar(target.uuid(), target.name(), avatarX, avatarY, VOTE_AVATAR, 5f, fill);
        if (dim) {
            c.roundRect(avatarX, avatarY, VOTE_AVATAR, VOTE_AVATAR, 5f, TabletTheme.withAlpha(fill, 0x99));
        }
        if (selected) {
            // 12 px disc on the avatar's top-right, cut out of the card by a 1 px fill-coloured ring; kept clear of
            // the 1.5 px selection outline and of the name column.
            // 头像右上角 12 px 勾选圆点，外圈 1 px 卡片底色描边；不压住 1.5 px 选中描边，也不侵入名字列。
            float discX = avatarX + VOTE_AVATAR - 2f;
            float discY = avatarY + 2.5f;
            c.circle(discX, discY, 7f, fill);
            c.circle(discX, discY, 6f, a.base());
            TabletIcons.check(c, discX - 4.5f, discY - 4.5f, 9f, TabletTheme.WHITE);
        }

        // Vote-count badge on the right. 右侧票数徽章。
        String votes = String.valueOf(Math.max(0, target.votes()));
        int badgeW = Math.max(BADGE_MIN_W, visibleWidth(tr, votes) + 12);
        int badgeX = x + w - 8 - badgeW;
        int badgeY = centerY - BADGE_H / 2;
        boolean hasVotes = target.votes() > 0 && !dim;
        c.roundRect(badgeX, badgeY, badgeW, BADGE_H, BADGE_H / 2f,
                hasVotes ? TabletTheme.withAlpha(a.base(), 0x4D) : TabletTheme.FAINT);
        c.text(tr, votes, badgeX + (badgeW - visibleWidth(tr, votes) + 1) / 2, textY,
                hasVotes ? a.pale() : TabletTheme.TEXT_3);

        int nameX = x + VOTE_TEXT_X;
        String name = TabletCanvas.trim(tr, target.name(), badgeX - 6 - nameX);
        int nameColor = dim ? TabletTheme.TEXT_3 : viewOnly && !selected ? TabletTheme.TEXT_2 : TabletTheme.TEXT;
        c.text(tr, name, nameX, textY, nameColor);
    }

    /** Footer status line (TEXT_2), optionally led by a 12 px lock; trimmed to {@code maxWidth}. */
    static void meetingStatus(TabletCanvas c, TextRenderer tr, int x, int centerY, int maxWidth, Text status,
                              boolean locked) {
        if (maxWidth <= 0) {
            return;
        }
        int textX = x;
        if (locked && maxWidth >= BUTTON_ICON + 5 + 12) {
            TabletIcons.lock(c, x, centerY - BUTTON_ICON / 2f, BUTTON_ICON, TabletTheme.TEXT_2);
            textX += BUTTON_ICON + 5;
        }
        String text = TabletCanvas.trim(tr, status.getString(), maxWidth - (textX - x));
        c.text(tr, text, textX, centerY - 4, TabletTheme.TEXT_2);
    }
}
