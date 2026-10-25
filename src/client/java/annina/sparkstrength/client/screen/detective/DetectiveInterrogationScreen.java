package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.ui.common.PlayerNameResolver;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.DetectiveCase;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.SuspectClue;
import annina.sparkstrength.network.detective.DetectiveInterrogationResultS2CPacket;
import annina.sparkstrength.network.detective.InterrogateDetectiveSuspectC2SPacket;
import annina.sparkstrength.network.detective.SelectDetectiveCaseC2SPacket;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import annina.sparkstrength.role.detective.DetectiveCaseRules.InvestigationOutcome;
import annina.sparkstrength.role.detective.DetectiveRules;
import annina.sparkstrength.util.ItemCooldownSync;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The magnifier's remote interrogation roster, drawn as a cork evidence board: a paper header with the case under
 * investigation (switchable), the clue count and the magnifier cooldown; one pinned polaroid per round participant;
 * and a paper strip with the latest answer. Clicking a polaroid asks the server. Opened by the server after it synced
 * {@link DetectiveCasePlayerComponent}.
 * 放大镜的远程审问名单，绘制为软木证物板：纸质页眉显示正在调查的命案（可切换）、线索数与放大镜冷却；每名本局参与者一张
 * 钉住的拍立得；底部纸条显示最近一次回应。点击拍立得即向服务端提问。服务端先同步案件组件后再通知打开。
 *
 * <p>Authority: the server decides every answer. The roster is the client copy of the round's game profiles: every
 * participant except the viewer, never their alive/dead state (owner 2026-10-25). Recorded clues and "not present"
 * answers come from the owner-only case sync; the result packet only drives the answer line. The detective picks real
 * players here, so names and heads are resolved from the real uuid.
 * 权威：每个回应都由服务端决定。名单取自客户端同步的本局玩家档案：除自己以外的所有参与者，绝不显示存活/死亡状态
 * （所有者 2026-10-25）。已记录的线索与“不在场”回应都来自仅发给本人的案件同步；结果包只驱动回应文字。
 * 这里选择的是真实玩家，因此名字与头像按真实 UUID 解析。</p>
 */
public final class DetectiveInterrogationScreen extends Screen {
    private static final String KEY = "screen.sparkstrength.detective_interrogation.";
    // A request the server dropped (it raced the cooldown) stops blocking the roster after this long.
    // 服务端丢弃的请求（与冷却赛跑）在此时间后不再阻塞名单。
    private static final int PENDING_TIMEOUT_TICKS = 40;
    // An unconfirmed case switch falls back to the synced selection after this long. 未确认的命案切换在此时间后回退到同步值。
    private static final int SELECTION_TIMEOUT_TICKS = 100;
    private static final int REVOKE_GRACE_TICKS = 10;

    private static final int PREFERRED_WIDTH = 320;
    private static final int PREFERRED_HEIGHT = 200;
    private static final int FRAME = 6;
    private static final int INSET = 4;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 14;
    private static final int BOARD_GAP = 4;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_ROOM = SCROLLBAR_WIDTH + 3;
    private static final int CARD_WIDTH = 56;
    private static final int CARD_HEIGHT = 40;
    private static final int CARD_GAP = 3;
    // 3x the 8 px skin face, so heads stay pixel-crisp. / 皮肤脸部 8 像素的 3 倍，头像像素保持清晰。
    private static final int PHOTO_SIZE = 24;
    private static final int PHOTO_TOP = 3;
    private static final int TAG_HEIGHT = 9;
    // Room above the first row for the pins and below the last for the drop shadow. / 首行上方留给图钉，末行下方留给投影。
    private static final int CONTENT_TOP_PAD = 3;
    private static final int CONTENT_BOTTOM_PAD = 2;
    private static final int ARROW_BOX = 9;
    private static final int CORK_SPECKLE_CELL = 9;

    private static final int COLOR_DIM = 0xB0000000;
    private static final int COLOR_SHADOW = 0x66000000;
    private static final int COLOR_FRAME = 0xFF6B4A2E;
    private static final int COLOR_FRAME_EDGE = 0xFF3B2716;
    private static final int COLOR_FRAME_LIGHT = 0xFF8C6642;
    private static final int COLOR_FRAME_DARK = 0xFF52371F;
    private static final int COLOR_CORK = 0xFFB98A57;
    private static final int COLOR_CORK_DARK = 0xFF9C7045;
    private static final int COLOR_CORK_LIGHT = 0xFFCCA06D;
    private static final int COLOR_CORK_SHADE = 0x33000000;
    private static final int COLOR_PAPER = 0xFFF6F1E3;
    private static final int COLOR_PAPER_EDGE = 0xFFD8CFB8;
    private static final int COLOR_CARD = 0xFFFBFBF8;
    private static final int COLOR_CARD_EDGE = 0xFFCFCABF;
    private static final int COLOR_CARD_HOVER = 0xFFFFF6DC;
    private static final int COLOR_CARD_HOVER_EDGE = 0xFFD9A95B;
    private static final int COLOR_CARD_SHADOW = 0x55000000;
    private static final int COLOR_CARD_DISABLED = 0x47000000;
    private static final int COLOR_PHOTO = 0xFF2F2F2F;
    private static final int COLOR_PHOTO_ABSENT = 0x99262626;
    private static final int COLOR_PIN = 0xFFD9B44A;
    private static final int COLOR_PIN_RECORDED = 0xFFC8372D;
    private static final int COLOR_PIN_ABSENT = 0xFF8C857A;
    private static final int COLOR_PIN_SHINE = 0xCCFFFFFF;
    private static final int COLOR_TAG_RECORDED = 0xFFA0322A;
    private static final int COLOR_TAG_ABSENT = 0xFF5A534A;
    private static final int COLOR_TAG_TEXT = 0xFFF8F1E4;
    private static final int COLOR_ARROW = 0xFFE6DCC8;
    private static final int COLOR_ARROW_HOVER = 0xFFFFF3D6;
    private static final int COLOR_COOLDOWN_BAR = 0xCCA0322A;
    private static final int COLOR_SCROLL_TRACK = 0x40000000;
    private static final int COLOR_SCROLL_THUMB = 0xFF4A3220;
    private static final int COLOR_LENS = 0xFFBFD8E8;
    private static final int COLOR_HANDLE = 0xFF6B4A2E;

