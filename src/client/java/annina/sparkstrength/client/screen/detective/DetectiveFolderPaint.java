package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.client.ui.common.PlayerHeadTextureHelper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Fill-drawn paper props shared by the case folder screen (no GUI textures).
 * 文件夹界面共用的纯色块绘制道具（不使用 GUI 贴图）。
 */
final class DetectiveFolderPaint {
    static final int INK = 0xFF2E2A25;
    static final int INK_RED = 0xFFA0322A;
    static final int INK_NAVY = 0xFF25476F;
    static final int INK_MUTED = 0xFF7C8A99;
    static final int INK_FADED = 0xFFA6B8CB;
    static final int STICKY = 0xFFF7E27A;
    static final int STICKY_FOLD = 0xFFE6CC55;
    static final int STICKY_INK = 0xFF4A3B12;
    static final int TAPE = 0x99F4F0E2;
    private static final int POLAROID_FRAME = 0xFFFBFBF8;
    private static final int POLAROID_EDGE = 0xFFCFCABF;
    private static final int POLAROID_PHOTO = 0xFF2F2F2F;
    private static final int UNKNOWN_FACE = 0xFF3A3A3A;
    private static final int UNKNOWN_FACE_EDGE = 0xFF6A6A6A;
    private static final int PENCIL_BODY = 0xFFE8B83A;
    private static final int PENCIL_ERASER = 0xFFE58C9A;
    private static final int PENCIL_TIP = 0xFF3A2E24;
    private static final int SILHOUETTE_BACKDROP = 0xFF4B4B4B;
    private static final int INK_LIGHT = 0xFFF2F2F2;
    private static final int INK_DARK = 0xFF1C1C1C;
    private static final int STICKY_PADDING_X = 7;
    private static final int STICKY_MIN_WIDTH = 56;
    private static final int STICKY_TEXT_TOP = 5;
    private static final int STICKY_TAPE_RISE = 3;
    private static final double MIN_CONTRAST = 3.0D;
    private static final int CONTRAST_STEPS = 20;
    // Fixed uuid so an unknown victim always gets the same default skin. / 固定 UUID，未知受害人始终使用同一默认皮肤。
    private static final UUID UNKNOWN_SKIN_UUID = new UUID(0L, 0L);

    private DetectiveFolderPaint() {
    }

    /**
     * Head from the synced display uuid only. The stable helper never reads live entity skins, so a disguise shown
     * at filing time is not overwritten by what the entity looks like now.
     * 只使用同步来的显示 UUID 绘制头像；稳定皮肤工具不读取实体实时皮肤，记录时看到的伪装不会被当前外观覆盖。
     */
    static void drawHead(DrawContext context, @Nullable UUID displayUuid, int x, int y, int size) {
        if (displayUuid == null) {
            PlayerSkinDrawer.draw(context, DefaultSkinHelper.getSkinTextures(UNKNOWN_SKIN_UUID), x, y, size);
            return;
        }
        PlayerSkinDrawer.draw(context, PlayerHeadTextureHelper.resolveStableSkinTextures(displayUuid, null), x, y, size);
    }

    /** "?" box used instead of a head for anonymous suspects. / 匿名嫌疑人用“?”方块代替头像。 */
    static void drawUnknownFace(DrawContext context, TextRenderer renderer, int x, int y, int size) {
        context.fill(x, y, x + size, y + size, UNKNOWN_FACE);
        context.drawBorder(x, y, size, size, UNKNOWN_FACE_EDGE);
        int textX = x + (size - renderer.getWidth("?")) / 2 + 1;
        int textY = y + (size - renderer.fontHeight) / 2 + 1;
        context.drawText(renderer, "?", textX, textY, 0xFFE0E0E0, false);
    }

