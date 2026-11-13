package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.sabotage.EngineCondition;
import annina.sparkstrength.sabotage.EngineRepairMode;
import annina.sparkstrength.sabotage.EngineRules;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import static annina.sparkstrength.client.sabotage.EngineRepairLayout.BUTTON_H;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.BUTTON_W;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.FUSE_H;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.FUSE_SLOTS;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.FUSE_W;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.KNOB_R;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.PAD;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.PANEL_W;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.TACH_R;
import static annina.sparkstrength.client.sabotage.EngineRepairGame.END_FAILURE;
import static annina.sparkstrength.client.sabotage.EngineRepairGame.END_NONE;
import static annina.sparkstrength.client.sabotage.EngineRepairGame.END_SUCCESS;

/**
 * Paints the engine panel from the mini-game state: the cabinet, header and lamps, fuses, pressure gauges and knobs,
 * the tachometer, the restart button and the outcome banner. Called inside the draw batch the base screen opens.
 * 根据小游戏状态绘制动力装置面板：机柜、标题栏与指示灯、保险丝、压力表与旋钮、转速表、重启按钮以及结果横幅。
 * 在基类界面打开的绘制批次内调用。
 */
final class EngineRepairPainter {
    private static final int CAPTION_Y = 44;

    /** Dial sweep: 0 at 8 o'clock, 1 at 4 o'clock. / 表盘量程：0 在 8 点方向，1 在 4 点方向。 */
    private static final float START_DEG = -120.0F;
    private static final float SWEEP_DEG = 240.0F;
    private static final float TACH_GREEN_LO = 0.70F;
    private static final float TACH_GREEN_HI = 0.90F;
    private static final float TACH_REDLINE = 0.92F;

    private static final int STEEL_TOP = 0xFF2B3036;
    private static final int STEEL_BOTTOM = 0xFF171A1E;
    private static final int STEEL_RIM = 0xFF4A515B;
    private static final int CHAR_TOP = 0xFF221F1C;
    private static final int CHAR_BOTTOM = 0xFF0E0D0C;
    private static final int CHAR_RIM = 0xFF3D352D;
    private static final int BRASS = 0xFFC9A04A;
    private static final int BRASS_DARK = 0xFF7C5F2A;
    private static final int BRASS_LIGHT = 0xFFF0D58E;
    private static final int BRASS_BURNT = 0xFF4A3B22;
    private static final int DIAL_FACE = 0xFF11161A;
    private static final int DIAL_FACE_CHAR = 0xFF0B0C0D;
    private static final int TICK = 0xFF8E98A3;
    private static final int NEEDLE = 0xFFF3EBDD;
    private static final int TACH_NEEDLE = 0xFFF2643C;
    private static final int GEAR = 0xFFF0A040;
    private static final int HAZARD = 0xFFE0A526;
    private static final int RUST = 0xFFA8432C;
    private static final int DEAD = 0xFF3A3D42;
    private static final int INK = 0xFF0D0F12;

    private final EngineRepairScreen screen;
    private final EngineRepairGame game;
    private final EngineRepairLayout layout;

    EngineRepairPainter(EngineRepairScreen screen, EngineRepairGame game, EngineRepairLayout layout) {
        this.screen = screen;
        this.game = game;
        this.layout = layout;
    }

