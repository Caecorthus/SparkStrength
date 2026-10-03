package annina.sparkstrength.client.screen.tablet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Single-line tablet text editor built on {@link SelectionManager}. It draws only selection, text, placeholder and
 * caret (no background, border or text shadow); the screen paints the surrounding pill. ESC/ENTER/TAB are never
 * consumed so the screen keeps sending, closing and focus traversal.
 * 基于 {@link SelectionManager} 的单行平板输入框，只绘制选区、文字、占位符与光标（无背景、边框与文字阴影），外围胶囊由界面绘制；
 * 从不消费 ESC/回车/TAB，由界面负责发送、关闭与焦点切换。
 */
public final class TabletTextInput extends ClickableWidget {
    private static final int CARET_BLINK_MS = 530;
    private static final int DOUBLE_CLICK_MS = 250;
    private static final int LINE_HEIGHT = 9;
    private static final String ELLIPSIS = "…";
    private static final int PLACEHOLDER_INSET = 3;

    private final TextRenderer textRenderer;
    private final SelectionManager selection;
    private String text = "";
    private int maxLength = 32;
    @Nullable
    private Consumer<String> changedListener;
    @Nullable
    private Text placeholder;
    private int textColor = TabletTheme.TEXT;
    private final int placeholderColor = TabletTheme.TEXT_3;
    private int caretColor = TabletTheme.TEXT;
    private int selectionColor = TabletTheme.withAlpha(TabletTheme.POLICE.base(), 0x66);
    // Char index of the first visible char; always on a code-point boundary.
    // 第一个可见字符的下标，始终位于码点边界上。
    private int firstVisible;
    private long caretEpochMs = Util.getMeasuringTimeMs();
    private long lastClickMs;
    private int lastClickIndex = -1;
    // High surrogate waiting for its low half: GLFW delivers supplementary code points as two charTyped calls.
    // 等待低位代理的高位代理：GLFW 会把补充平面字符拆成两次 charTyped 传入。
    private char pendingHighSurrogate;

    public TabletTextInput(TextRenderer textRenderer, int x, int y, int width, int height, Text narration) {
        super(x, y, width, height, narration);
        this.textRenderer = textRenderer;
        MinecraftClient client = MinecraftClient.getInstance();
        // Every insertion goes through insertText() (and setText/setMaxLength truncate themselves), which pre-truncates
        // to maxLength, so the filter only guards against a rejected write leaving SelectionManager's cursor out of sync.
        // 所有插入都经 insertText() 预先截断到 maxLength（setText/setMaxLength 自行截断），因此过滤器只是兜底，
        // 避免写入被拒导致光标与文本不同步。
        this.selection = new SelectionManager(
                () -> text,
                this::applyText,
                SelectionManager.makeClipboardGetter(client),
                SelectionManager.makeClipboardSetter(client),
                value -> value.length() <= maxLength
        );
    }

    public String getText() {
        return text;
    }

    /**
     * Replaces the text (invalid chars stripped, truncated to the max length) and puts the caret at the end.
     * The changed listener fires only when the text actually changes.
     * 替换文本（去除非法字符并截断到最大长度），光标移到末尾；仅在文本实际改变时触发变更监听。
     */
    public void setText(String value) {
        applyText(truncate(StringHelper.stripInvalidChars(value == null ? "" : value), maxLength));
        selection.putCursorAtEnd();
        touchCaret();
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        if (text.length() > this.maxLength) {
            applyText(truncate(text, this.maxLength));
            selection.setSelection(
                    Math.min(selection.getSelectionStart(), text.length()),
                    Math.min(selection.getSelectionEnd(), text.length())
            );
        }
    }

    public void setChangedListener(@Nullable Consumer<String> changedListener) {
        this.changedListener = changedListener;
    }