    private final DetectiveFolderPaint.StickyNoteWidths stickyNoteWidths = new DetectiveFolderPaint.StickyNoteWidths();
    private Layout layout = Layout.forViewport(0, 0);
    private List<RosterEntry> roster = List.of();
    private int rosterSourceSize = -1;
    private double scroll;
    private @Nullable UUID pendingTarget;
    private int pendingTicks;
    private @Nullable UUID pendingSelection;
    private int pendingSelectionTicks;
    private int lastSyncRevision;
    private @Nullable Text resultLine;
    private @Nullable UUID resultCase;
    private int revokedTicks;
    private int ageTicks;
    private @Nullable List<Text> hoverTooltip;

    public DetectiveInterrogationScreen() {
        super(Text.translatable(KEY + "title"));
    }

    /** Result packet handler: updates an open roster's answer line. / 结果包处理：更新打开中名单的回应文字。 */
    public static void acceptResult(DetectiveInterrogationResultS2CPacket result) {
        if (MinecraftClient.getInstance().currentScreen instanceof DetectiveInterrogationScreen screen) {
            screen.onResult(result);
        }
    }

    @Override
    protected void init() {
        super.init();
        layout = Layout.forViewport(width, height);
        stickyNoteWidths.clear();
        DetectiveCasePlayerComponent folder = folder();
        if (folder != null) {
            lastSyncRevision = folder.getSyncRevision();
        }
        rebuildRoster();
        clampScroll();
    }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        ClientPlayerEntity player = client == null ? null : client.player;
        DetectiveCasePlayerComponent folder = folder();
        if (player == null || folder == null) {
            close();
            return;
        }
        // Mirror the server gate (alive, non-spectator detective holding the magnifier); requests would be ignored.
        // 与服务端门槛一致（存活、非旁观、手持放大镜的侦探）；不满足时请求本就会被忽略。
        if (!canKeepOpen(player)) {
            if (++revokedTicks >= REVOKE_GRACE_TICKS) {
                close();
                return;
            }
        } else {
            revokedTicks = 0;
        }
        int revision = folder.getSyncRevision();
        if (revision != lastSyncRevision) {
            lastSyncRevision = revision;
            if (pendingSelection != null
                    && (pendingSelection.equals(folder.getSelectedCaseId()) || folder.findCase(pendingSelection) == null)) {
                pendingSelection = null;
            }
        }
        if (pendingSelection != null && --pendingSelectionTicks <= 0) {
            pendingSelection = null;
        }
        if (pendingTarget != null && --pendingTicks <= 0) {
            pendingTarget = null;
        }
        if (participantCount(player) != rosterSourceSize) {
            rebuildRoster();
            clampScroll();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DetectiveCasePlayerComponent folder = folder();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || folder == null || !layout.frame().contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        DetectiveCase shown = shownCase(folder);
        if (shown != null && folder.getCases().size() > 1) {
            HeaderGeometry header = headerGeometry(folder, shown);
            if (header.previous().contains(mouseX, mouseY)) {
                switchCase(folder, shown, -1);
                return true;
            }
            if (header.next().contains(mouseX, mouseY)) {
                switchCase(folder, shown, 1);
                return true;
            }
        }
        if (shown != null) {
            Card card = hoveredCard(mouseX, mouseY);
            if (card != null) {
                CardState state = cardState(shown, card.entry().uuid());
                if (state.askable() && canAsk(folder, shown) && send(new InterrogateDetectiveSuspectC2SPacket(card.entry().uuid()))) {
                    pendingTarget = card.entry().uuid();
                    pendingTicks = PENDING_TIMEOUT_TICKS;
                    resultLine = null;
                    playSound(SoundEvents.UI_BUTTON_CLICK.value());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0D && layout.board().contains(mouseX, mouseY)) {
            scroll -= verticalAmount * (CARD_HEIGHT + CARD_GAP);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, COLOR_DIM);
        hoverTooltip = null;
        ClientPlayerEntity player = client == null ? null : client.player;
        DetectiveCasePlayerComponent folder = folder();
        DetectiveFolderLayout.Rect frame = layout.frame();
        // Clip to the frame so a window smaller than the board cuts it off instead of drawing over the world.
        // 裁剪到边框内，窗口小于证物板时截掉溢出部分，而不是画到世界上。
        context.enableScissor(frame.x(), frame.y(), frame.right() + 3, frame.bottom() + 3);
        drawFrame(context);
        DetectiveCase shown = folder == null ? null : shownCase(folder);
        if (player != null && folder != null) {
            drawHeader(context, player, folder, shown, mouseX, mouseY, delta);
            drawBoard(context, folder, shown, mouseX, mouseY);
            drawFooter(context, player, folder, shown);
        }
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();
        if (hoverTooltip != null) {
            context.drawTooltip(textRenderer, hoverTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No vanilla blur/darkening; render() draws its own dim fill behind the board.
        // 不使用原版模糊/变暗；render() 自行在证物板后方绘制暗色遮罩。
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- Rendering / 绘制 ----

    private void drawFrame(DrawContext context) {
        DetectiveFolderLayout.Rect frame = layout.frame();
        DetectiveFolderLayout.Rect cork = layout.cork();
        context.fill(frame.x() + 3, frame.y() + 3, frame.right() + 3, frame.bottom() + 3, COLOR_SHADOW);
        context.fill(frame.x(), frame.y(), frame.right(), frame.bottom(), COLOR_FRAME);
        context.drawBorder(frame.x(), frame.y(), frame.width(), frame.height(), COLOR_FRAME_EDGE);
        // Bevel: lit top-left, shaded bottom-right, then a dark lip around the cork. / 斜面：左上受光、右下背光，软木外一圈深色边。
        context.fill(frame.x() + 1, frame.y() + 1, frame.right() - 1, frame.y() + 2, COLOR_FRAME_LIGHT);
        context.fill(frame.x() + 1, frame.y() + 1, frame.x() + 2, frame.bottom() - 1, COLOR_FRAME_LIGHT);
        context.fill(frame.x() + 1, frame.bottom() - 2, frame.right() - 1, frame.bottom() - 1, COLOR_FRAME_DARK);
        context.fill(frame.right() - 2, frame.y() + 1, frame.right() - 1, frame.bottom() - 1, COLOR_FRAME_DARK);
        context.drawBorder(cork.x() - 1, cork.y() - 1, cork.width() + 2, cork.height() + 2, COLOR_FRAME_EDGE);
        context.fill(cork.x(), cork.y(), cork.right(), cork.bottom(), COLOR_CORK);
        // Deterministic cork grain, about one speckle per cell, sent as one batch. / 确定性的软木颗粒，每格约一粒，合为一个批次。
        context.draw(() -> {
            for (int cellY = cork.y(); cellY < cork.bottom() - 1; cellY += CORK_SPECKLE_CELL) {
                for (int cellX = cork.x(); cellX < cork.right() - 1; cellX += CORK_SPECKLE_CELL) {
                    int hash = speckleHash(cellX - cork.x(), cellY - cork.y());
                    if ((hash & 0x30) == 0) {
                        continue;
                    }
                    int x = Math.min(cork.right() - 2, cellX + (hash & 7));
                    int y = Math.min(cork.bottom() - 2, cellY + ((hash >> 3) & 7) % CORK_SPECKLE_CELL);
                    int size = (hash & 0x40) != 0 ? 2 : 1;
                    context.fill(x, y, Math.min(cork.right(), x + size), Math.min(cork.bottom(), y + 1),
                            (hash & 0x80) != 0 ? COLOR_CORK_DARK : COLOR_CORK_LIGHT);
                }
            }
        });
        // Inner shade under the frame lip. / 边框内沿下的阴影。
        context.fill(cork.x(), cork.y(), cork.right(), cork.y() + 2, COLOR_CORK_SHADE);
        context.fill(cork.x(), cork.y(), cork.x() + 2, cork.bottom(), COLOR_CORK_SHADE);
    }

    private static int speckleHash(int x, int y) {
        int hash = x * 0x27D4EB2D ^ y * 0x165667B1;
        hash ^= hash >>> 15;
        hash *= 0x2C1B3C6D;
        return hash ^ (hash >>> 12);
    }

    private void drawHeader(DrawContext context, ClientPlayerEntity player, DetectiveCasePlayerComponent folder,
                            @Nullable DetectiveCase shown, int mouseX, int mouseY, float delta) {
        DetectiveFolderLayout.Rect header = layout.header();
        context.fill(header.x() + 2, header.y() + 2, header.right() + 2, header.bottom() + 2, COLOR_CARD_SHADOW);
        context.fill(header.x(), header.y(), header.right(), header.bottom(), COLOR_PAPER);
        context.drawBorder(header.x(), header.y(), header.width(), header.height(), COLOR_PAPER_EDGE);
        context.fill(header.x() - 3, header.y() - 2, header.x() + 11, header.y() + 5, DetectiveFolderPaint.TAPE);
        context.fill(header.right() - 11, header.y() - 2, header.right() + 3, header.y() + 5, DetectiveFolderPaint.TAPE);

        int lineOneY = header.y() + 4;
        int lineTwoY = header.y() + 14;
        drawMagnifierIcon(context, header.x() + 5, lineOneY - 1);
        int textX = header.x() + 17;
        int rightX = header.right() - 6;
        Style bold = Style.EMPTY.withBold(true);
        int limit = DetectiveCaseRules.clampSuspectLimit(folder.getSyncedSuspectLimit());
        String clues = shown == null ? "" : Text.translatable("screen.sparkstrength.detective_folder.clues",
                shown.getSuspects().size(), limit).getString();
        int cluesX = rightX - textRenderer.getWidth(clues);
        String title = Text.translatable(KEY + "title").getString();
        context.drawText(textRenderer, Text.literal(trimStyled(title, bold, Math.max(0, cluesX - 6 - textX))).setStyle(bold),
                textX, lineOneY, DetectiveFolderPaint.INK_RED, false);
        if (!clues.isEmpty()) {
            context.drawText(textRenderer, clues, cluesX, lineOneY, DetectiveFolderPaint.INK_NAVY, false);
        }

        int cooldownTicks = ItemCooldownSync.remainingTicks(player.getItemCooldownManager(), SparkStrengthItems.magnifier());
        String cooldown = cooldownTicks > 0
                ? Text.translatable(KEY + "cooldown", Math.ceilDiv(cooldownTicks, 20)).getString()
                : "";
        int cooldownX = rightX - textRenderer.getWidth(cooldown);
        if (!cooldown.isEmpty()) {
            context.drawText(textRenderer, cooldown, cooldownX, lineTwoY, DetectiveFolderPaint.INK_RED, false);
            float progress = player.getItemCooldownManager().getCooldownProgress(SparkStrengthItems.magnifier(), delta);
            int barWidth = Math.round((header.width() - 2) * Math.max(0.0F, Math.min(1.0F, progress)));
            context.fill(header.x() + 1, header.bottom() - 2, header.x() + 1 + barWidth, header.bottom() - 1, COLOR_COOLDOWN_BAR);
        }

        if (shown == null) {
            String none = Text.translatable(KEY + "no_case").getString();
            context.drawText(textRenderer, DetectiveFolderPaint.trim(textRenderer, none, Math.max(0, cooldownX - 6 - textX)),
                    textX, lineTwoY, DetectiveFolderPaint.INK_MUTED, false);
            return;
        }
        HeaderGeometry geometry = headerGeometry(folder, shown);
        boolean arrows = folder.getCases().size() > 1;
        if (arrows) {
            drawArrowBox(context, geometry.previous(), true, mouseX, mouseY);
            drawArrowBox(context, geometry.next(), false, mouseX, mouseY);
        }
        Text caseText = Text.translatable(KEY + "investigating", Text.literal(geometry.victimName()).setStyle(bold));
        context.drawText(textRenderer, caseText, geometry.caseTextX(), lineTwoY, DetectiveFolderPaint.INK, false);
        DetectiveFolderLayout.Rect caseArea = new DetectiveFolderLayout.Rect(geometry.caseTextX(), lineTwoY - 1,
                textRenderer.getWidth(caseText), textRenderer.fontHeight + 1);
        if (caseArea.contains(mouseX, mouseY) && !geometry.victimName().equals(shown.getVictimName())) {
            hoverTooltip = List.of(Text.literal(shown.getVictimName()));
        }
    }

    /**
     * Case line geometry: with more than one case, a ‹ box, the "investigating" text and a › box right after it;
     * otherwise the text alone, aligned with the title. The victim name is trimmed so the line stops before the cooldown.
     * 命案行的几何：多于一起命案时为 ‹ 按钮、“调查中”文字与紧随其后的 › 按钮；否则只有文字，与标题对齐。受害人名字会被截短，
     * 使整行止于冷却文字之前。
     */
    private HeaderGeometry headerGeometry(DetectiveCasePlayerComponent folder, DetectiveCase shown) {
        DetectiveFolderLayout.Rect header = layout.header();
        int lineTwoY = header.y() + 14;
        boolean arrows = folder.getCases().size() > 1;
        // Reserve the widest cooldown text so the line never shifts while it counts down. / 预留最宽的冷却文字，倒计时期间整行不跳动。
        String widestCooldown = Text.translatable(KEY + "cooldown", 88).getString();
        int end = header.right() - 6 - textRenderer.getWidth(widestCooldown) - 6;
        DetectiveFolderLayout.Rect previous = new DetectiveFolderLayout.Rect(header.x() + 5, lineTwoY - 1, ARROW_BOX, ARROW_BOX);
        int textX = arrows ? previous.right() + 3 : header.x() + 17;
        Style bold = Style.EMPTY.withBold(true);
        int labelWidth = textRenderer.getWidth(Text.translatable(KEY + "investigating", ""));
        int room = Math.max(0, end - (arrows ? ARROW_BOX + 3 : 0) - textX - labelWidth);
        String victim = trimStyled(shown.getVictimName(), bold, room);
        int textWidth = textRenderer.getWidth(Text.translatable(KEY + "investigating", Text.literal(victim).setStyle(bold)));
        DetectiveFolderLayout.Rect next = new DetectiveFolderLayout.Rect(textX + textWidth + 3, lineTwoY - 1, ARROW_BOX, ARROW_BOX);
        return new HeaderGeometry(previous, next, textX, victim);
    }

    private void drawArrowBox(DrawContext context, DetectiveFolderLayout.Rect box, boolean left, int mouseX, int mouseY) {
        boolean hovered = box.contains(mouseX, mouseY);
        context.fill(box.x(), box.y(), box.right(), box.bottom(), hovered ? COLOR_ARROW_HOVER : COLOR_ARROW);
        context.drawBorder(box.x(), box.y(), box.width(), box.height(), COLOR_PAPER_EDGE);
        DetectiveFolderPaint.drawArrow(context, box.x() + (box.width() - 4) / 2, box.y() + 1, left,
                hovered ? DetectiveFolderPaint.INK_RED : DetectiveFolderPaint.INK);
    }

    /** 10x9 magnifier: a ring with a pale lens and a short handle to the bottom right. / 10x9 放大镜：浅色镜片的圆环加右下方短柄。 */
    private static void drawMagnifierIcon(DrawContext context, int x, int y) {
        context.fill(x + 1, y + 1, x + 6, y + 6, COLOR_LENS);
        context.fill(x + 2, y, x + 5, y + 1, DetectiveFolderPaint.INK);
        context.fill(x + 2, y + 6, x + 5, y + 7, DetectiveFolderPaint.INK);
        context.fill(x, y + 2, x + 1, y + 5, DetectiveFolderPaint.INK);
        context.fill(x + 6, y + 2, x + 7, y + 5, DetectiveFolderPaint.INK);
        context.fill(x + 1, y + 1, x + 2, y + 2, DetectiveFolderPaint.INK);
        context.fill(x + 5, y + 1, x + 6, y + 2, DetectiveFolderPaint.INK);
        context.fill(x + 1, y + 5, x + 2, y + 6, DetectiveFolderPaint.INK);
        context.fill(x + 5, y + 5, x + 6, y + 6, DetectiveFolderPaint.INK);
        context.fill(x + 2, y + 2, x + 3, y + 3, 0xFFFFFFFF);
        for (int step = 0; step < 3; step++) {
            context.fill(x + 6 + step, y + 6 + step, x + 8 + step, y + 7 + step, COLOR_HANDLE);
        }
    }

    private void drawBoard(DrawContext context, DetectiveCasePlayerComponent folder, @Nullable DetectiveCase shown,
                           int mouseX, int mouseY) {
        DetectiveFolderLayout.Rect board = layout.board();
        if (shown == null) {
            drawCenteredNote(context, Text.translatable("screen.sparkstrength.detective_folder.no_case"), -3.0F);
            return;
        }
        if (roster.isEmpty()) {
            drawCenteredNote(context, Text.translatable(KEY + "empty"), 2.0F);
            return;
        }
        clampScroll();
        boolean canAsk = canAsk(folder, shown);
        Card hovered = hoveredCard(mouseX, mouseY);
        context.enableScissor(board.x(), board.y(), board.right(), board.bottom());
        for (Card card : cards()) {
            int y = board.y() + card.y() - (int) Math.round(scroll);
            if (y + CARD_HEIGHT + 2 < board.y() || y - 3 > board.bottom()) {
                continue;
            }
            drawCard(context, card.entry(), card.x(), y, cardState(shown, card.entry().uuid()), canAsk,
                    card == hovered);
        }
        context.disableScissor();

        int contentHeight = contentHeight();
        if (contentHeight > board.height()) {
            int trackX = board.right() + 2;
            context.fill(trackX, board.y(), trackX + SCROLLBAR_WIDTH, board.bottom(), COLOR_SCROLL_TRACK);
            int thumbHeight = Math.max(8, board.height() * board.height() / contentHeight);
            int maxScroll = contentHeight - board.height();
            int thumbY = board.y() + (int) Math.round((board.height() - thumbHeight) * (scroll / Math.max(1, maxScroll)));
            context.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, COLOR_SCROLL_THUMB);
        }
    }

    /**
     * One pinned polaroid: head, name, and a tag for what the folder already knows (red distance for a recorded clue,
     * grey "absent" for a "not present" answer). Cards that cannot be asked right now are shaded.
     * 一张钉住的拍立得：头像、名字，以及文件夹已知信息的标签（已记录线索为红色距离，“不在场”为灰色标签）。
     * 此刻无法提问的卡片会加上阴影。
     */
    private void drawCard(DrawContext context, RosterEntry entry, int x, int y, CardState state, boolean canAsk,
                          boolean hovered) {
        boolean live = canAsk && state.askable();
        boolean highlighted = live && hovered;
        boolean pending = entry.uuid().equals(pendingTarget);
        context.fill(x + 1, y + 1, x + CARD_WIDTH + 1, y + CARD_HEIGHT + 1, COLOR_CARD_SHADOW);
        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, highlighted ? COLOR_CARD_HOVER : COLOR_CARD);
        int edge = highlighted || pending ? COLOR_CARD_HOVER_EDGE : COLOR_CARD_EDGE;
        if (pending) {
            // Pulse while the server answers. / 等待服务端回应时闪烁。
            edge = DetectiveFolderPaint.withAlpha(edge, (ageTicks / 4) % 2 == 0 ? 0xFF : 0x88);
        }
        context.drawBorder(x, y, CARD_WIDTH, CARD_HEIGHT, edge);

        int photoX = x + (CARD_WIDTH - PHOTO_SIZE) / 2;
        int photoY = y + PHOTO_TOP;
        DetectiveFolderPaint.drawPhotoBackground(context, photoX, photoY, PHOTO_SIZE);
        DetectiveFolderPaint.drawHead(context, entry.uuid(), photoX, photoY, PHOTO_SIZE);
        if (state.absent()) {
            context.fill(photoX, photoY, photoX + PHOTO_SIZE, photoY + PHOTO_SIZE, COLOR_PHOTO_ABSENT);
        }

        String visible = DetectiveFolderPaint.trim(textRenderer, entry.name(), CARD_WIDTH - 6);
        int nameY = photoY + PHOTO_SIZE + 3;
        context.drawText(textRenderer, visible, x + (CARD_WIDTH - textRenderer.getWidth(visible)) / 2, nameY,
                DetectiveFolderPaint.INK, false);

        // The tag sits across the lower half of the photo, clear of the name. / 标签横跨照片下半部分，不压住名字。
        int tagY = photoY + PHOTO_SIZE - TAG_HEIGHT - 2;
        if (state.recordedDistance() != null) {
            String tag = Text.translatable("screen.sparkstrength.detective_folder.distance", state.recordedDistance()).getString();
            drawTag(context, tag, x, tagY, COLOR_TAG_RECORDED);
        } else if (state.absent()) {
            drawTag(context, Text.translatable(KEY + "absent").getString(), x, tagY, COLOR_TAG_ABSENT);
        }
        if (!live && !state.decided() && !pending) {
            context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, COLOR_CARD_DISABLED);
        }

        int pinColor = state.recordedDistance() != null ? COLOR_PIN_RECORDED : state.absent() ? COLOR_PIN_ABSENT : COLOR_PIN;
        drawPin(context, x + CARD_WIDTH / 2 - 2, y - 2, pinColor);

        if (hovered) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.literal(entry.name()));
            if (state.recordedDistance() != null) {
                lines.add(Text.translatable(KEY + "tooltip_recorded", state.recordedDistance()).formatted(Formatting.GRAY));
            } else if (state.absent()) {
                lines.add(Text.translatable(KEY + "tooltip_absent").formatted(Formatting.GRAY));
            }
            if (lines.size() > 1 || !visible.equals(entry.name())) {
                hoverTooltip = lines;
            }
        }
    }

