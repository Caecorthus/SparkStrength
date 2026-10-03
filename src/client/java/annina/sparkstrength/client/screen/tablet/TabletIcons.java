package annina.sparkstrength.client.screen.tablet;

/**
 * Vector glyphs for the tablet UI. Each icon fills the square box (x, y, size) and is authored on a 16-unit
 * design grid (u = size / 16); strokes are about size / 9 wide but never thinner than one physical pixel.
 * Shapes avoid overlapping each other where possible, so translucent colours blend cleanly.
 * 平板界面的矢量图标。每个图标占据方形区域 (x, y, size)，按 16 单位设计网格绘制（u = size / 16）；
 * 描边宽约 size / 9，且不小于 1 个物理像素。形状尽量不互相重叠，以便半透明颜色干净混合。
 */
public final class TabletIcons {
    private static final float GRID = 16f;
    private static final float SQRT_HALF = 0.70710677f;
    // Render-thread path scratch (x, y pairs). 渲染线程专用的路径缓冲（x, y 成对）。
    private static final float[] PATH = new float[160];
    private static final float[] ARM_X = {0f, 1f, 0f, -1f};
    private static final float[] ARM_Y = {-1f, 0f, 1f, 0f};

    private TabletIcons() {
    }

    /** Two people: back figure (55% alpha) head (11.3,5) r2.3, front head (6,5.4) r2.8, half-ellipse bodies. */
    public static void members(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        int back = TabletTheme.multiplyAlpha(color, 0.55f);
        canvas.circle(x + 11.3f * u, y + 5.0f * u, 2.3f * u, back);
        halfEllipse(canvas, x + 11.9f * u, y + 13.8f * u, 3.6f * u, 4.2f * u, back);
        canvas.circle(x + 6f * u, y + 5.4f * u, 2.8f * u, color);
        halfEllipse(canvas, x + 6f * u, y + 14.4f * u, 4.8f * u, 4.8f * u, color);
    }

    /** Filled bubble (1.5,2.2 13x9.4 r3.2) with a tail at the bottom-left down to (3.2,14.6). */
    public static void chat(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        canvas.roundRect(x + 1.5f * u, y + 2.2f * u, 13f * u, 9.4f * u, 3.2f * u, color);
        int n = 0;
        n = put(n, x + 3.4f * u, y + 11.2f * u);
        n = put(n, x + 7.8f * u, y + 11.2f * u);
        n = put(n, x + 3.2f * u, y + 14.6f * u);
        canvas.fillPath(PATH, n, color);
    }

    /** Mouthpiece (1.8..5.2, 6..10.4), horn quad to x=11.2 (2.6..13.8), handle, one sound-wave arc. */
    public static void megaphone(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        canvas.roundRect(x + 1.8f * u, y + 6.0f * u, 3.4f * u, 4.4f * u, 1.0f * u, color);
        int n = 0;
        n = put(n, x + 5.2f * u, y + 6.0f * u);
        n = put(n, x + 11.2f * u, y + 2.6f * u);
        n = put(n, x + 11.2f * u, y + 13.8f * u);
        n = put(n, x + 5.2f * u, y + 10.4f * u);
        canvas.fillPath(PATH, n, color);
        canvas.capsule(x + 4.0f * u, y + 11.4f * u, x + 4.7f * u, y + 13.8f * u, 1.9f * u, color);
        canvas.arc(x + 11.2f * u, y + 8.2f * u, 4.4f * u, stroke, 50f, 80f, color);
    }

    /** Ring centred (8,8) outer r5.2, centre dot r1.6, four ticks from the ring edge out to 0.4 / 15.6. */
    public static void target(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        float cx = x + 8f * u;
        float cy = y + 8f * u;
        canvas.ring(cx, cy, 5.2f * u, stroke, color);
        canvas.circle(cx, cy, 1.6f * u, color);
        canvas.line(cx, y + 0.4f * u, cx, y + 2.8f * u, stroke, color);
        canvas.line(cx, y + 13.2f * u, cx, y + 15.6f * u, stroke, color);
        canvas.line(x + 0.4f * u, cy, x + 2.8f * u, cy, stroke, color);
        canvas.line(x + 13.2f * u, cy, x + 15.6f * u, cy, stroke, color);
    }

