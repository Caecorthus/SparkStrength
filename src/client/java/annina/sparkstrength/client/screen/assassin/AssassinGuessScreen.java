package annina.sparkstrength.client.screen.assassin;

import annina.sparkstrength.client.screen.assassin.AssassinGuessLayout.BandSpot;
import annina.sparkstrength.client.screen.assassin.AssassinGuessLayout.CellSpot;
import annina.sparkstrength.client.screen.assassin.AssassinGuessLayout.Frame;
import annina.sparkstrength.client.screen.assassin.AssassinGuessLayout.Rect;
import annina.sparkstrength.client.screen.assassin.AssassinGuessLayout.RoleGrid;
import annina.sparkstrength.client.ui.common.PlayerHeadTextureHelper;
import annina.sparkstrength.client.ui.common.PlayerNameResolver;
import annina.sparkstrength.client.ui.common.RoleFactionBands;
import annina.sparkstrength.client.ui.common.UiColors;
import annina.sparkstrength.client.ui.common.UiText;
import annina.sparkstrength.component.assassin.AssassinMisfireComponent;
import annina.sparkstrength.role.detective.DetectiveCaseRules.RolePickerBand;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;
import org.agmas.noellesroles.packet.AssassinGuessRoleC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SparkStrength's Assassin menu, shown in place of NoellesRoles' {@code AssassinScreen}: the living targets on the
 * left, the guessable roles of the chosen target grouped by faction on the right, the stakes and the buttons below.
 * Clicking a role only pencils it in for that target ({@link AssassinGuessNotebook}); nothing is sent until Guess is
 * pressed, so targets and roles can be changed freely and the screen can be closed with the marks kept for the round.
 * SparkStrength 的刺客菜单，替代 NoellesRoles 的 AssassinScreen：左边是存活目标，右边是所选目标可猜的身份（按阵营分组），
 * 下方是风险提示与按钮。点选身份只是用铅笔为该目标记下（AssassinGuessNotebook），按下“确认猜测”前什么都不发送；
 * 因此可以随意更换目标与身份，也可以直接关闭界面，标记保留到本局结束。
 *
 * <p>Authority stays with NoellesRoles: the targets and roles come from its native screen
 * ({@link AssassinGuessSources}), and the guess is its own {@link AssassinGuessRoleC2SPacket}, which the server
 * re-validates. The Guess button mirrors {@code canGuess()} so a cooldown that starts while the menu is open shows.
 * 权威仍在 NoellesRoles：目标与身份取自其原生界面（AssassinGuessSources），猜测使用它自己的 AssassinGuessRoleC2SPacket，
 * 由服务端重新校验。“确认猜测”按钮跟随 canGuess()，菜单打开期间开始的冷却也会显示出来。</p>
 */
public final class AssassinGuessScreen extends Screen {
    private static final String KEY = "screen.sparkstrength.assassin.";
    // targets() initialises a throwaway native screen: once a second is plenty to notice a death.
    // targets() 会初始化一个一次性的原生界面：每秒一次足以发现有人死亡。
    private static final int TARGET_REFRESH_TICKS = 20;
    private static final int TICKS_PER_SECOND = 20;
    // NoellesRoles' 60 s miss cooldown: the widest countdown the Guess button must fit. / 猜错 60 秒冷却：按钮需容纳的最宽倒计时。
    private static final int LONGEST_COOLDOWN_SECONDS = 60;
    private static final int ROLE_SCROLL_ROWS = 2;
    /**
     * Band display order: innocents are an Assassin's usual targets, killers the rarest (only listed with SparkTraits).
     * 分组显示顺序：平民是刺客最常见的目标，杀手最少见（仅在安装 SparkTraits 时列出）。
     */
    private static final List<RolePickerBand> BAND_ORDER = List.of(
            RolePickerBand.INNOCENTS,
            RolePickerBand.NEUTRALS,
            RolePickerBand.WITCHES,
            RolePickerBand.JIAHAO,
            RolePickerBand.KILLERS
    );
    private static final String MARK_GLYPH = "✎";
    private static final Comparator<Target> BY_NAME = Comparator
            .comparing((Target target) -> target.name().toLowerCase(Locale.ROOT))
            .thenComparing(Target::uuid);