    /**
     * Small label centred on the card, trimmed to stay inside it. / 卡片居中的小标签，截短以保持在卡片内。
     */
    private void drawTag(DrawContext context, String text, int cardX, int y, int color) {
        String visible = DetectiveFolderPaint.trim(textRenderer, text, CARD_WIDTH - 8);
        int width = textRenderer.getWidth(visible) + 4;
        int x = cardX + (CARD_WIDTH - width) / 2;
        context.fill(x + 1, y + 1, x + width + 1, y + TAG_HEIGHT + 1, COLOR_CARD_SHADOW);
        context.fill(x, y, x + width, y + TAG_HEIGHT, color);
        context.drawText(textRenderer, visible, x + 2, y + 1, COLOR_TAG_TEXT, false);
    }

    /** 4x4 round pin head with a shine pixel. / 带高光像素的 4x4 圆形图钉。 */
    private static void drawPin(DrawContext context, int x, int y, int color) {
        context.fill(x + 1, y, x + 3, y + 4, color);
        context.fill(x, y + 1, x + 4, y + 3, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, COLOR_PIN_SHINE);
    }

    private void drawCenteredNote(DrawContext context, Text note, float tilt) {
        DetectiveFolderLayout.Rect board = layout.board();
        int noteWidth = stickyNoteWidths.get(textRenderer, note, Math.min(150, Math.max(40, board.width() - 40)), 4);
        int noteHeight = DetectiveFolderPaint.stickyNoteHeight(textRenderer, note, noteWidth, 4);
        int noteX = board.x() + (board.width() - noteWidth) / 2;
        int noteY = board.y() + (board.height() - noteHeight) / 2;
        DetectiveFolderPaint.rotated(context, noteX + noteWidth / 2.0F, noteY + noteHeight / 2.0F, tilt,
                () -> DetectiveFolderPaint.drawStickyNote(context, textRenderer, note, noteX, noteY, noteWidth, 4));
    }

