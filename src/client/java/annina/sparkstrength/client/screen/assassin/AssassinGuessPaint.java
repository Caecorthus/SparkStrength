package annina.sparkstrength.client.screen.assassin;

import annina.sparkstrength.client.screen.assassin.AssassinGuessLayout.Rect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Palette and fill-drawn props of the guess screen: NoellesRoles' dark cinematic menu (black veil, blood-red
 * letterbox), pixel borders and the vanilla font, no GUI textures.
 * 猜测界面的配色与纯色块绘制道具：沿用 NoellesRoles 暗色电影感菜单（黑色遮罩、血红黑边），1 像素边框与原版字体，不用 GUI 贴图。
 */
final class AssassinGuessPaint {
    static final int VEIL = 0xE8000000;
    static final int BAR = 0xFF3C0606;
    static final int BAR_EDGE = 0xFF7A1414;
    static final int TITLE = 0xFFF2DCD2;
    static final int HINT = 0xFFB0928A;

    static final int PANEL = 0xF2120A0A;
    static final int PANEL_EDGE = 0xFF5A1C1C;
    static final int PANEL_SHADOW = 0x88000000;
    static final int RULE = 0xFF3A1A1A;
    static final int TEXT = 0xFFEADBD2;
    static final int TEXT_DIM = 0xFF8C7A74;
    static final int TEXT_FAINT = 0xFF5E504C;

    static final int ROW = 0xFF1C1212;
    static final int ROW_HOVER = 0xFF2A1A1A;
    static final int ROW_FOCUS = 0xFF3C1414;
    static final int ROW_FOCUS_EDGE = 0xFFB0302A;
    static final int FOCUS_ACCENT = 0xFFE0463C;

    static final int CELL = 0xFF1E1616;
    static final int CELL_HOVER = 0xFF2E2222;
    static final int CELL_HOVER_EDGE = 0xFF5E4642;
    static final int CELL_DISABLED_VEIL = 0x99120A0A;
    // How far a marked cell's fill leans toward the role colour. / 已标记单元格的底色向身份颜色靠近的比例。
    static final float MARK_TINT = 0.32F;

    static final int BUTTON = 0xFF261A1A;
    static final int BUTTON_HOVER = 0xFF382424;
    static final int BUTTON_EDGE = 0xFF5E4442;
    static final int BUTTON_EDGE_HOVER = 0xFFB89286;
    static final int GUESS = 0xFF8E1B1B;
    static final int GUESS_HOVER = 0xFFAE2424;
    static final int GUESS_EDGE = 0xFFE85A4E;
    static final int GUESS_TEXT = 0xFFFFF2EE;
    static final int DISABLED = 0xFF1E1818;
    static final int DISABLED_EDGE = 0xFF3E3232;
    static final int DISABLED_TEXT = 0xFF7E6C68;
    static final int BLOCKED_TEXT = 0xFFC2ACA4;

    static final int STAKE_FORGIVEN = 0xFFE6B74A;
    static final int STAKE_LETHAL = 0xFFFF5A4E;
    static final int TAG_PAD = 3;
    static final int TAG_ALPHA = 0x2C;
    static final int PIP_LEFT = 0xFFD8403A;
    static final int PIP_SPENT = 0xFF3E2C2C;
    static final int PIP_SIZE = 5;
    static final int PIP_GAP = 2;
    // More charges than this read as text only. / 次数超过此值时只显示文字。
    static final int MAX_PIPS = 8;

    static final int SCROLL_TRACK = 0x40FFFFFF;
    static final int SCROLL_THUMB = 0xFFB0302A;

    // WCAG AA for body text: the vanilla font is small and the panel near-black. / 正文级 WCAG AA：原版字体小、底色近黑。
    private static final double MIN_CONTRAST = 4.5D;
    private static final int CONTRAST_STEPS = 20;
    private static final String ELLIPSIS = "…";

    private AssassinGuessPaint() {
    }

    static void fill(DrawContext context, Rect rect, int color) {
        context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
    }

    static void border(DrawContext context, Rect rect, int color) {
        context.drawBorder(rect.x(), rect.y(), rect.width(), rect.height(), color);
    }

    /** A flat button with a 1 px edge and its label centred. / 平面按钮：1 像素边框，文字居中。 */
    static void button(DrawContext context, TextRenderer renderer, Rect rect, String label, int fill, int edge, int text) {
        fill(context, rect, fill);
        border(context, rect, edge);
        String visible = trim(renderer, label, rect.width() - 4);
        int textX = rect.x() + (rect.width() - renderer.getWidth(visible)) / 2;
        int textY = rect.y() + (rect.height() - renderer.fontHeight) / 2 + 1;
        context.drawText(renderer, visible, textX, textY, text, false);
    }

    /** Charge pips, filled for the guesses left; returns the width drawn. / 次数圆点，剩余的实心；返回绘制宽度。 */
    static int pips(DrawContext context, int x, int y, int left, int max) {
        if (max <= 0 || max > MAX_PIPS) {
            return 0;
        }
        for (int index = 0; index < max; index++) {
            int pipX = x + index * (PIP_SIZE + PIP_GAP);
            context.fill(pipX, y, pipX + PIP_SIZE, y + PIP_SIZE, index < left ? PIP_LEFT : PIP_SPENT);
        }
        return max * (PIP_SIZE + PIP_GAP) - PIP_GAP;
    }

    static String trim(TextRenderer renderer, String value, int width) {
        if (renderer.getWidth(value) <= width) {
            return value;
        }
        return renderer.trimToWidth(value, Math.max(0, width - renderer.getWidth(ELLIPSIS))) + ELLIPSIS;
    }

    static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    static int mix(int from, int to, float amount) {
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Role colour made readable on {@code background}: kept at WCAG contrast >= 4.5:1, otherwise mixed toward white
     * (toward black on a light background) in small steps, keeping the hue. Some roles are near-black or deep blue.
     * 让身份颜色在给定底色上可读：WCAG 对比度不低于 4.5:1 时原样保留，否则逐步向白色（浅色底则向黑色）混合并保持色相。
     * 部分身份颜色接近黑色或深蓝。
     */
    static int readableOn(int rgb, int background) {
        int base = opaque(rgb);
        if (contrast(base, background) >= MIN_CONTRAST) {
            return base;
        }
        int target = luminance(background) < 0.18D ? 0xFFFFFFFF : 0xFF000000;
        for (int step = 1; step < CONTRAST_STEPS; step++) {
            int candidate = mix(base, target, step / (float) CONTRAST_STEPS);
            if (contrast(candidate, background) >= MIN_CONTRAST) {
                return candidate;
            }
        }
        return target;
    }

    private static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05D) / (Math.min(a, b) + 0.05D);
    }

    private static double luminance(int color) {
        return 0.2126D * linear(color >> 16) + 0.7152D * linear(color >> 8) + 0.0722D * linear(color);
    }

    private static double linear(int channel) {
        double value = (channel & 0xFF) / 255.0D;
        return value <= 0.03928D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }
}
