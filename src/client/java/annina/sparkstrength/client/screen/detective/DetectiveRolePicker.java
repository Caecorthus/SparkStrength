package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.client.ui.common.RoleFactionBands;
import annina.sparkstrength.client.ui.common.UiText;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import annina.sparkstrength.role.detective.DetectiveCaseRules.RolePickerBand;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modal killer-role picker drawn over the folder: killers, neutrals, witch faction, Team Jiahao, then innocents, in a
 * scrollable grid.
 * 覆盖在文件夹上的凶手身份选择浮层：按杀手、中立、魔女阵营、嘉豪阵营、平民分组，网格可滚动。
 *
 * <p>The list is presentation only and offers every registered non-special role, including ones disabled for this
 * game ("any role"). The server re-validates every guess against the same set (WatheRoles.ROLES minus special roles).
 * 列表仅用于展示，提供所有已注册的非特殊角色（包括本局未启用的角色，即“任何身份”）；服务端按同一范围
 * （WatheRoles.ROLES 去除特殊角色）重新校验每个推测。</p>
 */
final class DetectiveRolePicker {
    private static final int HEADER_HEIGHT = 20;
    private static final int CELL_HEIGHT = 16;
    private static final int CELL_GAP = 2;
    private static final int GROUP_GAP = 6;
    private static final int GROUP_BAR = 3;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MIN_CELL_WIDTH = 88;
    private static final float PICKER_Z = 200.0F;
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
    private static final DetectiveFolderPaint.PickerChrome CHROME = new DetectiveFolderPaint.PickerChrome(
            COLOR_PANEL, COLOR_EDGE, COLOR_TITLE, COLOR_DIVIDER, COLOR_BUTTON, COLOR_BUTTON_HOVER);

    private List<Option> options = List.of();
    private boolean open;
    private double scroll;

    boolean isOpen() {
        return open;
    }