    public void setPlaceholder(@Nullable Text placeholder) {
        this.placeholder = placeholder;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public void setCaretColor(int caretColor) {
        this.caretColor = caretColor;
    }

    public void setSelectionColor(int selectionColor) {
        this.selectionColor = selectionColor;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        pendingHighSurrogate = 0;
        if (focused) {
            touchCaret();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !isFocused()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_TAB) {
            return false;
        }
        boolean handled = handleKey(keyCode);
        if (handled) {
            touchCaret();
        }
        return handled;
    }

    private boolean handleKey(int keyCode) {
        if (Screen.isSelectAll(keyCode)) {
            selection.selectAll();
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            selection.copy();
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            insertText(SelectionManager.getClipboard(MinecraftClient.getInstance()));
            return true;
        }
        if (Screen.isCut(keyCode)) {
            selection.cut();
            return true;
        }
        boolean shift = Screen.hasShiftDown();
        // Ctrl (Cmd on macOS, per Screen.hasControlDown) or macOS Option moves/deletes by word.
        // Ctrl（macOS 上 Screen.hasControlDown 为 Cmd）或 macOS 的 Option 按单词移动/删除。
        boolean word = Screen.hasControlDown() || (MinecraftClient.IS_SYSTEM_MAC && Screen.hasAltDown());
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                int direction = keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : 1;
                if (word && !selection.isSelecting()) {
                    selection.deleteWord(direction);
                } else {
                    selection.delete(direction);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                int direction = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1;
                if (word) {
                    selection.moveCursorPastWord(direction, shift);
                } else if (!shift && selection.isSelecting()) {
                    // Collapse the selection to the matching edge, like every desktop text field.
                    // 与常见桌面输入框一致：无 Shift 时方向键把选区收拢到对应端。
                    int a = selection.getSelectionStart();
                    int b = selection.getSelectionEnd();
                    selection.moveCursorTo(direction < 0 ? Math.min(a, b) : Math.max(a, b), false);
                } else {
                    selection.moveCursor(direction, shift);
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                selection.moveCursorToStart(shift);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                selection.moveCursorToEnd(shift);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || !isFocused()) {
            return false;
        }
        if (Character.isHighSurrogate(chr)) {
            pendingHighSurrogate = chr;
            return true;
        }
        if (Character.isLowSurrogate(chr)) {
            if (pendingHighSurrogate != 0) {
                insertText(new String(new char[]{pendingHighSurrogate, chr}));
                pendingHighSurrogate = 0;
                touchCaret();
            }
            return true;
        }
        pendingHighSurrogate = 0;
        if (!StringHelper.isValidChar(chr)) {
            return false;
        }
        insertText(String.valueOf(chr));
        touchCaret();
        return true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int index = indexAt(mouseX);
        long now = Util.getMeasuringTimeMs();
        boolean shift = Screen.hasShiftDown();
        if (!shift && index == lastClickIndex && now - lastClickMs <= DOUBLE_CLICK_MS) {
            selectWordAt(index);
            lastClickIndex = -1;
        } else {
            selection.moveCursorTo(index, shift);
            lastClickIndex = index;
            lastClickMs = now;
        }
        touchCaret();
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        // Dragging past the left edge steps one code point before the first visible char, so updateScroll() sees
        // cursor < firstVisible and scrolls left (indexAt alone clamps to firstVisible and would never scroll).
        // 拖过左边缘时把光标移到首个可见字符之前一个码点，使 updateScroll() 向左滚动（仅用 indexAt 会夹在 firstVisible 而无法滚动）。
        updateScroll();
        int target = (mouseX < getX() && firstVisible > 0)
                ? Util.moveCursor(text, firstVisible, -1)
                : indexAt(mouseX);
        selection.moveCursorTo(target, true);
        touchCaret();
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected MutableText getNarrationMessage() {
        return Text.translatable("narration.edit_box", getText());
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getNarrationMessage());
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        updateScroll();
        int x = getX();
        int y = textY();
        int width = getWidth();
        int cursor = selection.getSelectionStart();

        if (text.isEmpty()) {
            if (placeholder != null) {
                // Inset so the focused caret (drawn at x - 1) never fuses with the first placeholder glyph.
                // 占位文字右移，避免聚焦时的光标（位于 x - 1）与第一个字形粘连。
                context.drawText(textRenderer, ellipsize(placeholder.getString(), width - PLACEHOLDER_INSET),
                        x + PLACEHOLDER_INSET, y, placeholderColor, false);
            }
        } else {
            int visibleEnd = visibleEnd(width);
            if (isFocused() && selection.isSelecting()) {
                int from = clamp(Math.min(cursor, selection.getSelectionEnd()), firstVisible, visibleEnd);
                int to = clamp(Math.max(cursor, selection.getSelectionEnd()), firstVisible, visibleEnd);
                if (to > from) {
                    int left = x + widthBetween(firstVisible, from);
                    int right = x + widthBetween(firstVisible, to);
                    context.fill(left, y - 1, right, y + LINE_HEIGHT, selectionColor);
                }
            }
            context.drawText(textRenderer, text.substring(firstVisible, visibleEnd), x, y, textColor, false);
        }

        if (isFocused() && caretVisible()) {
            // The caret sits in the 1-px advance gap after the previous glyph.
            // 光标位于前一个字形之后的 1 像素字距空隙中。
            int caretX = x + widthBetween(firstVisible, clamp(cursor, firstVisible, text.length())) - 1;
            context.fill(caretX, y - 1, caretX + 1, y - 1 + LINE_HEIGHT, caretColor);
        }
    }

    private void applyText(String value) {
        if (value.equals(text)) {
            return;
        }
        text = value;
        if (changedListener != null) {
            changedListener.accept(value);
        }
    }

    /**
     * Inserts at the caret, replacing the selection; line breaks become spaces, invalid chars are stripped and an
     * over-long insert is cut to the remaining room (never splitting a surrogate pair) instead of being rejected.
     * 在光标处插入并替换选区；换行转为空格、去除非法字符，超长内容截断到剩余容量（不拆分代理对）而不是整体拒绝。
     */
    private void insertText(String raw) {
        String clean = StringHelper.stripInvalidChars(raw.replace('\n', ' ').replace('\t', ' '));
        int selectedLength = Math.abs(selection.getSelectionStart() - selection.getSelectionEnd());
        int room = maxLength - (text.length() - selectedLength);
        clean = truncate(clean, Math.max(0, room));
        if (clean.isEmpty()) {
            return;
        }
        selection.insert(clean);
    }

    private void selectWordAt(int index) {
        int start = index;
        while (start > 0 && !StringHelper.isWhitespace(text.codePointBefore(start))) {
            start -= Character.charCount(text.codePointBefore(start));
        }
        int end = index;
        while (end < text.length() && !StringHelper.isWhitespace(text.codePointAt(end))) {
            end += Character.charCount(text.codePointAt(end));
        }
        if (end > start) {
            selection.setSelection(end, start);
        } else {
            selection.moveCursorTo(index, false);
        }
    }

    /** Char index (code-point boundary) nearest to a mouse x in this widget's coordinates. */
    private int indexAt(double mouseX) {
        updateScroll();
        double relative = mouseX - getX();
        if (relative <= 0) {
            return firstVisible;
        }
        int index = firstVisible;
        int indexWidth = 0;
        while (index < text.length()) {
            int next = Util.moveCursor(text, index, 1);
            int nextWidth = widthBetween(firstVisible, next);
            if (relative < (indexWidth + nextWidth) / 2.0) {
                return index;
            }
            index = next;
            indexWidth = nextWidth;
        }
        return text.length();
    }

    /** Keeps the caret inside the visible window and avoids blank space on the right when scrolled. */
    private void updateScroll() {
        int length = text.length();
        firstVisible = boundary(clamp(firstVisible, 0, length));
        int cursor = clamp(selection.getSelectionStart(), 0, length);
        int width = Math.max(1, getWidth());
        if (cursor < firstVisible) {
            firstVisible = boundary(cursor);
        }
        while (firstVisible < cursor && widthBetween(firstVisible, cursor) > width) {
            firstVisible = Util.moveCursor(text, firstVisible, 1);
        }
        while (firstVisible > 0) {
            int previous = Util.moveCursor(text, firstVisible, -1);
            if (textRenderer.getWidth(text.substring(previous)) > width) {
                break;
            }
            firstVisible = previous;
        }
    }

    private int visibleEnd(int width) {
        int end = firstVisible + textRenderer.trimToWidth(text.substring(firstVisible), Math.max(0, width)).length();
        return boundary(clamp(end, firstVisible, text.length()));
    }

    private int widthBetween(int from, int to) {
        return to <= from ? 0 : textRenderer.getWidth(text.substring(from, to));
    }

    /** Moves an index that splits a surrogate pair back onto the pair's start. */
    private int boundary(int index) {
        if (index > 0 && index < text.length()
                && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) {
            return index - 1;
        }
        return index;
    }

    private String ellipsize(String value, int width) {
        if (textRenderer.getWidth(value) <= width) {
            return value;
        }
        String trimmed = textRenderer.trimToWidth(value, Math.max(0, width - textRenderer.getWidth(ELLIPSIS)));
        return truncate(trimmed, trimmed.length()) + ELLIPSIS;
    }

    private int textY() {
        return getY() + (getHeight() - (LINE_HEIGHT - 1)) / 2;
    }

    private boolean caretVisible() {
        return (Util.getMeasuringTimeMs() - caretEpochMs) / CARET_BLINK_MS % 2 == 0;
    }

    private void touchCaret() {
        caretEpochMs = Util.getMeasuringTimeMs();
    }

    /** Cuts to at most {@code limit} chars without leaving a dangling high surrogate. */
    private static String truncate(String value, int limit) {
        if (value.length() <= limit) {
            if (!value.isEmpty() && Character.isHighSurrogate(value.charAt(value.length() - 1))) {
                return value.substring(0, value.length() - 1);
            }
            return value;
        }
        int end = limit;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
