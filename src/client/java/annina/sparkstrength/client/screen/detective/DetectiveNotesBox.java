package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.role.detective.DetectiveCaseRules;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Multi-line notes field. Vanilla draws the text light with a shadow, so the box keeps a dark ink fill instead of
 * the paper colour; only the frame sprite is replaced to stay texture-free.
 * 多行笔记输入框。原版以浅色带阴影绘制文字，所以输入框保留深色墨水底而非纸色；这里只替换边框贴图，保持无贴图绘制。
 */
final class DetectiveNotesBox extends EditBoxWidget {
    private static final int FILL = 0xFF243447;
    private static final int EDGE = 0xFF3E5673;
    private static final int EDGE_FOCUSED = 0xFFE8D9B5;

    DetectiveNotesBox(TextRenderer renderer, DetectiveFolderLayout.Rect bounds) {
        super(
                renderer,
                bounds.x(),
                bounds.y(),
                bounds.width(),
                bounds.height(),
                Text.translatable("screen.sparkstrength.detective_folder.notes_placeholder"),
                Text.translatable("screen.sparkstrength.detective_folder.notes")
        );
        setMaxLength(DetectiveCaseRules.NOTES_MAX_LENGTH);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && atLineCap()) {
            // Refuse before insertion so the caret stays put; the screen's listener revert would jump it to the end.
            // 在插入前拒绝，光标保持原位；界面监听器的回退会把光标移到末尾。
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean atLineCap() {
        String text = getText();
        int lines = 1;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines >= DetectiveCaseRules.NOTES_MAX_LINES;
    }

    @Override
    protected void drawBox(DrawContext context) {
        int x = getX();
        int y = getY();
        context.fill(x, y, x + getWidth(), y + getHeight(), FILL);
        context.drawBorder(x, y, getWidth(), getHeight(), isFocused() ? EDGE_FOCUSED : EDGE);
    }
}