    private void drawFooter(DrawContext context, ClientPlayerEntity player, DetectiveCasePlayerComponent folder,
                            @Nullable DetectiveCase shown) {
        DetectiveFolderLayout.Rect footer = layout.footer();
        context.fill(footer.x() + 2, footer.y() + 2, footer.right() + 2, footer.bottom() + 2, COLOR_CARD_SHADOW);
        context.fill(footer.x(), footer.y(), footer.right(), footer.bottom(), COLOR_PAPER);
        context.drawBorder(footer.x(), footer.y(), footer.width(), footer.height(), COLOR_PAPER_EDGE);
        Text line = footerText(player, folder, shown);
        String visible = DetectiveFolderPaint.trim(textRenderer, line.getString(), footer.width() - 10);
        int color = resultLine != null && line == resultLine ? DetectiveFolderPaint.INK : DetectiveFolderPaint.INK_MUTED;
        context.drawText(textRenderer, visible, footer.x() + (footer.width() - textRenderer.getWidth(visible)) / 2,
                footer.y() + (footer.height() - textRenderer.fontHeight) / 2 + 1, color, false);
    }

    /**
     * Footer priority: waiting for an answer, then the latest answer for the shown case, then why nobody can be asked,
     * then the hint.
     * 底部文字优先级：等待回应 > 当前命案的最近回应 > 无法提问的原因 > 操作提示。
     */
    private Text footerText(ClientPlayerEntity player, DetectiveCasePlayerComponent folder, @Nullable DetectiveCase shown) {
        if (shown == null) {
            return Text.translatable(KEY + "hint_no_case");
        }
        if (pendingTarget != null) {
            return Text.translatable(KEY + "pending", PlayerNameResolver.playerName(pendingTarget));
        }
        if (resultLine != null && shown.getCaseId().equals(resultCase)) {
            return resultLine;
        }
        if (shown.getSuspects().size() >= DetectiveCaseRules.clampSuspectLimit(folder.getSyncedSuspectLimit())) {
            return Text.translatable("message.sparkstrength.detective.enough_clues");
        }
        if (ItemCooldownSync.remainingTicks(player.getItemCooldownManager(), SparkStrengthItems.magnifier()) > 0) {
            return Text.translatable(KEY + "cooling");
        }
        return Text.translatable(KEY + "hint");
    }