    /** Polaroid frame with a tape strip; the caller rotates the matrix. / 带胶带的拍立得相框，旋转由调用方处理。 */
    static void drawPolaroid(DrawContext context, int x, int y, int width, int height) {
        context.fill(x + 1, y + 1, x + width + 1, y + height + 1, 0x55000000);
        context.fill(x, y, x + width, y + height, POLAROID_FRAME);
        context.drawBorder(x, y, width, height, POLAROID_EDGE);
        int tapeWidth = Math.max(8, width / 2);
        int tapeX = x + (width - tapeWidth) / 2;
        context.fill(tapeX, y - 3, tapeX + tapeWidth, y + 4, TAPE);
    }

    static void drawPhotoBackground(DrawContext context, int x, int y, int size) {
        context.fill(x, y, x + size, y + size, POLAROID_PHOTO);
    }

    /**
     * Silhouette with a "?" for an unnamed killer, tinted with the guessed role colour. The "?" sits on the shoulders
     * and takes whichever of the light/dark inks contrasts more with that colour.
     * 未指认凶手时的剪影与问号，按推测身份着色；问号位于肩部，在浅色/深色墨水中选对比度更高的一种。
     */
    static void drawSilhouette(DrawContext context, TextRenderer renderer, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, SILHOUETTE_BACKDROP);
        int headSize = Math.max(4, width / 3);
        int headX = x + (width - headSize) / 2;
        int headY = y + Math.max(1, height / 6);
        context.fill(headX, headY, headX + headSize, headY + headSize, color);
        int shoulderY = headY + headSize + 1;
        context.fill(x + 2, shoulderY, x + width - 2, y + height, color);
        int ink = contrast(INK_LIGHT, color) >= contrast(INK_DARK, color) ? INK_LIGHT : INK_DARK;
        context.drawText(renderer, "?", x + 3, y + height - renderer.fontHeight, ink, false);
    }

    /**
     * Yellow sticky note with tape, wrapped text and a folded bottom edge. Returns the note height. Explicit
     * {@code \n} in the text always starts a new line (vanilla line wrapping honours it).
     * 带胶带、自动换行文字与底部折痕的黄色便利贴，返回便利贴高度。文本中的 \n 总会换行（原版换行逻辑支持）。
     */
    static int drawStickyNote(DrawContext context, TextRenderer renderer, Text text, int x, int y, int width, int maxLines) {
        List<OrderedText> lines = renderer.wrapLines(text, stickyInnerWidth(width));
        int lineCount = Math.max(1, Math.min(maxLines, lines.size()));
        int height = stickyHeight(renderer, lineCount);
        context.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x55000000);
        context.fill(x, y, x + width, y + height, STICKY);
        context.fill(x, y + height - 3, x + width, y + height, STICKY_FOLD);
        int tapeWidth = Math.min(34, width / 3);
        int tapeX = x + (width - tapeWidth) / 2;
        context.fill(tapeX, y - STICKY_TAPE_RISE, tapeX + tapeWidth, y + 4, TAPE);
        for (int index = 0; index < Math.min(lineCount, lines.size()); index++) {
            OrderedText line = lines.get(index);
            int lineX = x + (width - renderer.getWidth(line)) / 2;
            context.drawText(renderer, line, lineX, y + STICKY_TEXT_TOP + index * (renderer.fontHeight + 1), STICKY_INK, false);
        }
        return height;
    }

    static int stickyNoteHeight(TextRenderer renderer, Text text, int width, int maxLines) {
        int lineCount = Math.max(1, Math.min(maxLines, renderer.wrapLines(text, stickyInnerWidth(width)).size()));
        return stickyHeight(renderer, lineCount);
    }

    /**
     * Narrowest note width (at most {@code maxWidth}) that keeps the line count the text gets at full width, so the
     * lines come out balanced instead of one long line plus a stray word.
     * 在不增加行数的前提下（相对最大宽度时的行数）求最窄的便利贴宽度，使各行长度均衡，避免长行后跟一个孤零零的词。
     */
    static int stickyNoteWidth(TextRenderer renderer, Text text, int maxWidth, int maxLines) {
        int maxInner = stickyInnerWidth(maxWidth);
        int lines = Math.min(maxLines, renderer.wrapLines(text, maxInner).size());
        int low = 8;
        int high = maxInner;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (renderer.wrapLines(text, middle).size() <= lines) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        int widest = 0;
        for (OrderedText line : renderer.wrapLines(text, low)) {
            widest = Math.max(widest, renderer.getWidth(line));
        }
        return Math.max(Math.min(maxWidth, STICKY_MIN_WIDTH), Math.min(maxWidth, widest + STICKY_PADDING_X * 2));
    }

    private static int stickyInnerWidth(int width) {
        return Math.max(8, width - STICKY_PADDING_X * 2);
    }

    private static int stickyHeight(TextRenderer renderer, int lineCount) {
        return STICKY_TEXT_TOP + lineCount * (renderer.fontHeight + 1) + 3;
    }

    /** Small diagonal pencil, 8x8. / 8x8 的斜向小铅笔。 */
    static void drawPencil(DrawContext context, int x, int y) {
        for (int step = 0; step < 5; step++) {
            int px = x + 2 + step;
            int py = y + 5 - step;
            context.fill(px, py, px + 2, py + 2, PENCIL_BODY);
        }
        context.fill(x + 7, y, x + 8, y + 2, PENCIL_ERASER);
        context.fill(x + 6, y, x + 8, y + 1, PENCIL_ERASER);
        context.fill(x + 1, y + 6, x + 2, y + 7, PENCIL_TIP);
        context.fill(x, y + 7, x + 1, y + 8, PENCIL_TIP);
    }

    /** 4x7 triangle pointing left or right. / 指向左或右的 4x7 三角形。 */
    static void drawArrow(DrawContext context, int x, int y, boolean pointingLeft, int color) {
        for (int column = 0; column < 4; column++) {
            int columnX = pointingLeft ? x + column : x + 3 - column;
            context.fill(columnX, y + 3 - column, columnX + 1, y + 4 + column, color);
        }
    }

    static void drawDashedBorder(DrawContext context, int x, int y, int width, int height, int color) {
        int right = x + width;
        int bottom = y + height;
        for (int dashX = x; dashX < right; dashX += 4) {
            int end = Math.min(dashX + 2, right);
            context.fill(dashX, y, end, y + 1, color);
            context.fill(dashX, bottom - 1, end, bottom, color);
        }
        for (int dashY = y; dashY < bottom; dashY += 4) {
            int end = Math.min(dashY + 2, bottom);
            context.fill(x, dashY, x + 1, end, color);
            context.fill(right - 1, dashY, right, end, color);
        }
    }

    /** Runs {@code draw} rotated around (centerX, centerY). / 以 (centerX, centerY) 为中心旋转绘制。 */
    static void rotated(DrawContext context, float centerX, float centerY, float degrees, Runnable draw) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX, centerY, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        matrices.translate(-centerX, -centerY, 0.0F);
        draw.run();
        matrices.pop();
    }

    static String trim(TextRenderer renderer, String value, int width) {
        if (renderer.getWidth(value) <= width) {
            return value;
        }
        String ellipsis = "…";
        return renderer.trimToWidth(value, Math.max(0, width - renderer.getWidth(ellipsis))) + ellipsis;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    /**
     * Role colour made readable on {@code background}: kept as-is at WCAG contrast >= 3:1, otherwise mixed toward
     * white (toward black on a light background) in small steps, keeping the hue, until it reaches 3:1.
     * 让身份颜色在给定底色上可读：WCAG 对比度不低于 3:1 时原样返回，否则逐步向白色混合（浅色底则向黑色），保持色相直到达到 3:1。
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

    /** WCAG contrast ratio of two opaque colours (1..21). / 两种不透明颜色的 WCAG 对比度（1 到 21）。 */
    static double contrast(int first, int second) {
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

    private static int mix(int from, int to, float amount) {
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
