package annina.sparkstrength.client.ui.common;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;

/**
 * One-line labels cut to a pixel width with a trailing "…", shared by the detective and assassin screens. When even
 * the ellipsis does not fit, the label becomes the ellipsis alone. The tablet keeps its own trims (they drop the
 * ellipsis, strip spaces or keep styled runs), which are different contracts.
 * 侦探/刺客界面共用的单行标签按像素宽度截断并追加“…”。宽度连省略号都放不下时只剩省略号。
 * 平板有各自的截断方法（会省掉省略号、去掉空格或保留分段样式），约定不同，不在此合并。
 */
public final class UiText {
    private static final String ELLIPSIS = "…";

    private UiText() {
    }

    public static String trim(TextRenderer renderer, String value, int width) {
        if (renderer.getWidth(value) <= width) {
            return value;
        }
        return renderer.trimToWidth(value, Math.max(0, width - renderer.getWidth(ELLIPSIS))) + ELLIPSIS;
    }

    /**
     * Same cut for a label drawn with {@code style}; widths are measured in that style (bold glyphs are wider).
     * 以 {@code style} 绘制的标签做同样截断；宽度按该样式测量（粗体字形更宽）。
     */
    public static String trimStyled(TextRenderer renderer, String value, Style style, int width) {
        if (renderer.getWidth(StringVisitable.styled(value, style)) <= width) {
            return value;
        }
        int budget = Math.max(0, width - renderer.getWidth(StringVisitable.styled(ELLIPSIS, style)));
        return renderer.trimToWidth(StringVisitable.styled(value, style), budget).getString() + ELLIPSIS;
    }
}
