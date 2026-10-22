package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.partyanimal.PartygoerSummonC2SPacket;
import annina.sparkstrength.role.partyanimal.PartyAnimalRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Room picker for the Partygoer: where it appears (left) and where it carries its prey (right), then summon. Purely a
 * picker: the server re-checks every gate and the cost when the summon arrives.
 * 派对客选房界面：左边选出生房间，右边选扛人终点，然后召唤。仅负责选择：召唤请求到达时服务端会重新检查所有门槛与费用。
 */
final class PartygoerRoomScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PAD = 12;
    private static final int HEADER_H = 38;
    private static final int COLUMN_GAP = 10;
    private static final int ROW_H = 18;
    private static final int ROW_GAP = 3;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int FOOTER_H = 36;
    private static final int BUTTON_H = 20;
    private static final int PARTY = 0xFFFF5AC8;
    private static final int PARTY_SOFT = 0xFFFFB3E0;
    private static final int DESTINATION = 0xFFFFD84A;

    private final List<String> rooms;
    private int spawn = -1;
    private int destination = -1;
    private int scroll;

    PartygoerRoomScreen(List<String> rooms) {
        super(Text.translatable("screen.sparkstrength.partygoer.title"));
        this.rooms = List.copyOf(rooms);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int visibleRows() {
        return Math.min(MAX_VISIBLE_ROWS, rooms.size());
    }

    private int listHeight() {
        return visibleRows() * (ROW_H + ROW_GAP) - ROW_GAP;
    }

    private int panelHeight() {
        return HEADER_H + 14 + listHeight() + FOOTER_H + PAD;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int columnWidth() {
        return (PANEL_W - PAD * 2 - COLUMN_GAP) / 2;
    }

    private int columnX(int column) {
        return left() + PAD + column * (columnWidth() + COLUMN_GAP);
    }

    private int listTop() {
        return top() + HEADER_H + 14;
    }

    private int buttonY() {
        return listTop() + listHeight() + (FOOTER_H - BUTTON_H) / 2 + 2;
    }

    private int confirmX() {
        return left() + PANEL_W - PAD - confirmWidth();
    }

    private int confirmWidth() {
        return 120;
    }

    private int cancelX() {
        return confirmX() - 8 - 64;
    }

    private boolean ready() {
        return spawn >= 0 && destination >= 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int scale = Math.max(1, (int) Math.round(client.getWindow().getScaleFactor()));
        TabletCanvas canvas = new TabletCanvas(context, scale, client.getWindow().getFramebufferHeight());
        // One draw batch for the panel (the list clips still flush). / 整个面板一个绘制批次（列表裁剪仍会提交）。
        canvas.batch(() -> drawScreen(canvas, mouseX, mouseY));
    }

    private void drawScreen(TabletCanvas canvas, int mouseX, int mouseY) {
        int x = left();
        int y = top();
        int h = panelHeight();
        canvas.shadow(x, y, PANEL_W, h, 10, 12, 0x99000000);
        canvas.roundRectGradientV(x, y, PANEL_W, h, 10, TabletTheme.SCREEN_TOP, TabletTheme.SCREEN_BOTTOM);
        canvas.roundRectOutline(x, y, PANEL_W, h, 10, 1.0F, TabletTheme.withAlpha(PARTY, 0x90));
        canvas.glow(x + PANEL_W / 2.0F, y + 8, 70, TabletTheme.withAlpha(PARTY, 0x30));

        canvas.text(textRenderer, title.getString(), x + PAD, y + 10, PARTY_SOFT);
        String cost = Text.translatable("screen.sparkstrength.partygoer.cost", PartyAnimalRules.SKILL_PRICE).getString();
        canvas.textRight(textRenderer, cost, x + PANEL_W - PAD, y + 10, TabletTheme.WARNING);
        canvas.text(textRenderer, TabletCanvas.trim(textRenderer,
                Text.translatable("screen.sparkstrength.partygoer.hint").getString(), PANEL_W - PAD * 2), x + PAD, y + 23,
                TabletTheme.TEXT_3);

        drawColumn(canvas, 0, mouseX, mouseY);
        drawColumn(canvas, 1, mouseX, mouseY);

        drawButton(canvas, cancelX(), buttonY(), 64, Text.translatable("gui.cancel").getString(),
                inside(mouseX, mouseY, cancelX(), buttonY(), 64, BUTTON_H), true, TabletTheme.NONE.base());
        drawButton(canvas, confirmX(), buttonY(), confirmWidth(),
                Text.translatable("screen.sparkstrength.partygoer.summon").getString(),
                inside(mouseX, mouseY, confirmX(), buttonY(), confirmWidth(), BUTTON_H), ready(), PARTY);
    }

    private void drawColumn(TabletCanvas canvas, int column, int mouseX, int mouseY) {
        int cx = columnX(column);
        int w = columnWidth();
        int accent = column == 0 ? PARTY : DESTINATION;
        String label = Text.translatable(column == 0 ? "screen.sparkstrength.partygoer.spawn"
                : "screen.sparkstrength.partygoer.destination").getString();
        canvas.circle(cx + 3, top() + HEADER_H + 4, 2.5F, accent);
        canvas.text(textRenderer, label, cx + 9, top() + HEADER_H, TabletTheme.TEXT_2);
        int selected = column == 0 ? spawn : destination;
        canvas.pushClip(cx, listTop(), w, listHeight());
        for (int row = 0; row < rooms.size(); row++) {
            int ry = listTop() + (row - scroll) * (ROW_H + ROW_GAP);
            if (ry + ROW_H < listTop() || ry > listTop() + listHeight()) {
                continue;
            }
            boolean picked = row == selected;
            boolean hovered = !picked && inside(mouseX, mouseY, cx, ry, w, ROW_H) && inList(mouseY);
            int fill = picked ? TabletTheme.withAlpha(accent, 0x55)
                    : hovered ? TabletTheme.SURFACE_HOVER : TabletTheme.SURFACE;
            canvas.roundRect(cx, ry, w, ROW_H, 4, fill);
            if (picked) {
                canvas.roundRectOutline(cx, ry, w, ROW_H, 4, 1.0F, accent);
            }
            String name = TabletCanvas.trim(textRenderer, rooms.get(row), w - 12);
            canvas.text(textRenderer, name, cx + 6, ry + 5, picked ? TabletTheme.WHITE : TabletTheme.TEXT);
        }
        canvas.popClip();
        if (rooms.size() > MAX_VISIBLE_ROWS) {
            float track = listHeight();
            float thumb = Math.max(12.0F, track * MAX_VISIBLE_ROWS / rooms.size());
            float travel = track - thumb;
            float at = travel * scroll / (float) (rooms.size() - MAX_VISIBLE_ROWS);
            canvas.roundRect(cx + w + 2, listTop() + at, 2, thumb, 1, TabletTheme.withAlpha(accent, 0x80));
        }
    }

    private void drawButton(TabletCanvas canvas, int x, int y, int w, String label, boolean hovered, boolean enabled,
                            int accent) {
        int fill = !enabled ? TabletTheme.LOCKED_BADGE
                : hovered ? TabletTheme.mix(accent, TabletTheme.WHITE, 0.15F) : accent;
        canvas.roundRect(x, y, w, BUTTON_H, 5, fill);
        canvas.textCentered(textRenderer, label, x + w / 2, y + 6, enabled ? TabletTheme.WHITE : TabletTheme.TEXT_3);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (int column = 0; column < 2; column++) {
                int cx = columnX(column);
                if (!inside(mouseX, mouseY, cx, listTop(), columnWidth(), listHeight())) {
                    continue;
                }
                int row = scroll + (int) ((mouseY - listTop()) / (ROW_H + ROW_GAP));
                if (row >= 0 && row < rooms.size()) {
                    if (column == 0) {
                        spawn = row;
                    } else {
                        destination = row;
                    }
                    click();
                    return true;
                }
            }
            if (inside(mouseX, mouseY, cancelX(), buttonY(), 64, BUTTON_H)) {
                click();
                close();
                return true;
            }
            if (ready() && inside(mouseX, mouseY, confirmX(), buttonY(), confirmWidth(), BUTTON_H)) {
                click();
                if (ClientPlayNetworking.canSend(PartygoerSummonC2SPacket.ID)) {
                    ClientPlayNetworking.send(new PartygoerSummonC2SPacket(rooms.get(spawn), rooms.get(destination)));
                }
                close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (rooms.size() > MAX_VISIBLE_ROWS) {
            scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, rooms.size() - MAX_VISIBLE_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean inList(double mouseY) {
        return mouseY >= listTop() && mouseY < listTop() + listHeight();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