    private String trimStyled(String value, Style style, int width) {
        if (textRenderer.getWidth(StringVisitable.styled(value, style)) <= width) {
            return value;
        }
        String ellipsis = "…";
        int budget = Math.max(0, width - textRenderer.getWidth(StringVisitable.styled(ellipsis, style)));
        return textRenderer.trimToWidth(StringVisitable.styled(value, style), budget).getString() + ellipsis;
    }

    // ---- State / 状态 ----

    private void onResult(DetectiveInterrogationResultS2CPacket result) {
        if (result.playerUuid().equals(pendingTarget)) {
            pendingTarget = null;
        }
        Text name = Text.literal(PlayerNameResolver.playerName(result.playerUuid()));
        resultCase = result.caseId();
        resultLine = switch (result.outcome()) {
            case NO_CASE -> Text.translatable("message.sparkstrength.detective.no_case");
            case LIMIT_REACHED -> Text.translatable("message.sparkstrength.detective.enough_clues");
            case ALREADY_RECORDED -> Text.translatable("message.sparkstrength.detective.already_recorded");
            case NOT_PRESENT -> Text.translatable(KEY + "result_absent", name);
            case RECORDED -> Text.translatable(KEY + "result_clue", name, result.distanceBlocks());
        };
        playSound(result.outcome() == InvestigationOutcome.RECORDED
                ? SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT
                : SoundEvents.ITEM_BOOK_PAGE_TURN);
    }