    private final ClientPlayerEntity player;
    private List<RoleOption> options;
    private List<List<RoleOption>> bands = List.of();
    private List<Target> targets = List.of();
    private Frame frame;
    private RoleGrid grid;
    private @Nullable UUID focused;
    // The focused target left the list (died) while the menu was open. / 菜单打开期间所选目标离开名单（死亡）。
    private boolean focusLost;
    private boolean opened;
    private double targetScroll;
    private double roleScroll;
    private int refreshTicks;
    private @Nullable List<Text> tooltip;

    public AssassinGuessScreen(ClientPlayerEntity player) {
        super(Text.translatable(KEY + "title"));
        this.player = player;
        this.options = roleOptions(AssassinGuessSources.roles(player));
    }

    @Override
    protected void init() {
        super.init();
        frame = AssassinGuessLayout.frame(
                width,
                height,
                textRenderer.getWidth(Text.translatable(KEY + "erase")),
                textRenderer.getWidth(Text.translatable(KEY + "close")),
                widestGuessLabel()
        );
        bands = bandMembers(options);
        grid = AssassinGuessLayout.flowRoles(bands.stream().map(List::size).toList(),
                AssassinGuessLayout.rowWidth(frame.roleGrid()));
        refreshTargets();
        if (!opened) {
            opened = true;
            // Reopening returns to the target last looked at; a first open starts at the top of the list.
            // 再次打开时回到上次查看的目标；首次打开从列表第一位开始。
            UUID remembered = AssassinGuessNotebook.focusedTarget();
            if (remembered != null && isListed(remembered)) {
                focus(remembered);
            } else {
                focus(targets.isEmpty() ? null : targets.getFirst().uuid());
            }
            scrollFocusedRowIntoView();
        }
        clampScrolls();
    }

    /** Widest text the Guess button can show, so it never resizes. / “确认猜测”按钮可能显示的最宽文字，按钮因此不会变宽变窄。 */
    private int widestGuessLabel() {
        int widest = textRenderer.getWidth(Text.translatable(KEY + "cooldown", LONGEST_COOLDOWN_SECONDS));
        for (String key : List.of("guess", "guess_lethal", "no_guesses")) {
            widest = Math.max(widest, textRenderer.getWidth(Text.translatable(KEY + key)));
        }
        return widest;
    }