    void paint(TabletCanvas canvas, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int x = layout.left();
        int y = layout.top();
        boolean scorched = game.scorched();
        float time = screen.openTicks() + delta;
        float lit = MathHelper.lerp(delta, game.prevPower, game.power);

        drawPanel(canvas, x, y, scorched);
        drawHeader(canvas, textRenderer, x, y, scorched, time);

        canvas.rect(x + 222, y + CAPTION_Y + 2, 1, layout.panelH - CAPTION_Y - (screen.repair ? 36 : 24),
                TabletTheme.HAIRLINE);
        if (screen.mode == EngineRepairMode.OVERHAUL) {
            boolean done = game.fusesDone();
            caption(canvas, textRenderer, "screen.sparkstrength.engine.fuses", game.fusesReplaced() + "/"
                    + game.burntTotal, x + PAD, y + CAPTION_Y, done ? TabletTheme.SUCCESS : TabletTheme.TEXT_2);
            int hovered = game.live() && !done ? layout.fuseAt(mouseX, mouseY) : -1;
            for (int s = 0; s < FUSE_SLOTS; s++) {
                drawFuse(canvas, s, delta, s == hovered && game.burnt[s]);
            }
        }
        int gaugeCaptionY = screen.mode == EngineRepairMode.OVERHAUL ? y + 79 : y + CAPTION_Y;
        int gaugeCaptionColor = !game.gaugesPowered() ? TabletTheme.TEXT_3
                : screen.repair && game.allSet() ? TabletTheme.SUCCESS : TabletTheme.TEXT_2;
        caption(canvas, textRenderer, "screen.sparkstrength.engine.gauges",
                screen.repair ? game.setCount() + "/" + screen.gaugeCount : null, x + PAD, gaugeCaptionY,
                gaugeCaptionColor);
        for (int i = 0; i < screen.gaugeCount; i++) {
            boolean hover = game.live() && game.gaugesPowered()
                    && (game.dragGauge == i || game.dragGauge < 0 && layout.overGauge(mouseX, mouseY, i));
            drawGauge(canvas, i, delta, time, lit, scorched, hover);
        }

        String rpm = Text.translatable("screen.sparkstrength.engine.tachometer").getString();
        canvas.textCentered(textRenderer, rpm, (int) layout.columnCx(), y + CAPTION_Y,
                game.engineCaught() ? TabletTheme.SUCCESS : TabletTheme.TEXT_2);
        drawTachometer(canvas, textRenderer, delta, scorched, lit);
        if (screen.repair) {
            drawRestartButton(canvas, textRenderer, delta, time, mouseX, mouseY);
        }

        Text banner = screen.banner;
        if (banner != null) {
            int bannerColor = screen.bannerColor;
            int bw = textRenderer.getWidth(banner) + 20;
            float bx = x + (PANEL_W - bw) / 2.0F;
            float by = y + layout.panelH - 24;
            canvas.roundRect(bx, by, bw, 16, 8, 0xE0101215);
            canvas.roundRect(bx, by, bw, 16, 8, TabletTheme.withAlpha(bannerColor, 0x33));
            canvas.roundRectOutline(bx, by, bw, 16, 8, 1.0F, bannerColor);
            canvas.textCentered(textRenderer, banner.getString(), x + PANEL_W / 2, (int) by + 4, bannerColor);
        }
    }

    private void drawPanel(TabletCanvas canvas, int x, int y, boolean scorched) {
        int panelH = layout.panelH;
        float[] soot = game.soot;
        float[] panelCrack = game.panelCrack;
        canvas.shadow(x, y, PANEL_W, panelH, 10, 14, 0x90000000);
        canvas.roundRectGradientV(x, y, PANEL_W, panelH, 10,
                scorched ? CHAR_TOP : STEEL_TOP, scorched ? CHAR_BOTTOM : STEEL_BOTTOM);
        if (scorched) {
            canvas.pushClip(x + 1, y + 1, PANEL_W - 2, panelH - 2);
            for (int i = 0; i < soot.length; i += 4) {
                canvas.glow(x + soot[i], y + soot[i + 1], soot[i + 2], ((int) soot[i + 3]) << 24);
            }
            canvas.glow(x + soot[0], y + soot[1], soot[2] * 0.6F, 0x22FF5A1E);
            float[] crack = new float[panelCrack.length];
            for (int i = 0; i < crack.length; i += 2) {
                crack[i] = x + panelCrack[i];
                crack[i + 1] = y + panelCrack[i + 1];
            }
            canvas.polyline(crack, 1.0F, 0xFF060606);
            canvas.popClip();
        }

        // Top strip: hazard tape on a damaged unit, rust on a wrecked one, plain brass on the status panel.
        // 顶部饰条：受损时是警示胶带，损毁时是锈红，状态面板是素黄铜。
        canvas.pushClip(x, y, PANEL_W, 5);
        if (screen.mode == EngineRepairMode.STATUS) {
            canvas.roundRect(x, y, PANEL_W, 20, 10, scorched ? BRASS_BURNT : BRASS_DARK);
        } else {
            canvas.roundRect(x, y, PANEL_W, 20, 10, 0xFF111214);
            int stripe = screen.mode == EngineRepairMode.OVERHAUL ? RUST : HAZARD;
            canvas.pushClip(x + 9, y, PANEL_W - 18, 5);
            for (int i = -1; i < PANEL_W / 10 + 2; i++) {
                float sx = x + i * 10;
                canvas.polygon(new float[]{sx, y + 5, sx + 5, y, sx + 10, y, sx + 5, y + 5}, stripe);
            }
            canvas.popClip();
        }
        canvas.popClip();
        canvas.roundRectOutline(x, y, PANEL_W, panelH, 10, 1.0F, scorched ? CHAR_RIM : STEEL_RIM);

        // Cabinet rivets. / 机柜铆钉。
        float[][] rivets = {{x + 7, y + 12}, {x + PANEL_W - 7, y + 12}, {x + 7, y + panelH - 7},
                {x + PANEL_W - 7, y + panelH - 7}};
        for (float[] rivet : rivets) {
            canvas.circle(rivet[0], rivet[1] + 0.5F, 1.8F, 0x80000000);
            canvas.circle(rivet[0], rivet[1], 1.6F, scorched ? 0xFF3A3632 : 0xFF6B737D);
            canvas.circle(rivet[0] - 0.4F, rivet[1] - 0.4F, 0.6F, 0x50FFFFFF);
        }
    }

