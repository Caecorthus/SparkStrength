package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.tablet.TabletLayout;
import net.minecraft.client.util.Window;

/**
 * Maps the window framebuffer to the tablet's logical canvas ({@link TabletLayout#effectiveScale}); {@link TabletScreen}
 * recomputes it in every init() and divides every mouse coordinate by {@link #logicalScale()}.
 * 把窗口帧缓冲映射到平板的逻辑画布（见 effectiveScale）；TabletScreen 在每次 init() 中重算，并把所有鼠标坐标除以 logicalScale。
 */
final class TabletViewport {
    // Logical canvas (recomputed in init(), which vanilla also runs on resize). 逻辑画布（在 init() 中重算，原版调整窗口时也会调用）。
    private int pixelScale = 1;
    private float logicalScale = 1f;
    private int canvasWidth;
    private int canvasHeight;
    private int framebufferHeight;

    /**
     * Logical canvas = framebuffer / e; the GUI matrix is scaled by e / guiScale so one logical unit is e physical
     * pixels. Mouse events arrive in GUI units and are divided by the same factor.
     * 逻辑画布 = 帧缓冲 / e；GUI 矩阵按 e / guiScale 缩放，使 1 逻辑单位 = e 物理像素。鼠标事件以 GUI 单位到达，除以同一系数。
     */
    void update(Window window) {
        int framebufferWidth = Math.max(1, window.getFramebufferWidth());
        framebufferHeight = Math.max(1, window.getFramebufferHeight());
        int guiScale = Math.max(1, (int) window.getScaleFactor());
        pixelScale = TabletLayout.effectiveScale(framebufferWidth, framebufferHeight, guiScale);
        logicalScale = pixelScale / (float) guiScale;
        canvasWidth = framebufferWidth / pixelScale;
        canvasHeight = framebufferHeight / pixelScale;
    }

    double toLogical(double guiCoordinate) {
        return guiCoordinate / logicalScale;
    }

    int pixelScale() {
        return pixelScale;
    }

    float logicalScale() {
        return logicalScale;
    }

    int canvasWidth() {
        return canvasWidth;
    }

    int canvasHeight() {
        return canvasHeight;
    }

    int framebufferHeight() {
        return framebufferHeight;
    }
}