    /**
     * Four bottom-aligned bars (w2.6, step 3.8 from x1.3, heights 4.5/7.5/10.5/13.5, bottom y14.8);
     * the first {@code litBars} use {@code litColor}, the rest {@code dimColor}.
     */
    public static void signal(TabletCanvas canvas, float x, float y, float size, int litBars, int litColor, int dimColor) {
        float u = size / GRID;
        float bottom = y + 14.8f * u;
        for (int i = 0; i < 4; i++) {
            float height = (4.5f + 3f * i) * u;
            float left = x + (1.3f + 3.8f * i) * u;
            canvas.roundRect(left, bottom - height, 2.6f * u, height, 0.9f * u, i < litBars ? litColor : dimColor);
        }
    }

    /** Body (2.8,7.2 10.4x7.6 r1.8) and a shackle stroke x5.1..10.9, arc centre (8,5.2) r2.9, ending on the body. */
    public static void lock(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        canvas.roundRect(x + 2.8f * u, y + 7.2f * u, 10.4f * u, 7.6f * u, 1.8f * u, color);
        int n = 0;
        n = put(n, x + 5.1f * u, y + 7.2f * u);
        n = put(n, x + 5.1f * u, y + 5.2f * u);
        final int steps = 10;
        for (int k = 1; k < steps; k++) {
            double angle = Math.PI + Math.PI * k / steps;
            n = put(n, x + (8f + 2.9f * (float) Math.cos(angle)) * u, y + (5.2f + 2.9f * (float) Math.sin(angle)) * u);
        }
        n = put(n, x + 10.9f * u, y + 5.2f * u);
        n = put(n, x + 10.9f * u, y + 7.2f * u);
        canvas.strokePath(PATH, n, stroke, color, false, false);
    }

    /** Two stadium outlines on the rising diagonal, centres (9.84,6.16) / (6.16,9.84), straight 3.8, radius 2.2. */
    public static void link(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        float offset = 2.6f * SQRT_HALF;
        int n = stadium(x + (8f + offset) * u, y + (8f - offset) * u, SQRT_HALF, -SQRT_HALF, 1.9f * u, 2.2f * u);
        canvas.strokePath(PATH, n, stroke, color, false, true);
        n = stadium(x + (8f - offset) * u, y + (8f + offset) * u, SQRT_HALF, -SQRT_HALF, 1.9f * u, 2.2f * u);
        canvas.strokePath(PATH, n, stroke, color, false, true);
    }

    /** Paper plane pointing right: notch (4.6,8), wings (1.8,2.4) / (1.8,13.6), nose (14.6,8). */
    public static void send(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        int n = 0;
        // Vertex 0 is the notch: the shape is star-shaped around it, so the fan from vertex 0 is valid.
        // 第 0 个顶点是凹口：图形相对它呈星形，因此从第 0 个顶点扇形剖分是正确的。
        n = put(n, x + 4.6f * u, y + 8f * u);
        n = put(n, x + 1.8f * u, y + 2.4f * u);
        n = put(n, x + 14.6f * u, y + 8f * u);
        n = put(n, x + 1.8f * u, y + 13.6f * u);
        canvas.fillPath(PATH, n, color);
    }

    /** × as one star polygon (no overlap in the middle): arms reach 5.9 along each diagonal, round tips. */
    public static void close(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float half = stroke(canvas, size) * 0.5f;
        float reach = 5.9f * u;
        float arm = Math.max(half, reach - half);
        float cx = x + 8f * u;
        float cy = y + 8f * u;
        final int tipSteps = 6;
        int n = 0;
        // Arms of a "+" (up, right, down, left), each with a semicircular tip, then rotated by 45 degrees.
        // “+” 的四臂（上、右、下、左）各带半圆端头，整体旋转 45 度。
        for (int armIndex = 0; armIndex < 4; armIndex++) {
            float dvx = ARM_X[armIndex];
            float dvy = ARM_Y[armIndex];
            float rx = -dvy;
            float ry = dvx;
            float tipX = dvx * arm;
            float tipY = dvy * arm;
            for (int k = 0; k <= tipSteps; k++) {
                double phi = Math.PI * k / tipSteps;
                float c = (float) Math.cos(phi);
                float s = (float) Math.sin(phi);
                n = putRotated(n, cx, cy, tipX + half * (-c * rx + s * dvx), tipY + half * (-c * ry + s * dvy));
            }
            n = putRotated(n, cx, cy, half * (rx + dvx), half * (ry + dvy));
        }
        canvas.fillStar(PATH, n, cx, cy, color);
    }

