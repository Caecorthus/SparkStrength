package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.OpenBreakerRepairS2CPacket;
import annina.sparkstrength.sabotage.SabotageRules;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Circuit breaker repair panel: flip every tripped switch back up, then hold the main lever until the voltage needle
 * settles in the green. The server only accepts the result after a minimum time and re-checks the session.
 * 电闸维修面板：把每个跳闸的空开拨回上方，再拉住主闸直到电压指针稳定在绿区。服务端只在最短用时之后才接受结果，
 * 并重新校验会话。
 */
final class BreakerRepairScreen extends AbstractRepairScreen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 176;
    private static final int SWITCH_W = 26;
    private static final int SWITCH_H = 40;
    private static final int SWITCH_GAP = 8;
    private static final int LEVER_W = 40;
    private static final int LEVER_H = 104;

    private final boolean[] tripped = new boolean[SabotageRules.REPAIR_SWITCH_SLOTS];
    private final float[] flip = new float[SabotageRules.REPAIR_SWITCH_SLOTS];
    private boolean holding;

    BreakerRepairScreen(OpenBreakerRepairS2CPacket packet) {
        super(Text.translatable("screen.sparkstrength.breaker.title"), packet.sessionId(), packet.holdTicks(),
                SabotageRules.repairMinTicks(packet.switches(), Math.max(1, packet.holdTicks())));
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < tripped.length; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, new Random(packet.seed()));
        int count = Math.min(tripped.length, Math.max(1, packet.switches()));
        for (int i = 0; i < count; i++) {
            tripped[slots.get(i)] = true;
        }
        for (int i = 0; i < flip.length; i++) {
            flip[i] = tripped[i] ? 0.0F : 1.0F;
        }
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - PANEL_H) / 2;
    }

    private int switchX(int index) {
        return left() + 16 + (index % 4) * (SWITCH_W + SWITCH_GAP);
    }

    private int switchY(int index) {
        return top() + 44 + (index / 4) * (SWITCH_H + 14);
    }

    private int leverX() {
        return left() + PANEL_W - 24 - LEVER_W - 40;
    }

    private int leverY() {
        return top() + 44;
    }

    private boolean allReset() {
        for (boolean value : tripped) {
            if (value) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void tickPanel() {
        for (int i = 0; i < flip.length; i++) {
            float target = tripped[i] ? 0.0F : 1.0F;
            flip[i] += (target - flip[i]) * 0.5F;
        }
    }

    @Override
    protected void tickRepair() {
        stepCharge(holding && allReset());
        // Full but faster than the server's minimum: hold the needle until that time has passed (the server starts its
        // clock before this screen opens, so waiting here is always enough).
        // 已拉满但快于服务端最短用时：指针保持在顶，直到时间够了（服务端的计时早于界面打开，因此在这里等就足够）。
        if (charged() && submitDue()) {
            holding = false;
            submit(Text.translatable("screen.sparkstrength.breaker.restoring"));
            playClick(SparkStrengthSounds.CIRCUIT_BREAKER_SWITCH, 0.7F);
        }
    }

    @Override
    protected void onReplyTimeout() {
        // The completion never arrived; the server session is still open, so start the lever again.
        // 完成包没有送达；服务端会话仍然有效，重新拉闸即可。
        banner = Text.translatable("screen.sparkstrength.breaker.retry");
        bannerColor = TabletTheme.DANGER;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !submitted() && !closing()) {
            for (int i = 0; i < tripped.length; i++) {
                if (tripped[i] && inside(mouseX, mouseY, switchX(i), switchY(i), SWITCH_W, SWITCH_H)) {
                    tripped[i] = false;
                    playClick(SparkStrengthSounds.CIRCUIT_BREAKER_SWITCH, 1.0F + 0.1F * (i % 4));
                    return true;
                }
            }
            if (inside(mouseX, mouseY, leverX() - 6, leverY() - 6, LEVER_W + 12, LEVER_H + 12)) {
                if (allReset()) {
                    holding = true;
                } else {
                    playClick(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6F);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void releaseHolds() {
        holding = false;
    }

    @Override
    protected void closedByServer(int reason) {
        switch (reason) {
            case CloseBreakerRepairS2CPacket.REASON_REPAIRED -> {
                banner = Text.translatable("screen.sparkstrength.breaker.repaired");
                bannerColor = TabletTheme.SUCCESS;
            }
            case CloseBreakerRepairS2CPacket.REASON_POWER_BACK -> {
                banner = Text.translatable("screen.sparkstrength.breaker.power_back");
                bannerColor = TabletTheme.SUCCESS;
            }
            default -> {
                banner = Text.translatable("screen.sparkstrength.breaker.interrupted");
                bannerColor = TabletTheme.DANGER;
            }
        }
    }

    @Override
    protected void drawScreen(TabletCanvas canvas, int mouseX, int mouseY, float delta) {
        int x = left();
        int y = top();
        canvas.shadow(x, y, PANEL_W, PANEL_H, 10, 14, 0x90000000);
        canvas.roundRectGradientV(x, y, PANEL_W, PANEL_H, 10, 0xFF262A31, 0xFF16191E);
        canvas.roundRectOutline(x, y, PANEL_W, PANEL_H, 10, 1.0F, 0xFF3D434D);
        // Hazard strip along the top edge. / 顶部的警示条。
        canvas.pushClip(x, y, PANEL_W, 6);
        for (int i = -1; i < PANEL_W / 10 + 2; i++) {
            float sx = x + i * 10;
            canvas.polygon(new float[]{sx, y + 6, sx + 5, y, sx + 10, y, sx + 5, y + 6}, 0xFFE0A526);
        }
        canvas.popClip();
        canvas.text(textRenderer, "⚡ " + title.getString(), x + 14, y + 13, TabletTheme.TEXT);
        String hint = Text.translatable(allReset()
                ? "screen.sparkstrength.breaker.hint_lever"
                : "screen.sparkstrength.breaker.hint_switches").getString();
        canvas.text(textRenderer, TabletCanvas.trim(textRenderer, hint, PANEL_W - 28), x + 14, y + 26,
                TabletTheme.TEXT_2);

        for (int i = 0; i < tripped.length; i++) {
            drawSwitch(canvas, i, mouseX, mouseY);
        }
        drawLever(canvas, delta, mouseX, mouseY);
        drawGauge(canvas, delta);

        if (banner != null) {
            int bw = textRenderer.getWidth(banner) + 20;
            float bx = x + (PANEL_W - bw) / 2.0F;
            float by = y + PANEL_H - 24;
            canvas.roundRect(bx, by, bw, 16, 8, TabletTheme.withAlpha(bannerColor, 0x33));
            canvas.roundRectOutline(bx, by, bw, 16, 8, 1.0F, bannerColor);
            canvas.textCentered(textRenderer, banner.getString(), x + PANEL_W / 2, (int) by + 4, bannerColor);
        }
    }

    private void drawSwitch(TabletCanvas canvas, int index, int mouseX, int mouseY) {
        int sx = switchX(index);
        int sy = switchY(index);
        boolean down = tripped[index];
        boolean hover = down && inside(mouseX, mouseY, sx, sy, SWITCH_W, SWITCH_H);
        canvas.roundRect(sx, sy, SWITCH_W, SWITCH_H, 4, hover ? 0xFF2E333B : 0xFF22262D);
        canvas.roundRectOutline(sx, sy, SWITCH_W, SWITCH_H, 4, 1.0F, down ? 0xFF7A2A2C : 0xFF3A4048);
        // LED. / 指示灯。
        int led = down ? TabletTheme.DANGER : TabletTheme.SUCCESS;
        canvas.glow(sx + SWITCH_W / 2.0F, sy + 7, 6, TabletTheme.withAlpha(led, 0x55));
        canvas.circle(sx + SWITCH_W / 2.0F, sy + 7, 2.5F, led);
        // Slot and toggle. / 拨杆槽与拨杆。
        float slotX = sx + SWITCH_W / 2.0F - 3;
        float slotY = sy + 14;
        float slotH = SWITCH_H - 20;
        canvas.roundRect(slotX, slotY, 6, slotH, 3, 0xFF0D0F12);
        float knobY = slotY + (1.0F - flip[index]) * (slotH - 9);
        canvas.roundRect(sx + 5, knobY, SWITCH_W - 10, 9, 2.5F, down ? 0xFFD64545 : 0xFFD9D4C7);
        canvas.textCentered(textRenderer, down ? "OFF" : "ON", sx + SWITCH_W / 2, sy + SWITCH_H + 2,
                down ? TabletTheme.DANGER : TabletTheme.TEXT_3);
    }

    private void drawLever(TabletCanvas canvas, float delta, int mouseX, int mouseY) {
        int lx = leverX();
        int ly = leverY();
        boolean ready = allReset() && !submitted();
        float progress = chargeProgress(delta);
        canvas.roundRect(lx, ly, LEVER_W, LEVER_H, 6, 0xFF1C1F24);
        canvas.roundRectOutline(lx, ly, LEVER_W, LEVER_H, 6, 1.0F,
                ready ? (holding ? 0xFFF5C451 : 0xFFE0A526) : 0xFF3A4048);
        float trackX = lx + LEVER_W / 2.0F - 3;
        canvas.roundRect(trackX, ly + 10, 6, LEVER_H - 20, 3, 0xFF0D0F12);
        float handleY = ly + LEVER_H - 26 - progress * (LEVER_H - 36);
        canvas.roundRect(lx + 6, handleY, LEVER_W - 12, 16, 4, ready ? 0xFFC43B3B : 0xFF5A3030);
        canvas.roundRect(lx + 6, handleY, LEVER_W - 12, 5, 4, TabletTheme.withAlpha(0xFFFFFFFF, 0x30));
        String label = Text.translatable(ready
                ? "screen.sparkstrength.breaker.lever_hold"
                : "screen.sparkstrength.breaker.lever_locked").getString();
        canvas.textCentered(textRenderer, label, lx + LEVER_W / 2, ly + LEVER_H + 4,
                ready ? 0xFFE0A526 : TabletTheme.TEXT_3);
    }

    private void drawGauge(TabletCanvas canvas, float delta) {
        float cx = leverX() + LEVER_W + 22;
        int gy = leverY();
        int gh = LEVER_H;
        float progress = chargeProgress(delta);
        canvas.roundRect(cx - 6, gy, 12, gh, 6, 0xFF0D0F12);
        float green = gh * 0.2F;
        canvas.roundRect(cx - 6, gy, 12, green, 6, TabletTheme.withAlpha(TabletTheme.SUCCESS, 0x55));
        float fill = gh * Math.min(1.0F, progress);
        if (fill > 1.0F) {
            int color = progress >= 0.8F ? TabletTheme.SUCCESS : 0xFFE0A526;
            canvas.roundRect(cx - 4, gy + gh - fill + 2, 8, Math.max(0, fill - 4), 4, color);
        }
        canvas.textCentered(textRenderer, "V", (int) cx, gy + gh + 4, TabletTheme.TEXT_3);
    }

    private void playClick(net.minecraft.sound.SoundEvent sound, float pitch) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
        }
    }
}
