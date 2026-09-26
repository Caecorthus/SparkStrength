package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.client.ui.common.PlayerHeadTextureHelper;
import annina.sparkstrength.client.ui.common.PlayerNameResolver;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Modal presumed-killer picker drawn over the folder: every participant of the current round except the viewer,
 * sorted by name, in a scrollable grid of heads and names.
 * 覆盖在文件夹上的假定凶手选择浮层：列出本局除自己以外的所有参与者，按名字排序，以头像加名字的网格滚动显示。
 *
 * <p>Presentation only. The list is the client copy of the round's game profiles and never shows alive/dead state,
 * which would leak undiscovered deaths. Heads use the stable skin helper (tab list / Wathe cache), never live entity
 * skins. The server re-validates every pick against the same participant map.
 * 仅用于展示：列表取自客户端同步的本局玩家档案，绝不显示存活/死亡状态（否则会泄露未被发现的死亡）；头像使用稳定
 * 皮肤工具（Tab 列表 / Wathe 缓存），不读取实体实时皮肤。服务端按同一参与者表重新校验每次选择。</p>
 */
final class DetectivePlayerPicker {
    private static final int HEADER_HEIGHT = 20;
    private static final int HEAD_SIZE = 16;
    private static final int CELL_PADDING = 2;
    private static final int CELL_HEIGHT = HEAD_SIZE + CELL_PADDING * 2;
    private static final int CELL_GAP = 2;
    private static final int NAME_GAP = 4;
    private static final int NAME_RIGHT_PADDING = 4;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MIN_COLUMNS = 2;
    private static final int MAX_COLUMNS = 3;
    private static final float PICKER_Z = 200.0F;
    // The active case tab's pin starts this far above the folder; the scrim covers it too.
    // 当前命案标签的图钉从文件夹上方此距离处开始绘制，遮罩也要盖住它。
    private static final int SCRIM_OVERHANG = 14;
    private static final int COLOR_SCRIM = 0x88000000;
    private static final int COLOR_PANEL = 0xF21E1A16;
    private static final int COLOR_EDGE = 0xFFD9A95B;
    private static final int COLOR_TITLE = 0xFFF2E6CF;
    private static final int COLOR_DIVIDER = 0xFF5A4C3C;
    private static final int COLOR_CELL = 0xFF2B2520;
    private static final int COLOR_CELL_HOVER = 0xFF40362C;
    private static final int COLOR_CELL_CURRENT = 0xFF3A3026;
    private static final int COLOR_BUTTON = 0xFF3A2F26;
    private static final int COLOR_BUTTON_HOVER = 0xFF54443A;
    private static final int COLOR_SCROLL_TRACK = 0xFF2B2520;
    private static final int COLOR_SCROLL_THUMB = 0xFFB08A55;

    private List<Option> options = List.of();
    private int widestName;
    private boolean open;
    private double scroll;

    boolean isOpen() {
        return open;
    }

    void open(DetectiveFolderLayout layout, TextRenderer renderer, @Nullable UUID current) {
        options = buildOptions();
        widestName = 0;
        for (Option option : options) {
            widestName = Math.max(widestName, renderer.getWidth(option.name()));
        }
        open = true;
        scroll = 0.0D;
        Geometry geometry = geometry(layout, renderer);
        for (Cell cell : geometry.cells()) {
            if (cell.option().uuid().equals(current)) {
                scroll = cell.y() - (geometry.grid().height() - CELL_HEIGHT) / 2.0D;
                break;
            }
        }
        clampScroll(geometry);
    }

    void close() {
        open = false;
    }

    void scroll(DetectiveFolderLayout layout, TextRenderer renderer, double verticalAmount) {
        scroll -= verticalAmount * (CELL_HEIGHT + CELL_GAP);
        clampScroll(geometry(layout, renderer));
    }