    /** Tick (3.2,8.6) → (6.6,12) → (12.8,4.6), round caps. */
    public static void check(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        int n = 0;
        n = put(n, x + 3.2f * u, y + 8.6f * u);
        n = put(n, x + 6.6f * u, y + 12.0f * u);
        n = put(n, x + 12.8f * u, y + 4.6f * u);
        canvas.strokePath(PATH, n, stroke(canvas, size), color, true, false);
    }

    /** Chevron (4.2,6.2) → (8,10) → (11.8,6.2) pointing down, mirrored vertically when {@code up}. */
    public static void caret(TabletCanvas canvas, float x, float y, float size, boolean up, int color) {
        float u = size / GRID;
        float outerY = up ? 9.8f : 6.2f;
        float tipY = up ? 6.0f : 10.0f;
        int n = 0;
        n = put(n, x + 4.2f * u, y + outerY * u);
        n = put(n, x + 8f * u, y + tipY * u);
        n = put(n, x + 11.8f * u, y + outerY * u);
        canvas.strokePath(PATH, n, stroke(canvas, size), color, true, false);
    }

    /** Faint full track plus a 100-degree arc rotating once every 800 ms; outer radius {@code radius}. */
    public static void spinner(TabletCanvas canvas, float cx, float cy, float radius, long timeMs, int color) {
        float thickness = Math.max(radius * 0.3f, canvas.px());
        canvas.ring(cx, cy, radius, thickness, TabletTheme.multiplyAlpha(color, 0.22f));
        float start = Math.floorMod(timeMs, 800L) / 800f * 360f;
        canvas.arc(cx, cy, radius, thickness, start, 100f, color);
    }

    /** Shield outline: top (8,1.4), shoulders x2.4/13.6 at y3.4..7.4, curved sides to the point (8,14.8); tick inside. */
    public static void shield(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        int n = 0;
        n = put(n, x + 8f * u, y + 1.4f * u);
        n = put(n, x + 13.6f * u, y + 3.4f * u);
        n = put(n, x + 13.6f * u, y + 7.4f * u);
        n = quadratic(n, x + 13.6f * u, y + 7.4f * u, x + 13.4f * u, y + 12f * u, x + 8f * u, y + 14.8f * u, 6);
        n = quadratic(n, x + 8f * u, y + 14.8f * u, x + 2.6f * u, y + 12f * u, x + 2.4f * u, y + 7.4f * u, 6);
        n = put(n, x + 2.4f * u, y + 3.4f * u);
        canvas.strokePath(PATH, n, stroke, color, false, true);
        n = 0;
        n = put(n, x + 5.3f * u, y + 8.3f * u);
        n = put(n, x + 7.3f * u, y + 10.3f * u);
        n = put(n, x + 10.8f * u, y + 6.4f * u);
        canvas.strokePath(PATH, n, stroke * 0.9f, color, true, false);
    }

    /** Ring centred (8,8) outer r6.9; hands (8,4.4) → (8,8) → (10.7,9.6) as one round-capped stroke. */
    public static void clock(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        canvas.ring(x + 8f * u, y + 8f * u, 6.9f * u, stroke, color);
        int n = 0;
        n = put(n, x + 8f * u, y + 4.4f * u);
        n = put(n, x + 8f * u, y + 8f * u);
        n = put(n, x + 10.7f * u, y + 9.6f * u);
        canvas.strokePath(PATH, n, stroke, color, true, false);
    }

    // ------------------------------------------------------------------------------------------------ door monitor

    /** Closed door: one stroke floor (2.6..13.4 at y14.6) → jambs x4.4/11.6 → lintel y1.8 with r2 top corners; knob (9.3,8.8) r1.05. */
    public static void door(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        doorFrame(canvas, x, y, u, stroke(canvas, size), color);
        canvas.circle(x + 9.3f * u, y + 8.8f * u, 1.05f * u, color);
    }

    /** {@link #door} frame with the leaf swung inwards: trapezoid hinged on the left jamb (x5.9, y3.6..13.1 → x9.8, y5.0..11.8). */
    public static void doorOpen(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        doorFrame(canvas, x, y, u, stroke(canvas, size), color);
        int n = 0;
        n = put(n, x + 5.9f * u, y + 3.6f * u);
        n = put(n, x + 9.8f * u, y + 5.0f * u);
        n = put(n, x + 9.8f * u, y + 11.8f * u);
        n = put(n, x + 5.9f * u, y + 13.1f * u);
        canvas.fillPath(PATH, n, color);
    }