    private void drawHeader(TabletCanvas canvas, TextRenderer textRenderer, int x, int y, boolean scorched,
                            float time) {
        drawGear(canvas, x + 20, y + 17, 5.5F, scorched ? 0xFFB8783A : GEAR, 0xFF101214, time);
        canvas.text(textRenderer, screen.getTitle().getString(), x + 30, y + 13, TabletTheme.TEXT);
        drawLamps(canvas, x + PANEL_W - 18, y + 17);

        String hint;
        int hintColor = TabletTheme.TEXT_2;
        if (!screen.repair) {
            // The condition ids are a saved contract (intact / damaged / wrecked), so they double as key suffixes.
            // condition 的 id 是保存契约（intact / damaged / wrecked），可直接作为键名后缀。
            String state = Text.translatable("screen.sparkstrength.engine.status."
                    + (game.condition == null ? "unknown" : game.condition.asString())).getString();
            hint = Text.translatable("screen.sparkstrength.engine.status", state).getString();
            hintColor = game.condition == EngineCondition.INTACT ? TabletTheme.SUCCESS
                    : game.condition == EngineCondition.DAMAGED ? TabletTheme.WARNING
                    : game.condition == EngineCondition.WRECKED ? TabletTheme.DANGER : TabletTheme.TEXT_3;
        } else if (game.endState != END_NONE && screen.banner != null) {
            // Closing: the step hints no longer apply, so repeat the outcome. / 关闭中：步骤提示已不适用，改为重复结果。
            hint = screen.banner.getString();
            hintColor = screen.bannerColor;
        } else if (game.restarted || screen.submitted()) {
            hint = Text.translatable("screen.sparkstrength.engine.hint_wait").getString();
        } else if (!game.gaugesPowered()) {
            hint = Text.translatable("screen.sparkstrength.engine.hint_fuses").getString();
        } else if (!game.allSet()) {
            hint = Text.translatable("screen.sparkstrength.engine.hint_gauges").getString();
        } else {
            hint = Text.translatable("screen.sparkstrength.engine.hint_restart").getString();
        }
        canvas.text(textRenderer, TabletCanvas.trim(textRenderer, hint, PANEL_W - 28), x + PAD, y + 27, hintColor);
        canvas.rect(x + 12, y + 39, PANEL_W - 24, 1, TabletTheme.HAIRLINE);
    }

    /** The engine junction box's gear, turning while the engine runs. / 动力接线箱上的齿轮，引擎运转时会转动。 */
    private void drawGear(TabletCanvas canvas, float cx, float cy, float r, int color, int hole, float time) {
        boolean running = screen.repair ? game.engineCaught() : game.condition == EngineCondition.INTACT;
        float spin = running ? time * 6.0F : 0.0F;
        for (int t = 0; t < 8; t++) {
            float a = (float) Math.toRadians(spin + t * 45.0F);
            float base = (float) Math.toRadians(13.0F);
            float tip = (float) Math.toRadians(8.0F);
            canvas.polygon(new float[]{
                    cx + MathHelper.sin(a - base) * r * 0.7F, cy - MathHelper.cos(a - base) * r * 0.7F,
                    cx + MathHelper.sin(a - tip) * r, cy - MathHelper.cos(a - tip) * r,
                    cx + MathHelper.sin(a + tip) * r, cy - MathHelper.cos(a + tip) * r,
                    cx + MathHelper.sin(a + base) * r * 0.7F, cy - MathHelper.cos(a + base) * r * 0.7F}, color);
        }
        canvas.circle(cx, cy, r * 0.76F, color);
        canvas.circle(cx, cy, r * 0.32F, hole);
    }