    /**
     * Handles a click while open; the click never falls through. Returns the choice, or null when the click only
     * dismissed the picker or hit nothing.
     * 处理浮层打开时的点击，点击不会穿透。返回所选结果；仅关闭浮层或未命中时返回 null。
     */
    @Nullable Choice click(DetectiveFolderLayout layout, TextRenderer renderer, double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return null;
        }
        Geometry geometry = geometry(layout, renderer);
        if (!geometry.box().contains(mouseX, mouseY)) {
            close();
            return null;
        }
        if (geometry.clearButton().contains(mouseX, mouseY)) {
            return new Choice(null);
        }
        Cell hovered = hoveredCell(geometry, mouseX, mouseY);
        return hovered == null ? null : new Choice(hovered.option().uuid());
    }

    void render(
            DrawContext context,
            TextRenderer renderer,
            DetectiveFolderLayout layout,
            int mouseX,
            int mouseY,
            @Nullable UUID current
    ) {
        Geometry geometry = geometry(layout, renderer);
        clampScroll(geometry);
        DetectiveFolderLayout.Rect box = geometry.box();
        Text tooltip = null;

        context.getMatrices().push();
        // Lift above the notes widget and any batched text drawn before. / 抬高到笔记控件与先前绘制的文字之上。
        context.getMatrices().translate(0.0F, 0.0F, PICKER_Z);
        DetectiveFolderLayout.Rect folder = layout.folder();
        context.fill(folder.x(), folder.y() - SCRIM_OVERHANG, folder.right(), folder.bottom(), COLOR_SCRIM);
        context.fill(box.x() + 3, box.y() + 3, box.right() + 3, box.bottom() + 3, 0x66000000);
        context.fill(box.x(), box.y(), box.right(), box.bottom(), COLOR_PANEL);
        context.drawBorder(box.x(), box.y(), box.width(), box.height(), COLOR_EDGE);

        DetectiveFolderLayout.Rect clear = geometry.clearButton();
        String title = Text.translatable("screen.sparkstrength.detective_folder.presumed_killer_pick_title").getString();
        context.drawText(
                renderer,
                DetectiveFolderPaint.trim(renderer, title, Math.max(0, clear.x() - box.x() - 14)),
                box.x() + 8,
                box.y() + 6,
                COLOR_TITLE,
                false
        );
        boolean clearHovered = clear.contains(mouseX, mouseY);
        context.fill(clear.x(), clear.y(), clear.right(), clear.bottom(), clearHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        context.drawBorder(clear.x(), clear.y(), clear.width(), clear.height(), clearHovered ? COLOR_EDGE : COLOR_DIVIDER);
        String clearLabel = Text.translatable("screen.sparkstrength.detective_folder.killer_clear").getString();
        context.drawText(
                renderer,
                clearLabel,
                clear.x() + (clear.width() - renderer.getWidth(clearLabel)) / 2,
                clear.y() + 3,
                current == null ? DetectiveFolderPaint.withAlpha(COLOR_TITLE, 0x99) : COLOR_TITLE,
                false
        );
        context.fill(box.x() + 6, box.y() + HEADER_HEIGHT - 1, box.right() - 6, box.y() + HEADER_HEIGHT, COLOR_DIVIDER);

        DetectiveFolderLayout.Rect grid = geometry.grid();
        Cell hovered = hoveredCell(geometry, mouseX, mouseY);
        context.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
        for (Cell cell : geometry.cells()) {
            int y = grid.y() + cell.y() - (int) Math.round(scroll);
            if (y + CELL_HEIGHT < grid.y() || y > grid.bottom()) {
                continue;
            }
            Option option = cell.option();
            boolean isCurrent = option.uuid().equals(current);
            boolean isHovered = cell == hovered;
            int x = cell.x();
            int fill = isHovered ? COLOR_CELL_HOVER : isCurrent ? COLOR_CELL_CURRENT : COLOR_CELL;
            context.fill(x, y, x + cell.width(), y + CELL_HEIGHT, fill);
            if (isCurrent) {
                context.drawBorder(x, y, cell.width(), CELL_HEIGHT, COLOR_EDGE);
            } else if (isHovered) {
                context.drawBorder(x, y, cell.width(), CELL_HEIGHT, COLOR_DIVIDER);
            }
            PlayerSkinDrawer.draw(
                    context,
                    PlayerHeadTextureHelper.resolveStableSkinTextures(option.uuid(), null),
                    x + CELL_PADDING,
                    y + CELL_PADDING,
                    HEAD_SIZE
            );
            String visible = DetectiveFolderPaint.trim(renderer, option.name(), nameRoom(cell.width()));
            context.drawText(
                    renderer,
                    visible,
                    x + CELL_PADDING + HEAD_SIZE + NAME_GAP,
                    y + (CELL_HEIGHT - renderer.fontHeight) / 2 + 1,
                    isCurrent ? COLOR_EDGE : COLOR_TITLE,
                    false
            );
            if (isHovered && !visible.equals(option.name())) {
                tooltip = Text.literal(option.name());
            }
        }
        context.disableScissor();

        if (geometry.contentHeight() > grid.height()) {
            int trackX = box.right() - 6 - SCROLLBAR_WIDTH;
            context.fill(trackX, grid.y(), trackX + SCROLLBAR_WIDTH, grid.bottom(), COLOR_SCROLL_TRACK);
            int thumbHeight = Math.max(8, grid.height() * grid.height() / geometry.contentHeight());
            int maxScroll = geometry.contentHeight() - grid.height();
            int thumbY = grid.y() + (int) Math.round((grid.height() - thumbHeight) * (scroll / Math.max(1, maxScroll)));
            context.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, COLOR_SCROLL_THUMB);
        }
        context.getMatrices().pop();

        if (tooltip != null) {
            context.drawTooltip(renderer, tooltip, mouseX, mouseY);
        }
    }

    private @Nullable Cell hoveredCell(Geometry geometry, double mouseX, double mouseY) {
        DetectiveFolderLayout.Rect grid = geometry.grid();
        if (!grid.contains(mouseX, mouseY)) {
            return null;
        }
        double contentY = mouseY - grid.y() + scroll;
        for (Cell cell : geometry.cells()) {
            if (mouseX >= cell.x() && mouseX < cell.x() + cell.width()
                    && contentY >= cell.y() && contentY < cell.y() + CELL_HEIGHT) {
                return cell;
            }
        }
        return null;
    }

    private void clampScroll(Geometry geometry) {
        double maxScroll = Math.max(0, geometry.contentHeight() - geometry.grid().height());
        scroll = Math.max(0.0D, Math.min(maxScroll, scroll));
    }

    private Geometry geometry(DetectiveFolderLayout layout, TextRenderer renderer) {
        DetectiveFolderLayout.Rect box = layout.pickerBox();
        String clearLabel = Text.translatable("screen.sparkstrength.detective_folder.killer_clear").getString();
        int clearWidth = Math.min(Math.max(0, box.width() / 2), renderer.getWidth(clearLabel) + 14);
        DetectiveFolderLayout.Rect clear = new DetectiveFolderLayout.Rect(box.right() - 6 - clearWidth, box.y() + 3, clearWidth, 14);
        DetectiveFolderLayout.Rect grid = new DetectiveFolderLayout.Rect(
                box.x() + 6,
                box.y() + HEADER_HEIGHT + 3,
                box.width() - 12 - SCROLLBAR_WIDTH - 2,
                box.height() - HEADER_HEIGHT - 9
        );
        // Three columns only when every name fits untrimmed; otherwise two wider columns.
        // 仅当所有名字都能完整显示时使用三列，否则使用两列更宽的格子。
        int columns = widestName <= nameRoom(cellWidth(grid.width(), MAX_COLUMNS)) ? MAX_COLUMNS : MIN_COLUMNS;
        int cellWidth = cellWidth(grid.width(), columns);

        List<Cell> cells = new ArrayList<>();
        int y = 0;
        for (int index = 0; index < options.size(); index++) {
            int column = index % columns;
            if (index > 0 && column == 0) {
                y += CELL_HEIGHT + CELL_GAP;
            }
            cells.add(new Cell(options.get(index), grid.x() + column * (cellWidth + CELL_GAP), y, cellWidth));
        }
        int contentHeight = options.isEmpty() ? 0 : y + CELL_HEIGHT;
        return new Geometry(box, clear, grid, cells, contentHeight);
    }

    private static int cellWidth(int gridWidth, int columns) {
        return Math.max(1, (gridWidth - (columns - 1) * CELL_GAP) / columns);
    }

    private static int nameRoom(int cellWidth) {
        return Math.max(0, cellWidth - CELL_PADDING - HEAD_SIZE - NAME_GAP - NAME_RIGHT_PADDING);
    }

    /**
     * Every uuid in the client's round profile map except the local player, sorted by name (case-insensitive).
     * 客户端本局玩家档案表中除本人外的所有 UUID，按名字排序（不区分大小写）。
     */
    private static List<Option> buildOptions() {
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (self == null) {
            return List.of();
        }
        List<Option> result = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(GameWorldComponent.KEY.get(self.getWorld()).getGameProfiles().keySet())) {
            if (uuid == null || uuid.equals(self.getUuid())) {
                continue;
            }
            result.add(new Option(uuid, PlayerNameResolver.playerName(uuid)));
        }
        result.sort(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Option::uuid));
        return List.copyOf(result);
    }

    /** A picked player uuid, or null for "clear". / 选中的玩家 UUID；null 表示清除。 */
    record Choice(@Nullable UUID playerUuid) {
    }

    private record Option(UUID uuid, String name) {
    }

    /** One grid cell; {@code y} is in scrolled content space. / 网格单元；y 为滚动内容坐标。 */
    private record Cell(Option option, int x, int y, int width) {
    }

    private record Geometry(
            DetectiveFolderLayout.Rect box,
            DetectiveFolderLayout.Rect clearButton,
            DetectiveFolderLayout.Rect grid,
            List<Cell> cells,
            int contentHeight
    ) {
    }
}