    /** Impact burst around (8,8.4): ten spikes alternating r7.4 / r5.6 with valleys at r3.2, slightly tilted. */
    public static void burst(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float cx = x + 8f * u;
        float cy = y + 8.4f * u;
        int n = 0;
        for (int k = 0; k < 20; k++) {
            double angle = -Math.PI / 2 + 0.12 + Math.PI * k / 10;
            float radius = (k & 1) == 1 ? 3.2f : (k & 2) == 0 ? 7.4f : 5.6f;
            n = put(n, cx + radius * u * (float) Math.cos(angle), cy + radius * u * (float) Math.sin(angle));
        }
        canvas.fillStar(PATH, n, cx, cy, color);
    }

    /** Key lying flat: bow ring centre (4.9,8) r3.4, shaft to x14.8, two teeth down from the shaft at x11.9 / x14.1. */
    public static void key(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        float half = stroke * 0.5f;
        canvas.ring(x + 4.9f * u, y + 8f * u, 3.4f * u, stroke, color);
        canvas.line(x + 8.3f * u, y + 8f * u, x + 14.8f * u, y + 8f * u, stroke, color);
        canvas.line(x + 11.9f * u, y + 8f * u + half, x + 11.9f * u, y + 11.4f * u, stroke, color);
        canvas.line(x + 14.1f * u, y + 8f * u + half, x + 14.1f * u, y + 10.6f * u, stroke, color);
    }

    /** Hook pick on the rising diagonal: thick handle (2.4,13.6) → (5.4,10.6), shaft to (11.8,4.2), hook to (13.6,6.0). */
    public static void lockpick(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        canvas.capsule(x + 2.4f * u, y + 13.6f * u, x + 5.4f * u, y + 10.6f * u, 3.4f * u, color);
        int n = 0;
        n = put(n, x + 5.4f * u, y + 10.6f * u);
        n = put(n, x + 11.8f * u, y + 4.2f * u);
        n = put(n, x + 13.6f * u, y + 6.0f * u);
        canvas.strokePath(PATH, n, stroke, color, true, false);
    }

    /** Wrench: handle (3,13) → (8.6,7.4) w2.8, C-shaped head centred (10.6,5.4) r4 w2.4, jaw opening up-right. */
    public static void wrench(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        canvas.capsule(x + 3.0f * u, y + 13.0f * u, x + 8.6f * u, y + 7.4f * u, 2.8f * u, color);
        canvas.arc(x + 10.6f * u, y + 5.4f * u, 4.0f * u, 2.4f * u, 82f, 286f, color);
    }

    /** {@link #lock} with the shackle lifted (arc centre y3.8) so its right leg stops at y4.6, clear of the body. */
    public static void unlock(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        float stroke = stroke(canvas, size);
        canvas.roundRect(x + 2.8f * u, y + 7.2f * u, 10.4f * u, 7.6f * u, 1.8f * u, color);
        int n = 0;
        n = put(n, x + 5.1f * u, y + 7.2f * u);
        n = put(n, x + 5.1f * u, y + 3.8f * u);
        final int steps = 10;
        for (int k = 1; k < steps; k++) {
            double angle = Math.PI + Math.PI * k / steps;
            n = put(n, x + (8f + 2.9f * (float) Math.cos(angle)) * u, y + (3.8f + 2.9f * (float) Math.sin(angle)) * u);
        }
        n = put(n, x + 10.9f * u, y + 3.8f * u);
        n = put(n, x + 10.9f * u, y + 4.6f * u);
        canvas.strokePath(PATH, n, stroke, color, false, false);
    }

    /** Almond outline (1.4,8) ↔ (14.6,8), quadratic lids through y2 / y14, pupil (8,8) r2.3. */
    public static void eye(TabletCanvas canvas, float x, float y, float size, int color) {
        float u = size / GRID;
        int n = 0;
        n = put(n, x + 1.4f * u, y + 8f * u);
        n = quadratic(n, x + 1.4f * u, y + 8f * u, x + 8f * u, y + 2.0f * u, x + 14.6f * u, y + 8f * u, 8);
        n = quadratic(n, x + 14.6f * u, y + 8f * u, x + 8f * u, y + 14.0f * u, x + 1.4f * u, y + 8f * u, 8);
        canvas.strokePath(PATH, n, stroke(canvas, size), color, false, true);
        canvas.circle(x + 8f * u, y + 8f * u, 2.3f * u, color);
    }