    /** The cabinet's three indicator lamps, mirroring the block. / 机柜的三个指示灯，与方块一致。 */
    private void drawLamps(TabletCanvas canvas, float rightX, float cy) {
        EngineCondition condition = game.condition;
        for (int k = 0; k < 3; k++) {
            float lx = rightX - k * 11;
            int color;
            float on;
            if (game.endState == END_SUCCESS || condition == EngineCondition.INTACT) {
                color = TabletTheme.SUCCESS;
                on = 1.0F;
            } else if (condition == EngineCondition.DAMAGED) {
                color = TabletTheme.DANGER;
                on = (screen.openTicks() / 8) % 2 == 0 ? 1.0F : 0.0F;
            } else if (condition == EngineCondition.WRECKED) {
                color = TabletTheme.DANGER;
                on = switch (k) {
                    case 0 -> ((screen.openTicks() * 7 + 3) % 11) < 3 ? 0.8F : 0.0F;
                    case 1 -> 0.45F;
                    default -> 0.0F;
                };
            } else {
                color = TabletTheme.DANGER;
                on = 0.0F;
            }
            canvas.circle(lx, cy, 4.2F, INK);
            canvas.ring(lx, cy, 4.2F, 1.1F, BRASS_DARK);
            if (on > 0.0F) {
                canvas.glow(lx, cy, 8.0F, TabletTheme.withAlpha(color, (int) (0x55 * on)));
            }
            canvas.circle(lx, cy, 2.8F, TabletTheme.mix(TabletTheme.mix(color, 0xFF000000, 0.75F), color, on));
            canvas.circle(lx - 0.8F, cy - 0.8F, 0.8F, TabletTheme.withAlpha(0xFFFFFFFF, (int) (0x20 + 0x40 * on)));
        }
    }

    private void caption(TabletCanvas canvas, TextRenderer textRenderer, String key, @Nullable String count, int x,
                         int y, int color) {
        int w = canvas.text(textRenderer, Text.translatable(key).getString(), x, y, color);
        if (count != null) {
            canvas.text(textRenderer, count, x + w + 5, y, color == TabletTheme.TEXT_2 ? TabletTheme.TEXT_3 : color);
        }
    }

    private void drawFuse(TabletCanvas canvas, int slot, float delta, boolean hover) {
        float fx = layout.fuseX(slot);
        float fy = layout.fuseY();
        boolean isBurnt = game.burnt[slot];
        float progress = isBurnt
                ? MathHelper.lerp(delta, game.prevFuseCharge[slot], game.fuseCharge[slot]) / EngineRules.FUSE_HOLD_TICKS
                : 1.0F;
        boolean held = isBurnt && hover && game.fuseHold;
        canvas.roundRect(fx, fy, FUSE_W, FUSE_H, 3, 0xFF121417);
        int rim = !isBurnt ? 0xFF33383F : held ? 0xFFF5C451 : hover ? HAZARD : 0xFF6A2C28;
        canvas.roundRectOutline(fx, fy, FUSE_W, FUSE_H, 3, 1.0F, rim);
        // Spring clips. / 弹簧夹。
        canvas.rect(fx + 4, fy + 3, 7, FUSE_H - 6, 0xFF2A2D31);
        canvas.rect(fx + FUSE_W - 11, fy + 3, 7, FUSE_H - 6, 0xFF2A2D31);
        if (isBurnt && progress < 1.0F) {
            drawCartridge(canvas, fx, fy, true);
        }
        if (progress > 0.0F) {
            // Replacing: fresh glass and brass wipe across the burnt cartridge. / 更换中：新的玻璃与黄铜从左向右替换烧黑的管子。
            boolean clip = progress < 1.0F;
            if (clip) {
                canvas.pushClip(fx, fy, FUSE_W * progress, FUSE_H);
            }
            drawCartridge(canvas, fx, fy, false);
            if (clip) {
                canvas.popClip();
                float edge = fx + FUSE_W * progress;
                canvas.glow(edge, fy + FUSE_H / 2.0F, 6, 0x80FFD27A);
                canvas.line(edge, fy + 3, edge, fy + FUSE_H - 3, 1.0F, 0xFFFFE6A8);
            }
        }
        if (game.fuseFlash[slot] > 0.0F) {
            canvas.glow(fx + FUSE_W / 2.0F, fy + FUSE_H / 2.0F, 16,
                    TabletTheme.withAlpha(TabletTheme.SUCCESS, (int) (0x70 * game.fuseFlash[slot])));
        }
    }