    private @Nullable DetectiveCasePlayerComponent folder() {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player == null ? null : DetectiveCasePlayerComponent.KEY.get(player);
    }

    /** The case the magnifier works on, as far as this client knows. / 本客户端所知的放大镜当前调查命案。 */
    private @Nullable DetectiveCase shownCase(DetectiveCasePlayerComponent folder) {
        DetectiveCase pending = pendingSelection == null ? null : folder.findCase(pendingSelection);
        return pending != null ? pending : folder.getSelectedCase();
    }

    /**
     * Asking needs a confirmed case with room for another clue, a ready magnifier and no question in flight.
     * 提问需要：已确认的命案且线索未满、放大镜不在冷却、没有尚未回应的提问。
     */
    private boolean canAsk(DetectiveCasePlayerComponent folder, DetectiveCase shown) {
        ClientPlayerEntity player = client == null ? null : client.player;
        return player != null
                && pendingSelection == null
                && pendingTarget == null
                && shown.getSuspects().size() < DetectiveCaseRules.clampSuspectLimit(folder.getSyncedSuspectLimit())
                && ItemCooldownSync.remainingTicks(player.getItemCooldownManager(), SparkStrengthItems.magnifier()) <= 0;
    }

    /**
     * What the shown case already says about {@code uuid}: remote clues carry the picked uuid as their display uuid,
     * and "not present" answers are kept by the case.
     * 当前命案对该玩家已有的结论：远程线索的显示 UUID 就是所选的 UUID，“不在场”回应由命案保存。
     */
    private static CardState cardState(DetectiveCase shown, UUID uuid) {
        for (SuspectClue clue : shown.getSuspects()) {
            if (!clue.anonymous() && uuid.equals(clue.displayUuid())) {
                return new CardState(clue.distanceBlocks(), false);
            }
        }
        return new CardState(null, shown.getAbsent().contains(uuid));
    }