    /** Shared door frame: floor + jambs + rounded lintel as one mitred stroke, round caps at the floor ends. */
    private static void doorFrame(TabletCanvas canvas, float x, float y, float u, float stroke, int color) {
        final float left = 4.4f;
        final float right = 11.6f;
        final float top = 1.8f;
        final float radius = 2.0f;
        final int steps = 4;
        int n = 0;
        n = put(n, x + 2.6f * u, y + 14.6f * u);
        n = put(n, x + left * u, y + 14.6f * u);
        n = put(n, x + left * u, y + (top + radius) * u);
        for (int k = 1; k < steps; k++) {
            double angle = Math.PI + Math.PI * 0.5 * k / steps;
            n = put(n, x + (left + radius + radius * (float) Math.cos(angle)) * u,
                    y + (top + radius + radius * (float) Math.sin(angle)) * u);
        }
        n = put(n, x + (left + radius) * u, y + top * u);
        n = put(n, x + (right - radius) * u, y + top * u);
        for (int k = 1; k < steps; k++) {
            double angle = Math.PI * 1.5 + Math.PI * 0.5 * k / steps;
            n = put(n, x + (right - radius + radius * (float) Math.cos(angle)) * u,
                    y + (top + radius + radius * (float) Math.sin(angle)) * u);
        }
        n = put(n, x + right * u, y + (top + radius) * u);
        n = put(n, x + right * u, y + 14.6f * u);
        n = put(n, x + 13.4f * u, y + 14.6f * u);
        canvas.strokePath(PATH, n, stroke, color, true, false);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static float stroke(TabletCanvas canvas, float size) {
        return Math.max(size / 9f, canvas.px());
    }

    private static int put(int index, float px, float py) {
        PATH[index * 2] = px;
        PATH[index * 2 + 1] = py;
        return index + 1;
    }

    private static int putRotated(int index, float cx, float cy, float dx, float dy) {
        return put(index, cx + (dx - dy) * SQRT_HALF, cy + (dx + dy) * SQRT_HALF);
    }

    /** Upper half of an ellipse centred on its flat base (cx, baseY); convex, filled. */
    private static void halfEllipse(TabletCanvas canvas, float cx, float baseY, float rx, float ry, int color) {
        final int steps = 12;
        int n = 0;
        for (int k = 0; k <= steps; k++) {
            double angle = Math.PI + Math.PI * k / steps;
            n = put(n, cx + rx * (float) Math.cos(angle), baseY + ry * (float) Math.sin(angle));
        }
        canvas.fillPath(PATH, n, color);
    }

    /** Closed stadium centreline around (cx, cy) along unit axis (ax, ay). Returns the point count. */
    private static int stadium(float cx, float cy, float ax, float ay, float halfStraight, float radius) {
        final int steps = 8;
        float perpX = -ay;
        float perpY = ax;
        int n = 0;
        for (int end = 0; end < 2; end++) {
            float sign = end == 0 ? 1f : -1f;
            float ex = cx + ax * halfStraight * sign;
            float ey = cy + ay * halfStraight * sign;
            for (int k = 0; k <= steps; k++) {
                double phi = -Math.PI * 0.5 + Math.PI * k / steps + (end == 0 ? 0.0 : Math.PI);
                float c = (float) Math.cos(phi);
                float s = (float) Math.sin(phi);
                n = put(n, ex + radius * (ax * c + perpX * s), ey + radius * (ay * c + perpY * s));
            }
        }
        return n;
    }

    /** Appends {@code steps} samples of a quadratic Bezier (excluding its start point). */
    private static int quadratic(int n, float x0, float y0, float x1, float y1, float x2, float y2, int steps) {
        for (int k = 1; k <= steps; k++) {
            float t = (float) k / steps;
            float a = (1f - t) * (1f - t);
            float b = 2f * (1f - t) * t;
            float c = t * t;
            n = put(n, a * x0 + b * x1 + c * x2, a * y0 + b * y1 + c * y2);
        }
        return n;
    }
}
