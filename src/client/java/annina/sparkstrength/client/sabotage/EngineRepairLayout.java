package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.sabotage.EngineRepairMode;

/**
 * Where the engine panel's parts sit and how big they are, and the hit tests on them. Read live from the screen's
 * size, so a resize moves everything at once.
 * 动力装置面板各部件的位置与尺寸，以及对它们的命中检测。每次都按界面的实时尺寸计算，因此窗口缩放时一起移动。
 */
final class EngineRepairLayout {
    static final int PANEL_W = 320;
    private static final int PANEL_H = 190;
    /** The status panel has no knobs or restart button, so it is shorter. / 状态面板没有旋钮和重启按钮，因此更矮。 */
    private static final int STATUS_PANEL_H = 156;
    static final int PAD = 14;
    /** Width of the left area (fuses and gauges). / 左侧区域（保险丝与压力表）的宽度。 */
    private static final int LEFT_W = 204;
    /** Centre of the right column (tachometer and restart), relative to the panel. / 右栏（转速表与重启）中线，相对面板。 */
    private static final int COLUMN_CX = 268;
    static final int FUSE_SLOTS = 5;
    static final int FUSE_W = 36;
    static final int FUSE_H = 18;
    private static final int FUSE_GAP = (LEFT_W - FUSE_SLOTS * FUSE_W) / (FUSE_SLOTS - 1);
    private static final int FUSE_Y = 55;
    static final float TACH_R = 25.0F;
    static final int BUTTON_W = 64;
    static final int BUTTON_H = 20;
    private static final int BUTTON_Y = 121;
    static final float KNOB_R = 7.0F;

    private final EngineRepairScreen screen;
    final int panelH;

    EngineRepairLayout(EngineRepairScreen screen) {
        this.screen = screen;
        this.panelH = screen.repair ? PANEL_H : STATUS_PANEL_H;
    }

    int left() {
        return (screen.width - PANEL_W) / 2;
    }

    int top() {
        return (screen.height - panelH) / 2;
    }

    private float slotW() {
        return LEFT_W / (float) screen.gaugeCount;
    }

    float gaugeCx(int index) {
        return left() + PAD + slotW() * (index + 0.5F);
    }

    /** Dial radius: as large as the slot and the mode's vertical room allow. / 表盘半径：在槽宽与该模式的纵向空间内尽量大。 */
    float gaugeR() {
        float max = switch (screen.mode) {
            case OVERHAUL -> 17.0F;
            case QUICK -> 24.0F;
            case STATUS -> 22.0F;
        };
        return Math.min(max, slotW() / 2.0F - 7.0F);
    }

    /** Overhaul sits below the fuse row; the quick repair's knobs line up with the restart button. / 大修位于保险丝排下方；抢修的旋钮与重启按钮对齐。 */
    float gaugeCy() {
        return top() + switch (screen.mode) {
            case OVERHAUL -> 109;
            case QUICK -> 86;
            case STATUS -> 90;
        };
    }

    float lampCy() {
        return gaugeCy() + gaugeR() + 9;
    }

    float knobCy() {
        return gaugeCy() + gaugeR() + 23;
    }

    boolean overGauge(double mx, double my, int index) {
        float half = slotW() / 2.0F;
        float y0 = gaugeCy() - gaugeR() - 4;
        return AbstractRepairScreen.inside(mx, my, gaugeCx(index) - half, y0, slotW(), knobCy() + KNOB_R + 3 - y0);
    }

    float fuseX(int slot) {
        return left() + PAD + slot * (FUSE_W + FUSE_GAP);
    }

    float fuseY() {
        return top() + FUSE_Y;
    }

    int fuseAt(double mx, double my) {
        for (int s = 0; s < FUSE_SLOTS; s++) {
            if (AbstractRepairScreen.inside(mx, my, fuseX(s), fuseY(), FUSE_W, FUSE_H)) {
                return s;
            }
        }
        return -1;
    }

    float columnCx() {
        return left() + COLUMN_CX;
    }

    float tachCy() {
        return top() + (screen.mode == EngineRepairMode.STATUS ? 90 : 85);
    }

    float buttonX() {
        return columnCx() - BUTTON_W / 2.0F;
    }

    float buttonY() {
        return top() + BUTTON_Y;
    }
}