    void open(DetectiveFolderLayout layout, TextRenderer renderer, @Nullable Identifier current) {
        options = buildOptions();
        open = true;
        scroll = 0.0D;
        Geometry geometry = geometry(layout, renderer);
        for (Cell cell : geometry.cells()) {
            if (cell.option().role().identifier().equals(current)) {
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
        return hovered == null ? null : new Choice(hovered.option().role().identifier());
    }

    void render(
            DrawContext context,
            TextRenderer renderer,
            DetectiveFolderLayout layout,
            int mouseX,
            int mouseY,
            @Nullable Identifier current
    ) {
        Geometry geometry = geometry(layout, renderer);
        clampScroll(geometry);
        DetectiveFolderLayout.Rect box = geometry.box();
        Text tooltip = null;

        context.getMatrices().push();
        // Lift above the notes widget and any batched text drawn before. / 抬高到笔记控件与先前绘制的文字之上。
        context.getMatrices().translate(0.0F, 0.0F, PICKER_Z);
        // The scrim also covers the ACTIVE pin raised above the folder. / 遮罩同时盖住文件夹上方凸起的“调查中”图钉。
        DetectiveFolderLayout.Rect scrim = layout.scrim();
        context.fill(scrim.x(), scrim.y(), scrim.right(), scrim.bottom(), COLOR_SCRIM);
        DetectiveFolderPaint.drawPickerFrame(context, renderer, box, geometry.clearButton(), HEADER_HEIGHT,
                Text.translatable("screen.sparkstrength.detective_folder.killer_pick_title").getString(),
                current != null, mouseX, mouseY, CHROME);

        DetectiveFolderLayout.Rect grid = geometry.grid();
        Cell hovered = hoveredCell(geometry, mouseX, mouseY);
        context.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
        for (Band band : geometry.bands()) {
            int top = grid.y() + band.top() - (int) Math.round(scroll);
            context.fill(grid.x(), top, grid.x() + GROUP_BAR, top + band.height(), RoleFactionBands.accent(band.group()));
        }
        for (Cell cell : geometry.cells()) {
            int y = grid.y() + cell.y() - (int) Math.round(scroll);
            if (y + CELL_HEIGHT < grid.y() || y > grid.bottom()) {
                continue;
            }
            Role role = cell.option().role();
            boolean isCurrent = role.identifier().equals(current);
            boolean isHovered = cell == hovered;
            int x = cell.x();
            int fill = isHovered ? COLOR_CELL_HOVER : isCurrent ? COLOR_CELL_CURRENT : COLOR_CELL;
            context.fill(x, y, x + cell.width(), y + CELL_HEIGHT, fill);
            int roleColor = DetectiveFolderPaint.readableOn(role.color(), fill);
            if (isCurrent) {
                context.drawBorder(x, y, cell.width(), CELL_HEIGHT, roleColor);
            } else if (isHovered) {
                context.drawBorder(x, y, cell.width(), CELL_HEIGHT, COLOR_DIVIDER);
            }
            String name = cell.option().name().getString();
            String visible = UiText.trim(renderer, name, cell.width() - 8);
            context.drawText(renderer, visible, x + 4, y + 4, roleColor, false);
            if (isHovered && !visible.equals(name)) {
                tooltip = cell.option().name();
            }
        }
        context.disableScissor();

        DetectiveFolderPaint.drawScrollbar(context, grid, box.right() - 6 - SCROLLBAR_WIDTH, SCROLLBAR_WIDTH,
                geometry.contentHeight(), scroll, COLOR_SCROLL_TRACK, COLOR_SCROLL_THUMB);
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
        int innerX = grid.x() + GROUP_BAR + 2;
        int innerWidth = Math.max(1, grid.right() - innerX);
        int columns = Math.max(2, Math.min(4, innerWidth / MIN_CELL_WIDTH));
        int cellWidth = Math.max(1, (innerWidth - (columns - 1) * CELL_GAP) / columns);

        List<Cell> cells = new ArrayList<>();
        List<Band> bands = new ArrayList<>();
        int y = 0;
        for (RolePickerBand group : RolePickerBand.values()) {
            List<Option> members = options.stream().filter(option -> option.group() == group).toList();
            if (members.isEmpty()) {
                continue;
            }
            if (!bands.isEmpty()) {
                y += GROUP_GAP;
            }
            int bandTop = y;
            for (int index = 0; index < members.size(); index++) {
                int column = index % columns;
                if (index > 0 && column == 0) {
                    y += CELL_HEIGHT + CELL_GAP;
                }
                cells.add(new Cell(members.get(index), innerX + column * (cellWidth + CELL_GAP), y, cellWidth));
            }
            y += CELL_HEIGHT;
            bands.add(new Band(group, bandTop, y - bandTop));
        }
        return new Geometry(box, clear, grid, cells, bands, y);
    }

    /**
     * Every registered, non-special role (whether or not it is enabled for this game), grouped killers, neutrals,
     * witch faction, Team Jiahao, innocents in registration order.
     * 所有已注册的非特殊角色（无论本局是否启用），按杀手、中立、魔女阵营、嘉豪阵营、平民分组并保持注册顺序。
     */
    private static List<Option> buildOptions() {
        Set<Identifier> special = new HashSet<>();
        for (Role role : WatheRoles.SPECIAL_ROLES) {
            if (role != null) {
                special.add(role.identifier());
            }
        }
        Set<Identifier> seen = new HashSet<>();
        List<Option> result = new ArrayList<>();
        for (Role role : WatheRoles.ROLES) {
            if (role == null || special.contains(role.identifier()) || !seen.add(role.identifier())) {
                continue;
            }
            result.add(new Option(role, RoleFactionBands.bandOf(role), RoleFactionBands.roleName(role)));
        }
        return List.copyOf(result);
    }

    /** A picked role id, or null for "clear". / 选中的角色 ID；null 表示清除。 */
    record Choice(@Nullable Identifier roleId) {
    }

    /** Drawn in {@link DetectiveCaseRules.RolePickerBand} order. / 按 RolePickerBand 的顺序绘制。 */
    private record Option(Role role, RolePickerBand group, Text name) {
    }

    /** One grid cell; {@code y} is in scrolled content space. / 网格单元；y 为滚动内容坐标。 */
    private record Cell(Option option, int x, int y, int width) {
    }

    private record Band(RolePickerBand group, int top, int height) {
    }

    private record Geometry(
            DetectiveFolderLayout.Rect box,
            DetectiveFolderLayout.Rect clearButton,
            DetectiveFolderLayout.Rect grid,
            List<Cell> cells,
            List<Band> bands,
            int contentHeight
    ) {
    }
}