    private void switchCase(DetectiveCasePlayerComponent folder, DetectiveCase shown, int direction) {
        List<DetectiveCase> cases = folder.getCases();
        int index = cases.indexOf(shown);
        if (index < 0 || cases.size() < 2) {
            return;
        }
        UUID next = cases.get(Math.floorMod(index + direction, cases.size())).getCaseId();
        if (send(new SelectDetectiveCaseC2SPacket(next))) {
            pendingSelection = next;
            pendingSelectionTicks = SELECTION_TIMEOUT_TICKS;
            resultLine = null;
        }
        playSound(SoundEvents.ITEM_BOOK_PAGE_TURN);
    }

    /**
     * Every uuid in the client's round profile map except the local player, sorted by name (case-insensitive), with
     * names resolved once.
     * 客户端本局玩家档案表中除本人外的所有 UUID，按名字排序（不区分大小写），名字只解析一次。
     */
    private void rebuildRoster() {
        ClientPlayerEntity self = client == null ? null : client.player;
        if (self == null) {
            roster = List.of();
            rosterSourceSize = -1;
            return;
        }
        Map<UUID, ?> profiles = GameWorldComponent.KEY.get(self.getWorld()).getGameProfiles();
        List<RosterEntry> result = new ArrayList<>();
        for (UUID uuid : new ArrayList<>(profiles.keySet())) {
            if (uuid != null && !uuid.equals(self.getUuid())) {
                result.add(new RosterEntry(uuid, PlayerNameResolver.playerName(uuid)));
            }
        }
        result.sort(Comparator.comparing(RosterEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(RosterEntry::uuid));
        roster = List.copyOf(result);
        rosterSourceSize = profiles.size();
    }

    private static int participantCount(ClientPlayerEntity player) {
        return GameWorldComponent.KEY.get(player.getWorld()).getGameProfiles().size();
    }

    private List<Card> cards() {
        DetectiveFolderLayout.Rect board = layout.board();
        int columns = layout.columns();
        int gridWidth = columns * CARD_WIDTH + (columns - 1) * CARD_GAP;
        int left = board.x() + Math.max(0, (board.width() - gridWidth) / 2);
        List<Card> cards = new ArrayList<>(roster.size());
        for (int index = 0; index < roster.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            cards.add(new Card(roster.get(index), left + column * (CARD_WIDTH + CARD_GAP),
                    CONTENT_TOP_PAD + row * (CARD_HEIGHT + CARD_GAP)));
        }
        return cards;
    }

    private int contentHeight() {
        if (roster.isEmpty()) {
            return 0;
        }
        int rows = (roster.size() + layout.columns() - 1) / layout.columns();
        return CONTENT_TOP_PAD + rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP + CONTENT_BOTTOM_PAD;
    }

    private @Nullable Card hoveredCard(double mouseX, double mouseY) {
        DetectiveFolderLayout.Rect board = layout.board();
        if (!board.contains(mouseX, mouseY)) {
            return null;
        }
        double contentY = mouseY - board.y() + scroll;
        for (Card card : cards()) {
            if (mouseX >= card.x() && mouseX < card.x() + CARD_WIDTH
                    && contentY >= card.y() && contentY < card.y() + CARD_HEIGHT) {
                return card;
            }
        }
        return null;
    }

    private void clampScroll() {
        double maxScroll = Math.max(0, contentHeight() - layout.board().height());
        scroll = Math.max(0.0D, Math.min(maxScroll, scroll));
    }

    private static boolean canKeepOpen(ClientPlayerEntity player) {
        // Wathe's alive check ignores spectator mode (e.g. Taotie-swallowed); the server rejects spectators' requests.
        // Wathe 的存活判断不排除旁观模式（如被饕餮吞下）；服务端会拒绝旁观者的请求。
        if (!GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()) {
            return false;
        }
        Item magnifier = SparkStrengthItems.magnifier();
        return DetectiveRules.isDetective(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && (player.getMainHandStack().isOf(magnifier) || player.getOffHandStack().isOf(magnifier));
    }

    /** Sends only when the server registered the channel, so an older server never disconnects us. / 服务端注册了通道才发送。 */
    private static boolean send(CustomPayload payload) {
        if (!ClientPlayNetworking.canSend(payload.getId())) {
            return false;
        }
        ClientPlayNetworking.send(payload);
        return true;
    }

    private void playSound(SoundEvent sound) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
        }
    }