    private void drawCartridge(TabletCanvas canvas, float fx, float fy, boolean isBurnt) {
        float capL = fx + 5;
        float capR = fx + FUSE_W - 10;
        float glassX = fx + 10;
        float glassW = FUSE_W - 20;
        int cap = isBurnt ? BRASS_BURNT : BRASS;
        canvas.roundRect(capL, fy + 4, 5, FUSE_H - 8, 1.5F, cap);
        canvas.roundRect(capR, fy + 4, 5, FUSE_H - 8, 1.5F, cap);
        if (isBurnt) {
            canvas.roundRect(glassX, fy + 5, glassW, FUSE_H - 10, 2, 0xFF1C1816);
            canvas.glow(glassX + glassW / 2.0F, fy + FUSE_H / 2.0F, 7, 0xB0000000);
            canvas.line(glassX, fy + FUSE_H / 2.0F, glassX + 5, fy + FUSE_H / 2.0F, 0.9F, 0xFF5A4A35);
            canvas.line(glassX + glassW - 5, fy + FUSE_H / 2.0F, glassX + glassW, fy + FUSE_H / 2.0F, 0.9F,
                    0xFF5A4A35);
            canvas.circle(glassX + 5.5F, fy + FUSE_H / 2.0F + 0.5F, 1.1F, 0xFF3A2E22);
        } else {
            canvas.rect(capL + 0.5F, fy + 5, 4, 1.3F, 0x60FFFFFF);
            canvas.rect(capR + 0.5F, fy + 5, 4, 1.3F, 0x60FFFFFF);
            canvas.roundRect(glassX, fy + 5, glassW, FUSE_H - 10, 2, 0xFF2C4752);
            canvas.line(glassX, fy + FUSE_H / 2.0F, glassX + glassW, fy + FUSE_H / 2.0F, 0.9F, 0xFFE8D7A6);
            canvas.rect(glassX + 1, fy + 6, glassW - 2, 1.3F, 0x55FFFFFF);
        }
    }