    @Override
    public void tick() {
        super.tick();
        if (client == null || client.player != player || !AssassinGuessSources.canOpen(player)) {
            close();
            return;
        }
        if (++refreshTicks >= TARGET_REFRESH_TICKS) {
            refreshTicks = 0;
            refreshTargets();
            clampScrolls();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- input / 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (frame.guessButton().contains(mouseX, mouseY)) {
            if (guessBlock() == GuessBlock.READY) {
                guess();
            }
            return true;
        }
        if (frame.eraseButton().contains(mouseX, mouseY)) {
            if (canErase()) {
                AssassinGuessNotebook.erase(focused);
                playClick(1.0F);
            }
            return true;
        }
        if (frame.closeButton().contains(mouseX, mouseY)) {
            playClick(1.0F);
            close();
            return true;
        }
        Target target = targetAt(mouseX, mouseY);
        if (target != null) {
            if (!target.uuid().equals(focused)) {
                focus(target.uuid());
                playClick(1.0F);
            }
            return true;
        }
        RoleOption role = roleAt(mouseX, mouseY);
        if (role != null && canMark()) {
            toggleMark(role);
            playClick(1.3F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0D && frame.targetList().contains(mouseX, mouseY)) {
            targetScroll -= verticalAmount * (AssassinGuessLayout.ROW_HEIGHT + AssassinGuessLayout.ROW_GAP);
            clampScrolls();
            return true;
        }
        if (verticalAmount != 0.0D && frame.roleGrid().contains(mouseX, mouseY)) {
            roleScroll -= verticalAmount * ROLE_SCROLL_ROWS * (AssassinGuessLayout.CELL_HEIGHT + AssassinGuessLayout.CELL_GAP);
            clampScrolls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---------------------------------------------------------------- state / 状态

    private void refreshTargets() {
        if (client == null) {
            return;
        }
        List<Target> fresh = new ArrayList<>();
        for (UUID uuid : AssassinGuessSources.targets(client, player, width, height)) {
            fresh.add(new Target(uuid, PlayerNameResolver.playerName(uuid)));
        }
        fresh.sort(BY_NAME);
        targets = List.copyOf(fresh);
        if (focused != null && !isListed(focused)) {
            // Never move the choice to someone else on its own: a guess must name who the player picked.
            // 绝不自动把选择移到别人身上：猜测必须指向玩家亲手选的人。
            focused = null;
            focusLost = true;
        }
    }

    private void focus(@Nullable UUID target) {
        focused = target;
        focusLost = false;
        AssassinGuessNotebook.focus(target);
        CellSpot markCell = target == null ? null : cellOf(AssassinGuessNotebook.markOf(target));
        if (markCell != null) {
            roleScroll = AssassinGuessLayout.scrollToShow(roleScroll, markCell.y(), AssassinGuessLayout.CELL_HEIGHT,
                    frame.roleGrid().height());
            clampScrolls();
        }
    }

    private void toggleMark(RoleOption option) {
        Identifier id = option.role().identifier();
        if (id.equals(AssassinGuessNotebook.markOf(focused))) {
            AssassinGuessNotebook.erase(focused);
        } else {
            AssassinGuessNotebook.mark(focused, id);
        }
    }

    /**
     * Sends the pencilled role for the focused target and closes, as NoellesRoles did on click. The mark is used up:
     * a correct guess kills the target and a wrong one proved it false.
     * 为所选目标发送铅笔记下的身份并关闭（与 NoellesRoles 点击后的行为一致）。标记随之用掉：猜对则目标死亡，猜错则证明它不对。
     */
    private void guess() {
        Identifier role = AssassinGuessNotebook.markOf(focused);
        ClientPlayNetworking.send(new AssassinGuessRoleC2SPacket(focused, role));
        AssassinGuessNotebook.erase(focused);
        playClick(0.8F);
        close();
    }

    private GuessBlock guessBlock() {
        AssassinPlayerComponent charges = AssassinPlayerComponent.KEY.get(player);
        if (!charges.canGuess()) {
            return charges.getGuessesRemaining() <= 0 ? GuessBlock.NO_GUESSES : GuessBlock.COOLDOWN;
        }
        if (!canMark()) {
            return GuessBlock.NO_TARGET;
        }
        return AssassinGuessNotebook.markOf(focused) == null ? GuessBlock.NO_MARK : GuessBlock.READY;
    }

    private boolean canMark() {
        return focused != null && isListed(focused);
    }

    private boolean canErase() {
        return canMark() && AssassinGuessNotebook.markOf(focused) != null;
    }

    private boolean isListed(UUID uuid) {
        for (Target target : targets) {
            if (target.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Target focusedTarget() {
        for (Target target : targets) {
            if (target.uuid().equals(focused)) {
                return target;
            }
        }
        return null;
    }

    private void clampScrolls() {
        int listContent = AssassinGuessLayout.targetListHeight(targets.size());
        targetScroll = Math.max(0.0D, Math.min(AssassinGuessLayout.maxScroll(listContent, frame.targetList().height()), targetScroll));
        roleScroll = Math.max(0.0D, Math.min(AssassinGuessLayout.maxScroll(grid.contentHeight(), frame.roleGrid().height()), roleScroll));
    }

    private void scrollFocusedRowIntoView() {
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).uuid().equals(focused)) {
                targetScroll = AssassinGuessLayout.scrollToShow(targetScroll, AssassinGuessLayout.targetRowY(index),
                        AssassinGuessLayout.ROW_HEIGHT, frame.targetList().height());
                return;
            }
        }
    }

    private void playClick(float pitch) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ---------------------------------------------------------------- hit testing / 命中检测

    private @Nullable Target targetAt(double mouseX, double mouseY) {
        Rect list = frame.targetList();
        if (!list.contains(mouseX, mouseY) || mouseX >= list.x() + AssassinGuessLayout.rowWidth(list)) {
            return null;
        }
        double contentY = mouseY - list.y() + targetScroll;
        int stride = AssassinGuessLayout.ROW_HEIGHT + AssassinGuessLayout.ROW_GAP;
        int index = (int) Math.floor(contentY / stride);
        if (index < 0 || index >= targets.size() || contentY - index * stride >= AssassinGuessLayout.ROW_HEIGHT) {
            return null;
        }
        return targets.get(index);
    }

    private @Nullable RoleOption roleAt(double mouseX, double mouseY) {
        Rect area = frame.roleGrid();
        if (!area.contains(mouseX, mouseY)) {
            return null;
        }
        double contentX = mouseX - area.x();
        double contentY = mouseY - area.y() + roleScroll;
        for (CellSpot cell : grid.cells()) {
            if (contentX >= cell.x() && contentX < cell.x() + cell.width()
                    && contentY >= cell.y() && contentY < cell.y() + AssassinGuessLayout.CELL_HEIGHT) {
                return bands.get(cell.band()).get(cell.member());
            }
        }
        return null;
    }

    private @Nullable CellSpot cellOf(@Nullable Identifier roleId) {
        if (roleId == null) {
            return null;
        }
        for (CellSpot cell : grid.cells()) {
            if (bands.get(cell.band()).get(cell.member()).role().identifier().equals(roleId)) {
                return cell;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- rendering / 绘制

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        tooltip = null;
        context.fill(0, 0, width, height, AssassinGuessPaint.VEIL);
        drawBars(context);
        Rect panel = frame.panel();
        context.fill(panel.x() + 2, panel.y() + 2, panel.right() + 2, panel.bottom() + 2, AssassinGuessPaint.PANEL_SHADOW);
        AssassinGuessPaint.fill(context, panel, AssassinGuessPaint.PANEL);
        AssassinGuessPaint.border(context, panel, AssassinGuessPaint.PANEL_EDGE);
        drawTargets(context, mouseX, mouseY);
        drawRoleHeader(context);
        drawRoles(context, mouseX, mouseY);
        drawFooter(context, mouseX, mouseY);
        if (tooltip != null) {
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private void drawBars(DrawContext context) {
        Rect top = frame.topBar();
        Rect bottom = frame.bottomBar();
        AssassinGuessPaint.fill(context, top, AssassinGuessPaint.BAR);
        AssassinGuessPaint.fill(context, bottom, AssassinGuessPaint.BAR);
        context.fill(top.x(), top.bottom() - 1, top.right(), top.bottom(), AssassinGuessPaint.BAR_EDGE);
        context.fill(bottom.x(), bottom.y(), bottom.right(), bottom.y() + 1, AssassinGuessPaint.BAR_EDGE);
        int textRoom = width - AssassinGuessLayout.MARGIN * 2;
        drawCentered(context, UiText.trim(textRenderer, title.getString(), textRoom),
                top.y() + (top.height() - 8) / 2, AssassinGuessPaint.TITLE);
        drawCentered(context, UiText.trim(textRenderer, Text.translatable(KEY + "hint").getString(), textRoom),
                bottom.y() + (bottom.height() - 8) / 2 + 1, AssassinGuessPaint.HINT);
    }

    private void drawCentered(DrawContext context, String text, int y, int color) {
        context.drawText(textRenderer, text, (width - textRenderer.getWidth(text)) / 2, y, color, true);
    }

    private void drawTargets(DrawContext context, int mouseX, int mouseY) {
        Rect header = frame.targetHeader();
        String label = Text.translatable(KEY + "targets").getString();
        context.drawText(textRenderer, label, header.x() + 1, header.y() + 2, AssassinGuessPaint.TEXT, false);
        context.drawText(textRenderer, String.valueOf(targets.size()), header.x() + 1 + textRenderer.getWidth(label) + 4,
                header.y() + 2, AssassinGuessPaint.TEXT_DIM, false);
        context.fill(header.x(), header.bottom() - 3, header.right(), header.bottom() - 2, AssassinGuessPaint.RULE);

        Rect list = frame.targetList();
        if (targets.isEmpty()) {
            String empty = UiText.trim(textRenderer, Text.translatable(KEY + "no_targets").getString(), list.width() - 4);
            context.drawText(textRenderer, empty, list.x() + 2, list.y() + 4, AssassinGuessPaint.TEXT_DIM, false);
            return;
        }
        int rowWidth = AssassinGuessLayout.rowWidth(list);
        Target hovered = targetAt(mouseX, mouseY);
        context.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        for (int index = 0; index < targets.size(); index++) {
            int y = list.y() + AssassinGuessLayout.targetRowY(index) - (int) Math.round(targetScroll);
            if (y + AssassinGuessLayout.ROW_HEIGHT <= list.y() || y >= list.bottom()) {
                continue;
            }
            drawTargetRow(context, targets.get(index), new Rect(list.x(), y, rowWidth, AssassinGuessLayout.ROW_HEIGHT),
                    targets.get(index) == hovered);
        }
        context.disableScissor();
        drawScrollbar(context, list, AssassinGuessLayout.targetListHeight(targets.size()), targetScroll);
    }

    private void drawTargetRow(DrawContext context, Target target, Rect row, boolean hovered) {
        boolean isFocused = target.uuid().equals(focused);
        int fill = isFocused ? AssassinGuessPaint.ROW_FOCUS : hovered ? AssassinGuessPaint.ROW_HOVER : AssassinGuessPaint.ROW;
        AssassinGuessPaint.fill(context, row, fill);
        if (isFocused) {
            AssassinGuessPaint.border(context, row, AssassinGuessPaint.ROW_FOCUS_EDGE);
            context.fill(row.x(), row.y(), row.x() + 2, row.bottom(), AssassinGuessPaint.FOCUS_ACCENT);
        }
        int headX = row.x() + 4;
        int headY = row.y() + (row.height() - AssassinGuessLayout.HEAD_SIZE) / 2;
        Role mark = roleById(AssassinGuessNotebook.markOf(target.uuid()));
        if (mark != null) {
            // The pencilled role rings the head, so the list reads as a notebook at a glance.
            // 铅笔记下的身份颜色环绕头像，列表一眼就能当笔记本看。
            context.drawBorder(headX - 1, headY - 1, AssassinGuessLayout.HEAD_SIZE + 2, AssassinGuessLayout.HEAD_SIZE + 2,
                    AssassinGuessPaint.readableOn(mark.color(), fill));
        }
        PlayerSkinDrawer.draw(context, PlayerHeadTextureHelper.resolveStableSkinTextures(target.uuid(), null), headX, headY,
                AssassinGuessLayout.HEAD_SIZE);

        int textX = headX + AssassinGuessLayout.HEAD_SIZE + 4;
        int textWidth = Math.max(0, row.right() - 3 - textX);
        String name = UiText.trim(textRenderer, target.name(), textWidth);
        context.drawText(textRenderer, name, textX, row.y() + AssassinGuessLayout.ROW_NAME_Y, isFocused ? 0xFFFFFFFF : AssassinGuessPaint.TEXT, false);
        String markLine;
        int markColor;
        if (mark != null) {
            markLine = MARK_GLYPH + " " + RoleFactionBands.roleName(mark).getString();
            markColor = AssassinGuessPaint.readableOn(mark.color(), fill);
        } else {
            markLine = Text.translatable(KEY + "unmarked").getString();
            markColor = AssassinGuessPaint.TEXT_FAINT;
        }
        String visibleMark = UiText.trim(textRenderer, markLine, textWidth);
        context.drawText(textRenderer, visibleMark, textX, row.y() + AssassinGuessLayout.ROW_MARK_Y, markColor, false);
        if (hovered && (!name.equals(target.name()) || !visibleMark.equals(markLine))) {
            tooltip = mark == null ? List.of(Text.literal(target.name())) : List.of(Text.literal(target.name()), Text.literal(markLine));
        }
    }

    private void drawRoleHeader(DrawContext context) {
        Rect header = frame.roleHeader();
        context.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), AssassinGuessPaint.RULE);
        Target target = focusedTarget();
        if (target == null) {
            String key = targets.isEmpty() ? "no_targets" : focusLost ? "target_gone" : "pick_target";
            String line = UiText.trim(textRenderer, Text.translatable(KEY + key).getString(), header.width() - 2);
            context.drawText(textRenderer, line, header.x() + 1, header.y() + (header.height() - 8) / 2,
                    focusLost ? AssassinGuessPaint.STAKE_LETHAL : AssassinGuessPaint.TEXT_DIM, false);
            return;
        }
        int headY = header.y() + 2;
        PlayerSkinDrawer.draw(context, PlayerHeadTextureHelper.resolveStableSkinTextures(target.uuid(), null), header.x(),
                headY, AssassinGuessLayout.HEAD_SIZE);
        int textX = header.x() + AssassinGuessLayout.HEAD_SIZE + 5;
        int textWidth = Math.max(0, header.right() - textX);
        context.drawText(textRenderer, UiText.trim(textRenderer, target.name(), textWidth), textX, header.y() + 2,
                0xFFFFFFFF, false);
        Role mark = roleById(AssassinGuessNotebook.markOf(target.uuid()));
        int lineY = header.y() + 12;
        if (mark == null) {
            context.drawText(textRenderer, UiText.trim(textRenderer, Text.translatable(KEY + "mark_hint").getString(),
                    textWidth), textX, lineY, AssassinGuessPaint.TEXT_DIM, false);
            return;
        }
        String prefix = Text.translatable(KEY + "marked").getString();
        context.drawText(textRenderer, prefix, textX, lineY, AssassinGuessPaint.TEXT_DIM, false);
        int nameX = textX + textRenderer.getWidth(prefix);
        context.drawText(textRenderer, UiText.trim(textRenderer, RoleFactionBands.roleName(mark).getString(),
                        Math.max(0, header.right() - nameX)), nameX, lineY,
                AssassinGuessPaint.readableOn(mark.color(), AssassinGuessPaint.PANEL), false);
    }

    private void drawRoles(DrawContext context, int mouseX, int mouseY) {
        Rect area = frame.roleGrid();
        int contentWidth = AssassinGuessLayout.rowWidth(area);
        int scroll = (int) Math.round(roleScroll);
        boolean markable = canMark();
        RoleOption hovered = markable ? roleAt(mouseX, mouseY) : null;
        Identifier mark = markable ? AssassinGuessNotebook.markOf(focused) : null;
        context.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (BandSpot band : grid.bands()) {
            int y = area.y() + band.headerY() - scroll;
            if (y + AssassinGuessLayout.BAND_HEADER_HEIGHT > area.y() && y < area.bottom()) {
                drawBandHeader(context, BAND_ORDER.get(band.band()), bands.get(band.band()).size(), area.x(), y, contentWidth);
            }
        }
        for (CellSpot cell : grid.cells()) {
            int y = area.y() + cell.y() - scroll;
            if (y + AssassinGuessLayout.CELL_HEIGHT <= area.y() || y >= area.bottom()) {
                continue;
            }
            RoleOption option = bands.get(cell.band()).get(cell.member());
            drawRoleCell(context, option, new Rect(area.x() + cell.x(), y, cell.width(), AssassinGuessLayout.CELL_HEIGHT),
                    option == hovered, option.role().identifier().equals(mark));
        }
        if (!markable) {
            // No target chosen: the grid stays readable but plainly inactive. / 未选目标：网格仍可阅读，但明显不可用。
            AssassinGuessPaint.fill(context, area, AssassinGuessPaint.CELL_DISABLED_VEIL);
        }
        context.disableScissor();
        drawScrollbar(context, area, grid.contentHeight(), roleScroll);
    }

    private void drawBandHeader(DrawContext context, RolePickerBand band, int count, int x, int y, int width) {
        int accent = AssassinGuessPaint.readableOn(RoleFactionBands.accent(band), AssassinGuessPaint.PANEL);
        context.fill(x, y + 1, x + 3, y + 8, accent);
        String name = Text.translatable(KEY + "band." + band.name().toLowerCase(Locale.ROOT)).getString();
        int textX = x + 6;
        context.drawText(textRenderer, name, textX, y + 1, accent, false);
        int countX = textX + textRenderer.getWidth(name) + 4;
        String countText = String.valueOf(count);
        context.drawText(textRenderer, countText, countX, y + 1, AssassinGuessPaint.TEXT_DIM, false);
        int ruleX = countX + textRenderer.getWidth(countText) + 4;
        if (ruleX < x + width) {
            context.fill(ruleX, y + 4, x + width, y + 5, UiColors.withAlpha(accent, 0x55));
        }
    }

    private void drawRoleCell(DrawContext context, RoleOption option, Rect cell, boolean hovered, boolean marked) {
        int roleColor = UiColors.opaque(option.role().color());
        int fill = marked
                ? UiColors.mix(AssassinGuessPaint.CELL, roleColor, AssassinGuessPaint.MARK_TINT)
                : hovered ? AssassinGuessPaint.CELL_HOVER : AssassinGuessPaint.CELL;
        AssassinGuessPaint.fill(context, cell, fill);
        if (marked) {
            AssassinGuessPaint.border(context, cell, AssassinGuessPaint.readableOn(roleColor, AssassinGuessPaint.PANEL));
        } else if (hovered) {
            AssassinGuessPaint.border(context, cell, AssassinGuessPaint.CELL_HOVER_EDGE);
        }
        int glyphRoom = marked ? textRenderer.getWidth(MARK_GLYPH) + 3 : 0;
        String name = option.name().getString();
        String visible = UiText.trim(textRenderer, name, cell.width() - 7 - glyphRoom);
        int textColor = marked ? 0xFFFFFFFF : AssassinGuessPaint.readableOn(roleColor, fill);
        int textY = cell.y() + (cell.height() - 8) / 2;
        context.drawText(textRenderer, visible, cell.x() + 4, textY, textColor, false);
        if (marked) {
            context.drawText(textRenderer, MARK_GLYPH, cell.right() - 3 - textRenderer.getWidth(MARK_GLYPH), textY, 0xFFFFFFFF, false);
        }
        if (hovered && !visible.equals(name)) {
            tooltip = List.of(option.name());
        }
    }

    private void drawScrollbar(DrawContext context, Rect area, int contentHeight, double scroll) {
        int[] thumb = AssassinGuessLayout.scrollThumb(contentHeight, area.height(), scroll);
        if (thumb == null) {
            return;
        }
        int x = area.right() - AssassinGuessLayout.SCROLLBAR_WIDTH;
        context.fill(x, area.y(), area.right(), area.bottom(), AssassinGuessPaint.SCROLL_TRACK);
        context.fill(x, area.y() + thumb[0], area.right(), area.y() + thumb[0] + thumb[1], AssassinGuessPaint.SCROLL_THUMB);
    }

    private void drawFooter(DrawContext context, int mouseX, int mouseY) {
        Rect footer = frame.footer();
        context.fill(footer.x(), footer.y() - 2, footer.right(), footer.y() - 1, AssassinGuessPaint.RULE);
        AssassinPlayerComponent charges = AssassinPlayerComponent.KEY.get(player);
        boolean forgiven = AssassinMisfireComponent.KEY.get(player).hasFreeMiss();

        Rect status = frame.status();
        int lineY = status.y() + 3;
        int pipsWidth = AssassinGuessPaint.pips(context, status.x() + 1, lineY + 1, charges.getGuessesRemaining(),
                charges.getMaxGuesses());
        int countX = status.x() + 1 + (pipsWidth > 0 ? pipsWidth + 4 : 0);
        String count = Text.translatable(KEY + "guesses", charges.getGuessesRemaining(), charges.getMaxGuesses()).getString();
        context.drawText(textRenderer, UiText.trim(textRenderer, count, Math.max(0, status.right() - countX)),
                countX, lineY, AssassinGuessPaint.TEXT, false);
        // The stakes read as a coloured tag, gold while a miss is forgiven, red once the next miss kills.
        // 风险提示画成一枚彩色标签：猜错可免死时为金色，下次猜错致命时为红色。
        String stake = UiText.trim(textRenderer,
                Text.translatable(KEY + (forgiven ? "stake.forgiven" : "stake.lethal")).getString(),
                status.width() - AssassinGuessPaint.TAG_PAD * 2 - 2);
        int stakeColor = forgiven ? AssassinGuessPaint.STAKE_FORGIVEN : AssassinGuessPaint.STAKE_LETHAL;
        int tagY = lineY + 10;
        Rect tag = new Rect(status.x(), tagY - 2, textRenderer.getWidth(stake) + AssassinGuessPaint.TAG_PAD * 2 + 2, 11);
        AssassinGuessPaint.fill(context, tag, UiColors.withAlpha(stakeColor, AssassinGuessPaint.TAG_ALPHA));
        context.fill(tag.x(), tag.y(), tag.x() + 2, tag.bottom(), stakeColor);
        context.drawText(textRenderer, stake, tag.x() + 2 + AssassinGuessPaint.TAG_PAD, tagY, stakeColor, false);

        drawSecondaryButton(context, frame.eraseButton(), Text.translatable(KEY + "erase").getString(), canErase(), mouseX, mouseY);
        drawSecondaryButton(context, frame.closeButton(), Text.translatable(KEY + "close").getString(), true, mouseX, mouseY);
        drawGuessButton(context, charges, forgiven, mouseX, mouseY);
    }

    private void drawSecondaryButton(DrawContext context, Rect button, String label, boolean enabled, int mouseX, int mouseY) {
        if (!enabled) {
            AssassinGuessPaint.button(context, textRenderer, button, label, AssassinGuessPaint.DISABLED,
                    AssassinGuessPaint.DISABLED_EDGE, AssassinGuessPaint.DISABLED_TEXT);
            return;
        }
        boolean hovered = button.contains(mouseX, mouseY);
        AssassinGuessPaint.button(context, textRenderer, button, label,
                hovered ? AssassinGuessPaint.BUTTON_HOVER : AssassinGuessPaint.BUTTON,
                hovered ? AssassinGuessPaint.BUTTON_EDGE_HOVER : AssassinGuessPaint.BUTTON_EDGE, AssassinGuessPaint.TEXT);
    }

    private void drawGuessButton(DrawContext context, AssassinPlayerComponent charges, boolean forgiven, int mouseX, int mouseY) {
        Rect button = frame.guessButton();
        GuessBlock block = guessBlock();
        String label = switch (block) {
            case NO_GUESSES -> Text.translatable(KEY + "no_guesses").getString();
            case COOLDOWN -> Text.translatable(KEY + "cooldown",
                    (charges.getCooldownTicks() + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND).getString();
            default -> Text.translatable(KEY + (forgiven ? "guess" : "guess_lethal")).getString();
        };
        if (block != GuessBlock.READY) {
            // A cooldown or empty charges is news worth reading; "pick something first" stays quiet.
            // 冷却或次数用完是值得读的信息；“先选目标/身份”则保持低调。
            boolean serverReason = block == GuessBlock.NO_GUESSES || block == GuessBlock.COOLDOWN;
            AssassinGuessPaint.button(context, textRenderer, button, label, AssassinGuessPaint.DISABLED,
                    AssassinGuessPaint.DISABLED_EDGE,
                    serverReason ? AssassinGuessPaint.BLOCKED_TEXT : AssassinGuessPaint.DISABLED_TEXT);
            return;
        }
        boolean hovered = button.contains(mouseX, mouseY);
        AssassinGuessPaint.button(context, textRenderer, button, label,
                hovered ? AssassinGuessPaint.GUESS_HOVER : AssassinGuessPaint.GUESS, AssassinGuessPaint.GUESS_EDGE,
                AssassinGuessPaint.GUESS_TEXT);
    }

    // ---------------------------------------------------------------- roles / 身份

    private @Nullable Role roleById(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        for (RoleOption option : options) {
            if (option.role().identifier().equals(id)) {
                return option.role();
            }
        }
        for (Role role : WatheRoles.ROLES) {
            if (role != null && role.identifier().equals(id)) {
                return role;
            }
        }
        return null;
    }

    private static List<RoleOption> roleOptions(List<Role> roles) {
        List<RoleOption> result = new ArrayList<>();
        for (Role role : roles) {
            result.add(new RoleOption(role, RoleFactionBands.bandOf(role), RoleFactionBands.roleName(role)));
        }
        return List.copyOf(result);
    }

    /** Options split by {@link #BAND_ORDER}, registration order kept inside a band. / 按 BAND_ORDER 拆分，组内保持注册顺序。 */
    private static List<List<RoleOption>> bandMembers(List<RoleOption> options) {
        List<List<RoleOption>> result = new ArrayList<>();
        for (RolePickerBand band : BAND_ORDER) {
            result.add(options.stream().filter(option -> option.band() == band).toList());
        }
        return List.copyOf(result);
    }

    private record RoleOption(Role role, RolePickerBand band, Text name) {
    }

    private record Target(UUID uuid, String name) {
    }

    /** Why the Guess button is off, in the order the reasons are checked. / “确认猜测”不可用的原因，按检查顺序。 */
    private enum GuessBlock {
        READY,
        NO_GUESSES,
        COOLDOWN,
        NO_TARGET,
        NO_MARK
    }
}