    /**
     * Pixel layout: a 320x200 board, centred and clamped to the viewport; the board area fits as many card columns
     * as its width allows and scrolls vertically.
     * 像素布局：320x200 的证物板，居中并限制在视口内；卡片区按宽度放下尽可能多的列，纵向滚动。
     */
    private record Layout(DetectiveFolderLayout.Rect frame, DetectiveFolderLayout.Rect cork,
                          DetectiveFolderLayout.Rect header, DetectiveFolderLayout.Rect board,
                          DetectiveFolderLayout.Rect footer, int columns) {
        static Layout forViewport(int screenWidth, int screenHeight) {
            int width = Math.max(0, Math.min(PREFERRED_WIDTH, screenWidth));
            int height = Math.max(0, Math.min(PREFERRED_HEIGHT, screenHeight));
            int x = (screenWidth - width) / 2;
            int y = (screenHeight - height) / 2;
            DetectiveFolderLayout.Rect frame = new DetectiveFolderLayout.Rect(x, y, width, height);
            DetectiveFolderLayout.Rect cork = new DetectiveFolderLayout.Rect(
                    x + FRAME, y + FRAME, width - FRAME * 2, height - FRAME * 2);
            DetectiveFolderLayout.Rect header = new DetectiveFolderLayout.Rect(
                    cork.x() + INSET, cork.y() + INSET, cork.width() - INSET * 2, HEADER_HEIGHT);
            DetectiveFolderLayout.Rect footer = new DetectiveFolderLayout.Rect(
                    cork.x() + INSET, cork.bottom() - INSET - FOOTER_HEIGHT, cork.width() - INSET * 2, FOOTER_HEIGHT);
            int boardTop = header.bottom() + BOARD_GAP;
            DetectiveFolderLayout.Rect board = new DetectiveFolderLayout.Rect(
                    cork.x() + INSET, boardTop, cork.width() - INSET * 2 - SCROLLBAR_ROOM, footer.y() - BOARD_GAP - boardTop);
            int columns = Math.max(1, (board.width() + CARD_GAP) / (CARD_WIDTH + CARD_GAP));
            return new Layout(frame, cork, header, board, footer, columns);
        }
    }

    private record RosterEntry(UUID uuid, String name) {
    }

    /** One roster card; {@code y} is in scrolled content space. / 名单卡片；y 为滚动内容坐标。 */
    private record Card(RosterEntry entry, int x, int y) {
    }

    private record HeaderGeometry(DetectiveFolderLayout.Rect previous, DetectiveFolderLayout.Rect next, int caseTextX,
                                  String victimName) {
    }

    /**
     * What the shown case already knows about one participant. A decided card (recorded or absent) is not asked
     * again: the server would only repeat the stored answer.
     * 当前命案对某名参与者已有的结论。已有结论的卡片（已记录或不在场）不再提问：服务端只会重复已保存的回应。
     */
    private record CardState(@Nullable Integer recordedDistance, boolean absent) {
        boolean decided() {
            return recordedDistance != null || absent;
        }

        boolean askable() {
            return !decided();
        }
    }
}