    private void drawGauge(TabletCanvas canvas, int index, float delta, float time, float lit, boolean scorched,
                           boolean hover) {
        boolean[] set = game.set;
        float[] bandLo = game.bandLo;
        float[] bandHi = game.bandHi;
        float cx = layout.gaugeCx(index);
        float cy = layout.gaugeCy();
        float r = layout.gaugeR();
        float raw = MathHelper.lerp(delta, game.prevNeedle[index], game.needle[index]);
        float value = raw;
        boolean shaking = (screen.repair && game.gaugesPowered() && !set[index] && game.endState == END_NONE)
                || (!screen.repair && game.condition == EngineCondition.DAMAGED);
        if (shaking) {
            // A sick engine: needles out of the green tremble. / 引擎不正常：不在绿区的指针在抖。
            value += 0.010F * MathHelper.sin(time * 2.1F + index * 1.7F) + 0.006F * MathHelper.sin(time * 3.7F + index);
        }
        value = MathHelper.clamp(value, -0.02F, 1.02F);

        int brass = scorched ? TabletTheme.mix(BRASS, BRASS_BURNT, 0.45F) : BRASS;
        canvas.circle(cx, cy + 1, r + 3.5F, 0x70000000);
        canvas.circle(cx, cy, r + 3, BRASS_DARK);
        canvas.ring(cx, cy, r + 3, 2.2F, brass);
        canvas.arc(cx, cy, r + 3, 1.0F, -80, 70, TabletTheme.withAlpha(BRASS_LIGHT, scorched ? 0x50 : 0xA0));
        canvas.circle(cx, cy, r, scorched ? DIAL_FACE_CHAR : DIAL_FACE);
        canvas.glow(cx, cy - r * 0.35F, r * 0.9F, TabletTheme.withAlpha(0xFFFFFFFF, (int) (0x12 * lit)));

        // Zones: red on both sides of the green band. / 分区：绿区两侧都是红区。
        float zoneR = r - 1.5F;
        int red = dim(TabletTheme.withAlpha(TabletTheme.DANGER, 0x90), lit);
        int green = dim(TabletTheme.withAlpha(TabletTheme.SUCCESS, set[index] ? 0xFF : 0xC0), lit);
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG, bandLo[index] * SWEEP_DEG, red);
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG + bandHi[index] * SWEEP_DEG, (1.0F - bandHi[index]) * SWEEP_DEG,
                red);
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG + bandLo[index] * SWEEP_DEG,
                (bandHi[index] - bandLo[index]) * SWEEP_DEG, green);
        int tick = dim(TICK, lit);
        for (int k = 0; k <= 8; k++) {
            boolean major = k % 2 == 0;
            radial(canvas, cx, cy, k / 8.0F, r - (major ? 9.0F : 7.0F), r - 5.0F, major ? 1.0F : 0.7F, tick);
        }
        if (scorched) {
            float[] c = game.cracks[index];
            canvas.polyline(new float[]{cx + c[0] * r, cy + c[1] * r, cx + c[2] * r, cy + c[3] * r,
                    cx + c[4] * r, cy + c[5] * r}, 0.6F, 0x55FFFFFF);
        }

        // Needle with a short tail, brass hub. / 指针带短尾，黄铜轴心。
        float a = (float) Math.toRadians(START_DEG + value * SWEEP_DEG);
        float sin = MathHelper.sin(a);
        float cos = MathHelper.cos(a);
        int needleColor = TabletTheme.mix(0xFF6B6F75, NEEDLE, lit);
        canvas.capsule(cx - sin * 3.0F, cy + cos * 3.0F, cx + sin * (r - 4.0F), cy - cos * (r - 4.0F), 1.4F,
                needleColor);
        canvas.circle(cx, cy, 2.6F, brass);
        canvas.circle(cx, cy, 1.0F, 0xFF2A2116);

        // Set lamp. / 到位指示灯。
        float ly = layout.lampCy();
        canvas.circle(cx, ly, 3.4F, INK);
        if (set[index]) {
            canvas.glow(cx, ly, 8.0F, TabletTheme.withAlpha(TabletTheme.SUCCESS, 0x60));
            canvas.circle(cx, ly, 2.4F, TabletTheme.SUCCESS);
        } else {
            canvas.circle(cx, ly, 2.4F, dim(0xFF5A2326, lit));
        }

        if (screen.repair) {
            drawKnob(canvas, cx, layout.knobCy(), hover, lit);
            float k = (float) Math.toRadians(-135.0F + 270.0F * game.target[index]);
            canvas.capsule(cx, layout.knobCy(), cx + MathHelper.sin(k) * 4.6F,
                    layout.knobCy() - MathHelper.cos(k) * 4.6F, 1.4F, dim(GEAR, lit));
        }
    }

    private void drawKnob(TabletCanvas canvas, float cx, float ky, boolean hover, float lit) {
        int arrow = dim(hover ? TabletTheme.TEXT_2 : TabletTheme.TEXT_3, lit);
        float ax = KNOB_R + 6;
        canvas.polygon(new float[]{cx - ax - 4, ky, cx - ax, ky - 3, cx - ax, ky + 3}, arrow);
        canvas.polygon(new float[]{cx + ax + 4, ky, cx + ax, ky + 3, cx + ax, ky - 3}, arrow);
        if (hover) {
            canvas.glow(cx, ky, 13, TabletTheme.withAlpha(GEAR, 0x38));
        }
        canvas.circle(cx, ky + 1, KNOB_R + 0.5F, 0x80000000);
        canvas.circle(cx, ky, KNOB_R, dim(hover ? BRASS_LIGHT : BRASS, lit));
        canvas.circle(cx, ky, KNOB_R - 1.4F, 0xFF2A2D31);
        canvas.circle(cx - 1.2F, ky - 1.2F, KNOB_R - 3.2F, 0xFF34383E);
    }

    private void drawTachometer(TabletCanvas canvas, TextRenderer textRenderer, float delta, boolean scorched,
                                float lit) {
        float cx = layout.columnCx();
        float cy = layout.tachCy();
        float r = TACH_R;
        float tachLit = screen.repair ? lit : 1.0F;
        int brass = scorched ? TabletTheme.mix(BRASS, BRASS_BURNT, 0.45F) : BRASS;
        canvas.circle(cx, cy + 1, r + 3.5F, 0x70000000);
        canvas.circle(cx, cy, r + 3, BRASS_DARK);
        canvas.ring(cx, cy, r + 3, 2.4F, brass);
        canvas.arc(cx, cy, r + 3, 1.0F, -80, 70, TabletTheme.withAlpha(BRASS_LIGHT, scorched ? 0x50 : 0xA0));
        canvas.circle(cx, cy, r, scorched ? DIAL_FACE_CHAR : DIAL_FACE);
        canvas.glow(cx, cy - r * 0.35F, r * 0.9F, 0x12FFFFFF);

        float value = MathHelper.lerp(delta, game.prevTach, game.tach);
        float zoneR = r - 1.5F;
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG, TACH_GREEN_LO * SWEEP_DEG, dim(0xFF2A3036, tachLit));
        boolean inGreen = value >= TACH_GREEN_LO - 0.005F && value <= TACH_GREEN_HI;
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG + TACH_GREEN_LO * SWEEP_DEG,
                (TACH_GREEN_HI - TACH_GREEN_LO) * SWEEP_DEG,
                dim(TabletTheme.withAlpha(TabletTheme.SUCCESS, inGreen ? 0xFF : 0xC0), tachLit));
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG + TACH_GREEN_HI * SWEEP_DEG,
                (TACH_REDLINE - TACH_GREEN_HI) * SWEEP_DEG, dim(0xFF2A3036, tachLit));
        canvas.arc(cx, cy, zoneR, 3.0F, START_DEG + TACH_REDLINE * SWEEP_DEG, (1.0F - TACH_REDLINE) * SWEEP_DEG,
                dim(TabletTheme.withAlpha(TabletTheme.DANGER, 0xC0), tachLit));
        // Progress trail inside the scale while spinning up. / 加速时刻度内侧的进度轨迹。
        if (value > 0.01F) {
            int trail = inGreen ? TabletTheme.SUCCESS : TabletTheme.WARNING;
            canvas.arc(cx, cy, r - 6.0F, 1.5F, START_DEG, Math.min(1.0F, value) * SWEEP_DEG,
                    TabletTheme.withAlpha(trail, 0x70));
        }
        int tick = dim(TICK, tachLit);
        for (int k = 0; k <= 16; k++) {
            boolean major = k % 2 == 0;
            radial(canvas, cx, cy, k / 16.0F, r - (major ? 12.0F : 10.0F), r - 8.0F, major ? 1.0F : 0.6F, tick);
        }
        if (scorched) {
            float[] c = game.cracks[EngineRules.MAX_GAUGES - 1];
            canvas.polyline(new float[]{cx - c[0] * r, cy + c[1] * r, cx - c[2] * r, cy + c[3] * r,
                    cx - c[4] * r, cy + c[5] * r}, 0.6F, 0x55FFFFFF);
        }
        canvas.textCentered(textRenderer, "RPM", (int) cx, (int) (cy + r * 0.38F), dim(TabletTheme.TEXT_3, tachLit));

        float a = (float) Math.toRadians(START_DEG + MathHelper.clamp(value, 0.0F, 1.02F) * SWEEP_DEG);
        float sin = MathHelper.sin(a);
        float cos = MathHelper.cos(a);
        canvas.capsule(cx - sin * 4.0F, cy + cos * 4.0F, cx + sin * (r - 5.0F), cy - cos * (r - 5.0F), 1.6F,
                TabletTheme.mix(0xFF6B6F75, TACH_NEEDLE, tachLit));
        canvas.circle(cx, cy, 3.2F, brass);
        canvas.circle(cx, cy, 1.2F, 0xFF2A2116);
    }

    private void drawRestartButton(TabletCanvas canvas, TextRenderer textRenderer, float delta, float time, int mouseX,
                                   int mouseY) {
        float bx = layout.buttonX();
        float by = layout.buttonY();
        boolean ready = game.restartReady();
        boolean done = game.engineCaught();
        // A failed close (interrupted, or wrecked mid quick repair) empties the button and tints it red, never green.
        // 失败的关闭（中断，或抢修途中动力损毁）会清空按钮并染成暗红，绝不显示为绿色。
        boolean failed = game.endState == END_FAILURE;
        boolean pressed = game.holdingRestart && ready;
        boolean hover = ready
                && AbstractRepairScreen.inside(mouseX, mouseY, bx - 4, by - 4, BUTTON_W + 8, BUTTON_H + 8);
        float progress = failed ? 0.0F : done ? 1.0F : screen.chargeProgress(delta);

        // Housing with a brass frame. / 黄铜边框的按钮座。
        canvas.roundRect(bx - 3, by - 3, BUTTON_W + 6, BUTTON_H + 6, 6, INK);
        canvas.roundRectOutline(bx - 3, by - 3, BUTTON_W + 6, BUTTON_H + 6, 6, 1.0F,
                ready ? TabletTheme.mix(BRASS_DARK, HAZARD, 0.5F + 0.5F * MathHelper.sin(time * 0.25F)) : BRASS_DARK);
        float press = pressed ? 1.0F : 0.0F;
        int capTop;
        int capBottom;
        if (failed) {
            capTop = 0xFF4A2326;
            capBottom = 0xFF2C1517;
        } else if (done) {
            capTop = 0xFF36A869;
            capBottom = 0xFF1F6B41;
        } else if (ready) {
            capTop = hover && !pressed ? 0xFFE04B44 : 0xFFC63B35;
            capBottom = 0xFF8A2421;
        } else {
            capTop = 0xFF34373C;
            capBottom = 0xFF25282C;
        }
        canvas.roundRectGradientV(bx, by + press, BUTTON_W, BUTTON_H - press, 4, capTop, capBottom);
        if (!pressed) {
            canvas.roundRect(bx + 2, by + 1, BUTTON_W - 4, 3, 1.5F, 0x30FFFFFF);
        }
        if (progress > 0.0F) {
            canvas.roundRect(bx + 3, by + BUTTON_H - 4, (BUTTON_W - 6) * Math.min(1.0F, progress), 2, 1,
                    progress >= 1.0F ? TabletTheme.SUCCESS : HAZARD);
        }
        String label = Text.translatable("screen.sparkstrength.engine.restart").getString();
        int labelColor = failed ? TabletTheme.mix(TabletTheme.TEXT_3, TabletTheme.DANGER, 0.5F)
                : ready || done ? TabletTheme.TEXT : TabletTheme.TEXT_3;
        canvas.textCentered(textRenderer, label, (int) (bx + BUTTON_W / 2.0F), (int) (by + 6 + press), labelColor);

        String note = null;
        int noteColor = TabletTheme.TEXT_3;
        if (ready && !pressed) {
            note = Text.translatable("screen.sparkstrength.engine.restart_hold").getString();
            noteColor = HAZARD;
        } else if (!ready && !done && !failed) {
            note = Text.translatable("screen.sparkstrength.engine.restart_locked").getString();
        }
        if (note != null) {
            canvas.textCentered(textRenderer, note, (int) layout.columnCx(), (int) (by + BUTTON_H + 6), noteColor);
        }
    }

    /** A tick mark from radius r0 to r1 at a dial value. / 在表盘某个读数处，从半径 r0 到 r1 画一条刻度。 */
    private static void radial(TabletCanvas canvas, float cx, float cy, float value, float r0, float r1,
                               float thickness, int color) {
        float a = (float) Math.toRadians(START_DEG + value * SWEEP_DEG);
        float sin = MathHelper.sin(a);
        float cos = MathHelper.cos(a);
        canvas.line(cx + sin * r0, cy - cos * r0, cx + sin * r1, cy - cos * r1, thickness, color);
    }

    /** Fades a colour towards dead grey while the gauges have no power. / 压力表未通电时把颜色淡向灰色。 */
    private static int dim(int color, float lit) {
        if (lit >= 0.999F) {
            return color;
        }
        int dead = (color & 0xFF000000) | (DEAD & 0x00FFFFFF);
        return TabletTheme.mix(dead, color, lit);
    }
}
