package annina.sparkstrength.client.screen.tablet;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.OrderedText;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Anti-aliased vector drawing for the tablet UI, in logical units (one unit = {@code pixelScale} physical pixels).
 * 平板界面的抗锯齿矢量绘制，使用逻辑单位（1 单位 = pixelScale 个物理像素）。
 *
 * <p>Contract: geometry goes into {@link RenderLayer#getGui()} (QUADS, position+colour) with the current matrix;
 * every public shape call ends with {@code context.draw()}, so draw order equals call order relative to vanilla
 * text/texture calls. Triangles are emitted as degenerate quads (a,b,c,c) and all winding is canonicalised to the
 * order {@link DrawContext#fill} uses (TL, BL, BR, TR), so back-face culling can never drop geometry.
 * 约定：几何体以当前矩阵写入 RenderLayer.getGui()（QUADS，位置+颜色）；每个公开形状方法结束时都会调用
 * context.draw()，因此与原版文字/贴图调用的绘制顺序与调用顺序一致。三角形以退化四边形 (a,b,c,c) 输出，
 * 所有绕序统一为 DrawContext.fill 的顺序（左上、左下、右下、右上），背面剔除永远不会丢弃几何体。</p>
 *
 * <p>Edges get a one-physical-pixel feather whose outer vertices keep the RGB but have alpha 0 (no dark fringe).
 * Render thread only: shared scratch buffers are static.
 * 边缘带 1 物理像素羽化，外圈顶点保留 RGB、alpha 为 0（避免暗边）。仅限渲染线程：临时缓冲为静态共享。</p>
 */
public final class TabletCanvas {
    private static final int MIN_SEGMENTS = 4;
    private static final int MAX_SEGMENTS = 24;
    private static final int MAX_CLIP_DEPTH = 8;
    private static final float MIN_MITER_DOT = 0.3f;
    private static final float POINT_EPSILON = 1.0e-4f;
    private static final float AREA_EPSILON = 1.0e-7f;
    private static final float[] STROKE_COLUMN_ALPHA = {0f, 1f, 1f, 0f};

    private static final int SLOT_A = 0;
    private static final int SLOT_B = 1;
    private static final int SLOT_C = 2;
    private static final int SLOT_D = 3;
    private static final int SLOT_POINTS = 4;
    private static final int SLOT_DIRECTIONS = 5;
    private static final int SLOT_GRID = 6;

    // Quarter-circle tables indexed by segment count (0..90 degrees inclusive).
    // 按分段数索引的四分之一圆三角函数表（含 0 与 90 度）。
    private static final float[][] QUARTER_COS = new float[MAX_SEGMENTS + 1][];
    private static final float[][] QUARTER_SIN = new float[MAX_SEGMENTS + 1][];
    private static final float[][] SCRATCH = new float[7][];

    static {
        for (int segments = MIN_SEGMENTS; segments <= MAX_SEGMENTS; segments++) {
            float[] cos = new float[segments + 1];
            float[] sin = new float[segments + 1];
            for (int k = 0; k <= segments; k++) {
                double angle = Math.PI * 0.5 * k / segments;
                cos[k] = (float) Math.cos(angle);
                sin[k] = (float) Math.sin(angle);
            }
            cos[segments] = 0f;
            sin[segments] = 1f;
            QUARTER_COS[segments] = cos;
            QUARTER_SIN[segments] = sin;
        }
    }

    private final DrawContext context;
    private final int pixelScale;
    private final int framebufferHeight;
    private final float px;
    private final float[] lineScratch = new float[4];
    // Physical top-left-origin clip rects (x0, y0, x1, y1) per depth. 各层物理像素裁剪矩形（左上原点）。
    private final int[] clipStack = new int[MAX_CLIP_DEPTH * 4];
    private int clipDepth;

    private Matrix4f matrix;
    private VertexConsumer buffer;
    private int paintTop;
    private int paintBottom;
    private float paintY0;
    private float paintInvHeight;
    private boolean paintGradient;

    public TabletCanvas(DrawContext context, int pixelScale, int framebufferHeight) {
        this.context = context;
        this.pixelScale = Math.max(1, pixelScale);
        this.framebufferHeight = framebufferHeight;
        this.px = 1f / this.pixelScale;
    }

    public DrawContext context() {
        return context;
    }

    /** One physical pixel in logical units. 一个物理像素对应的逻辑长度。 */
    public float px() {
        return px;
    }

    // ------------------------------------------------------------------------------------------------ fills

    public void rect(float x, float y, float w, float h, int color) {
        if (!(w > 0f) || !(h > 0f) || transparent(color)) {
            return;
        }
        begin();
        quad(x, y, color, x, y + h, color, x + w, y + h, color, x + w, y, color);
        end();
    }

    public void gradientV(float x, float y, float w, float h, int top, int bottom) {
        if (!(w > 0f) || !(h > 0f) || (transparent(top) && transparent(bottom))) {
            return;
        }
        begin();
        quad(x, y, top, x, y + h, bottom, x + w, y + h, bottom, x + w, y, top);
        end();
    }

    /**
     * Axis-aligned quad with one colour per corner and no feather. Drawn as a four-triangle fan around a centre
     * vertex carrying the corner average, so the fill approximates bilinear interpolation without a diagonal bias;
     * each edge stays a plain lerp of its two corners, so adjacent quads sharing corner colours meet seamlessly.
     * 每个角各一种颜色的轴对齐四边形，无羽化。以携带四角平均色的中心顶点做四三角形扇，近似双线性插值且无对角线偏向；
     * 每条边仍是两端角色的线性插值，因此共享角色的相邻四边形接缝无痕。
     */
    public void gradientQuad(float x0, float y0, float x1, float y1, int topLeft, int topRight, int bottomLeft,
                             int bottomRight) {
        if (!(x1 > x0) || !(y1 > y0)
                || (transparent(topLeft) && transparent(topRight) && transparent(bottomLeft) && transparent(bottomRight))) {
            return;
        }
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;
        int center = TabletTheme.mix(TabletTheme.mix(topLeft, bottomRight, 0.5f),
                TabletTheme.mix(topRight, bottomLeft, 0.5f), 0.5f);
        begin();
        tri(x0, y0, topLeft, x0, y1, bottomLeft, cx, cy, center);
        tri(x0, y1, bottomLeft, x1, y1, bottomRight, cx, cy, center);
        tri(x1, y1, bottomRight, x1, y0, topRight, cx, cy, center);
        tri(x1, y0, topRight, x0, y0, topLeft, cx, cy, center);
        end();
    }

    /** Rounded rectangle; {@code r <= 0} is a plain rect, {@code r} is clamped to half the short side. */
    public void roundRect(float x, float y, float w, float h, float r, int color) {
        if (!(w > 0f) || !(h > 0f) || transparent(color)) {
            return;
        }
        if (!(r > 0f)) {
            rect(x, y, w, h, color);
            return;
        }
        begin();
        solid(color);
        fillRoundRect(x, y, w, h, r);
        end();
    }

    public void roundRectGradientV(float x, float y, float w, float h, float r, int top, int bottom) {
        if (!(w > 0f) || !(h > 0f) || (transparent(top) && transparent(bottom))) {
            return;
        }
        if (!(r > 0f)) {
            gradientV(x, y, w, h, top, bottom);
            return;
        }
        begin();
        gradient(top, bottom, y, h);
        fillRoundRect(x, y, w, h, r);
        end();
    }

    /** A true ring between the outer rounded contour and the inset one (inner radius r - thickness). */
    public void roundRectOutline(float x, float y, float w, float h, float r, float thickness, int color) {
        if (!(w > 0f) || !(h > 0f) || !(thickness > 0f) || transparent(color)) {
            return;
        }
        if (thickness < px) {
            color = TabletTheme.multiplyAlpha(color, thickness / px);
            thickness = px;
        }
        float minSide = Math.min(w, h);
        if (thickness * 2f >= minSide) {
            roundRect(x, y, w, h, r, color);
            return;
        }
        r = clamp(r, 0f, minSide * 0.5f);
        int segments = segments(r);
        float t = thickness;
        float[] outer = scratch(SLOT_A, contourFloats(segments));
        float[] outerFeather = scratch(SLOT_B, contourFloats(segments));
        float[] inner = scratch(SLOT_C, contourFloats(segments));
        float[] innerFeather = scratch(SLOT_D, contourFloats(segments));
        int n = roundRectContour(outer, x, y, w, h, r, segments);
        roundRectContour(outerFeather, x - px, y - px, w + 2f * px, h + 2f * px, r + px, segments);
        roundRectContour(inner, x + t, y + t, w - 2f * t, h - 2f * t, Math.max(0f, r - t), segments);
        roundRectContour(innerFeather, x + t + px, y + t + px, w - 2f * (t + px), h - 2f * (t + px),
                Math.max(0f, r - t - px), segments);
        begin();
        solid(color);
        band(inner, 0, 1f, outer, 0, 1f, n, true);
        band(outer, 0, 1f, outerFeather, 0, 0f, n, true);
        band(innerFeather, 0, 0f, inner, 0, 1f, n, true);
        end();
    }

    public void circle(float cx, float cy, float radius, int color) {
        if (!(radius > 0f) || transparent(color)) {
            return;
        }
        begin();
        solid(color);
        fillCircle(cx, cy, radius);
        end();
    }

    /**
     * Ring whose OUTER radius is {@code radius}; the band extends {@code thickness} inward.
     * 外半径为 radius，环带向内延伸 thickness。
     */
    public void ring(float cx, float cy, float radius, float thickness, int color) {
        if (!(radius > 0f) || !(thickness > 0f) || transparent(color)) {
            return;
        }
        if (thickness < px) {
            color = TabletTheme.multiplyAlpha(color, thickness / px);
            thickness = px;
        }
        if (thickness >= radius) {
            circle(cx, cy, radius, color);
            return;
        }
        int n = 4 * segments(radius);
        float[] outer = scratch(SLOT_A, n * 2);
        float[] outerFeather = scratch(SLOT_B, n * 2);
        float[] inner = scratch(SLOT_C, n * 2);
        float[] innerFeather = scratch(SLOT_D, n * 2);
        circleContour(outer, cx, cy, radius, n);
        circleContour(outerFeather, cx, cy, radius + px, n);
        circleContour(inner, cx, cy, radius - thickness, n);
        circleContour(innerFeather, cx, cy, Math.max(0f, radius - thickness - px), n);
        begin();
        solid(color);
        band(inner, 0, 1f, outer, 0, 1f, n, true);
        band(outer, 0, 1f, outerFeather, 0, 0f, n, true);
        band(innerFeather, 0, 0f, inner, 0, 1f, n, true);
        end();
    }

    /**
     * Arc band with OUTER radius {@code radius}. 0 degrees = 12 o'clock, positive sweep = clockwise on screen;
     * a negative sweep runs counter-clockwise; |sweep| >= 360 draws the full ring. Ends are feathered too.
     * 外半径为 radius 的圆弧带。0 度 = 12 点方向，正 sweep 为屏幕顺时针；|sweep| >= 360 时绘制整圆环。
     */
    public void arc(float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, int color) {
        if (!(radius > 0f) || !(thickness > 0f) || sweepDeg == 0f || Float.isNaN(sweepDeg) || transparent(color)) {
            return;
        }
        if (Math.abs(sweepDeg) >= 360f) {
            ring(cx, cy, radius, thickness, color);
            return;
        }
        if (sweepDeg < 0f) {
            startDeg += sweepDeg;
            sweepDeg = -sweepDeg;
        }
        if (thickness < px) {
            color = TabletTheme.multiplyAlpha(color, thickness / px);
            thickness = px;
        }
        float outerRadius = radius;
        float innerRadius = Math.max(0f, radius - thickness);
        float[] radii = {Math.max(0f, innerRadius - px), innerRadius, outerRadius, outerRadius + px};
        int steps = Math.max(2, (int) Math.ceil(4f * segments(outerRadius) * sweepDeg / 360f));
        float capDeg = (float) Math.toDegrees(px / Math.max(px, (innerRadius + outerRadius) * 0.5f));
        begin();
        solid(color);
        // Rows -1 and steps+1 are the transparent end-cap feathers. 第 -1 行与第 steps+1 行是透明的端点羽化。
        for (int a = -1; a <= steps; a++) {
            double angle0 = Math.toRadians(arcAngle(a, steps, startDeg, sweepDeg, capDeg));
            double angle1 = Math.toRadians(arcAngle(a + 1, steps, startDeg, sweepDeg, capDeg));
            float sin0 = (float) Math.sin(angle0);
            float cos0 = (float) Math.cos(angle0);
            float sin1 = (float) Math.sin(angle1);
            float cos1 = (float) Math.cos(angle1);
            float row0 = a < 0 ? 0f : 1f;
            float row1 = a + 1 > steps ? 0f : 1f;
            for (int j = 0; j < 3; j++) {
                float ra = radii[j];
                float rb = radii[j + 1];
                float fa = STROKE_COLUMN_ALPHA[j];
                float fb = STROKE_COLUMN_ALPHA[j + 1];
                float ax = cx + ra * sin0;
                float ay = cy - ra * cos0;
                float bx = cx + rb * sin0;
                float by = cy - rb * cos0;
                float ccx = cx + rb * sin1;
                float ccy = cy - rb * cos1;
                float dx = cx + ra * sin1;
                float dy = cy - ra * cos1;
                quad(ax, ay, colorAt(ay, fa * row0), bx, by, colorAt(by, fb * row0),
                        ccx, ccy, colorAt(ccy, fb * row1), dx, dy, colorAt(dy, fa * row1));
            }
        }
        end();
    }

    /** Straight segment with butt ends (feathered on all sides). 平头线段（四边羽化）。 */
    public void line(float x1, float y1, float x2, float y2, float thickness, int color) {
        float[] xy = lineScratch;
        xy[0] = x1;
        xy[1] = y1;
        xy[2] = x2;
        xy[3] = y2;
        strokePath(xy, 2, thickness, color, false, false);
    }

    /** Segment with round caps; handy for icons. 带圆头的线段，常用于图标。 */
    public void capsule(float x1, float y1, float x2, float y2, float thickness, int color) {
        float[] xy = lineScratch;
        xy[0] = x1;
        xy[1] = y1;
        xy[2] = x2;
        xy[3] = y2;
        strokePath(xy, 2, thickness, color, true, false);
    }

    /** Open polyline, mitred joins, butt ends; no overdraw at joins (safe with translucent colours). */
    public void polyline(float[] xy, float thickness, int color) {
        strokePath(xy, xy.length / 2, thickness, color, false, false);
    }

    /**
     * Convex polygon (or star-shaped around its first vertex), fan-triangulated from vertex 0; concave input renders
     * wrong.
     * 凸多边形（或以第一个顶点为中心的星形），从顶点 0 扇形三角化；凹多边形会绘制错误。
     */
    public void polygon(float[] xy, int color) {
        fillPolygon(xy, xy.length / 2, false, 0f, 0f, color);
    }

    /**
     * Soft outer shadow: {@code color} inside and at the edge, easing to transparent over {@code blur}.
     * 柔和外阴影：内部及边缘为 color，在 blur 距离内渐隐为透明。
     */
    public void shadow(float x, float y, float w, float h, float r, float blur, int color) {
        if (!(w > 0f) || !(h > 0f) || transparent(color)) {
            return;
        }
        if (!(blur > 0f)) {
            roundRect(x, y, w, h, r, color);
            return;
        }
        r = clamp(r, 0f, Math.min(w, h) * 0.5f);
        int segments = segments(r + blur);
        float[] previous = scratch(SLOT_A, contourFloats(segments));
        float[] next = scratch(SLOT_B, contourFloats(segments));
        int n = roundRectContour(previous, x, y, w, h, r, segments);
        begin();
        solid(color);
        fan(x + w * 0.5f, y + h * 0.5f, 1f, previous, 0, 1f, n, true);
        final int rings = 4;
        for (int k = 0; k < rings; k++) {
            float f0 = (float) k / rings;
            float f1 = (float) (k + 1) / rings;
            float d = f1 * blur;
            roundRectContour(next, x - d, y - d, w + 2f * d, h + 2f * d, r + d, segments);
            band(previous, 0, falloff(f0), next, 0, falloff(f1), n, true);
            float[] swap = previous;
            previous = next;
            next = swap;
        }
        end();
    }

    /** Radial glow: {@code color} at the centre easing to transparent at {@code radius}. 径向光晕。 */
    public void glow(float cx, float cy, float radius, int color) {
        if (!(radius > 0f) || transparent(color)) {
            return;
        }
        int n = 4 * segments(radius);
        float[] previous = scratch(SLOT_A, n * 2);
        float[] next = scratch(SLOT_B, n * 2);
        final int rings = 5;
        circleContour(previous, cx, cy, radius / rings, n);
        begin();
        solid(color);
        fan(cx, cy, 1f, previous, 0, falloff(1f / rings), n, true);
        for (int k = 1; k < rings; k++) {
            float f0 = (float) k / rings;
            float f1 = (float) (k + 1) / rings;
            circleContour(next, cx, cy, radius * f1, n);
            band(previous, 0, falloff(f0), next, 0, falloff(f1), n, true);
            float[] swap = previous;
            previous = next;
            next = swap;
        }
        end();
    }

    /**
     * Paints the four regions of the square (x, y, size) that lie outside its rounded corners with
     * {@code surfaceColor}, feathered inward, so square content (skins) looks rounded on that surface.
     * 用 surfaceColor 覆盖正方形四角圆弧之外的区域（向内羽化），让方形内容（皮肤头像）在该底色上呈圆角。
     */
    public void cornerMask(float x, float y, float size, float r, int surfaceColor) {
        if (!(size > 0f) || !(r > 0f) || transparent(surfaceColor)) {
            return;
        }
        r = Math.min(r, size * 0.5f);
        int segments = segments(r);
        float[] arc = scratch(SLOT_A, contourFloats(segments));
        float[] feather = scratch(SLOT_B, contourFloats(segments));
        roundRectContour(arc, x, y, size, size, r, segments);
        roundRectContour(feather, x + px, y + px, size - 2f * px, size - 2f * px, Math.max(0f, r - px), segments);
        int perCorner = segments + 1;
        begin();
        solid(surfaceColor);
        for (int corner = 0; corner < 4; corner++) {
            float cornerX = corner == 0 || corner == 3 ? x : x + size;
            float cornerY = corner < 2 ? y : y + size;
            int base = corner * perCorner;
            for (int k = 0; k < segments; k++) {
                int i = (base + k) * 2;
                tri(cornerX, cornerY, surfaceColor, arc[i], arc[i + 1], surfaceColor, arc[i + 2], arc[i + 3], surfaceColor);
            }
            band(feather, base, 0f, arc, base, 1f, perCorner, false);
        }
        end();
    }

    /** Skin face via {@link TabletPlayerRow} then {@link #cornerMask}. 先画皮肤头像，再用底色遮出圆角。 */
    public void avatar(UUID uuid, String name, int x, int y, int size, float r, int surfaceColor) {
        if (size <= 0) {
            return;
        }
        flush();
        TabletPlayerRow.drawAvatar(context, uuid, name, x, y, size);
        cornerMask(x, y, size, r, surfaceColor);
    }

    // ------------------------------------------------------------------------------------------------ text

    public int text(TextRenderer tr, String s, int x, int y, int color) {
        int width = tr.getWidth(s);
        if (drawableText(color)) {
            context.drawText(tr, s, x, y, color, false);
        }
        return width;
    }

    public int text(TextRenderer tr, OrderedText s, int x, int y, int color) {
        int width = tr.getWidth(s);
        if (drawableText(color)) {
            context.drawText(tr, s, x, y, color, false);
        }
        return width;
    }

    public int textCentered(TextRenderer tr, String s, int centerX, int y, int color) {
        return text(tr, s, centerX - tr.getWidth(s) / 2, y, color);
    }

    public int textRight(TextRenderer tr, String s, int rightX, int y, int color) {
        return text(tr, s, rightX - tr.getWidth(s), y, color);
    }

    /** Trims to {@code maxWidth}, appending "…" when anything was cut. 超宽时截断并追加“…”。 */
    public static String trim(TextRenderer tr, String s, int maxWidth) {
        if (tr.getWidth(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int room = maxWidth - tr.getWidth(ellipsis);
        if (room < 0) {
            return "";
        }
        return tr.trimToWidth(s, room).stripTrailing() + ellipsis;
    }

    // ------------------------------------------------------------------------------------------------ clipping

    /**
     * Scissor in PHYSICAL pixels (logical * pixelScale; ignores the matrix, so pass canvas-space logical coords).
     * Nested pushes intersect with the enclosing clip. Do not interleave with {@code DrawContext.enableScissor}.
     * 以物理像素设置裁剪（逻辑坐标 * pixelScale，不经过矩阵，因此须传入画布空间逻辑坐标）。嵌套时与外层求交。
     * 不要与 DrawContext.enableScissor 交错使用。
     */
    public void pushClip(float x, float y, float w, float h) {
        flush();
        int x0 = (int) Math.floor(x * pixelScale);
        int y0 = (int) Math.floor(y * pixelScale);
        int x1 = (int) Math.ceil((x + Math.max(0f, w)) * pixelScale);
        int y1 = (int) Math.ceil((y + Math.max(0f, h)) * pixelScale);
        if (clipDepth > 0) {
            int top = (Math.min(clipDepth, MAX_CLIP_DEPTH) - 1) * 4;
            x0 = Math.max(x0, clipStack[top]);
            y0 = Math.max(y0, clipStack[top + 1]);
            x1 = Math.min(x1, clipStack[top + 2]);
            y1 = Math.min(y1, clipStack[top + 3]);
        }
        x1 = Math.max(x0, x1);
        y1 = Math.max(y0, y1);
        // Misuse beyond MAX_CLIP_DEPTH reuses the deepest slot (still intersected, never unbounded).
        // 超过 MAX_CLIP_DEPTH 属误用：复用最深一层（仍会求交，不会失去裁剪）。
        int slot = Math.min(clipDepth, MAX_CLIP_DEPTH - 1) * 4;
        clipStack[slot] = x0;
        clipStack[slot + 1] = y0;
        clipStack[slot + 2] = x1;
        clipStack[slot + 3] = y1;
        clipDepth++;
        applyClip(slot);
    }

    public void popClip() {
        if (clipDepth <= 0) {
            return;
        }
        flush();
        clipDepth--;
        if (clipDepth == 0) {
            RenderSystem.disableScissor();
        } else {
            applyClip((Math.min(clipDepth, MAX_CLIP_DEPTH) - 1) * 4);
        }
    }

    public void flush() {
        context.draw();
    }

    // ------------------------------------------------------------------------------------------------ package API

    /** Stroke through {@code count} points of {@code xy}. 沿 xy 的前 count 个点描边。 */
    void strokePath(float[] xy, int count, float thickness, int color, boolean roundCaps, boolean closed) {
        if (count <= 0 || !(thickness > 0f) || transparent(color)) {
            return;
        }
        if (thickness < px) {
            color = TabletTheme.multiplyAlpha(color, thickness / px);
            thickness = px;
        }
        float[] p = scratch(SLOT_POINTS, count * 2);
        int n = dedupe(xy, count, p);
        if (closed && n > 1 && samePoint(p[0], p[1], p[2 * n - 2], p[2 * n - 1])) {
            n--;
        }
        float half = thickness * 0.5f;
        if (n < 2) {
            if (roundCaps && n == 1) {
                begin();
                solid(color);
                fillCircle(p[0], p[1], half);
                end();
            }
            return;
        }
        if (closed && n < 3) {
            closed = false;
        }
        boolean butt = !closed && !roundCaps;
        int segmentCount = closed ? n : n - 1;
        float[] dir = scratch(SLOT_DIRECTIONS, segmentCount * 2);
        for (int s = 0; s < segmentCount; s++) {
            int e = (s + 1) % n;
            float dx = p[e * 2] - p[s * 2];
            float dy = p[e * 2 + 1] - p[s * 2 + 1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            dir[s * 2] = dx / len;
            dir[s * 2 + 1] = dy / len;
        }
        int rows = butt ? n + 2 : n;
        int first = butt ? 1 : 0;
        // Grid row = 4 columns (outer feather, edge, edge, outer feather) along the left normal (-dy, dx).
        // 网格每行 4 列（外羽化、边、边、外羽化），沿左法线 (-dy, dx) 排布。
        float[] g = scratch(SLOT_GRID, rows * 8);
        for (int i = 0; i < n; i++) {
            int prev;
            int next;
            if (closed) {
                prev = (i - 1 + n) % n;
                next = i;
            } else {
                prev = i == 0 ? 0 : i - 1;
                next = i == n - 1 ? n - 2 : i;
            }
            float nxPrev = -dir[prev * 2 + 1];
            float nyPrev = dir[prev * 2];
            float nxNext = -dir[next * 2 + 1];
            float nyNext = dir[next * 2];
            float mx = nxPrev + nxNext;
            float my = nyPrev + nyNext;
            float len = (float) Math.sqrt(mx * mx + my * my);
            float scale;
            if (len < 1.0e-3f) {
                mx = nxNext;
                my = nyNext;
                scale = 1f;
            } else {
                mx /= len;
                my /= len;
                scale = 1f / Math.max(MIN_MITER_DOT, mx * nxNext + my * nyNext);
            }
            float inner = half * scale;
            float outer = (half + px) * scale;
            float x = p[i * 2];
            float y = p[i * 2 + 1];
            int row = (first + i) * 8;
            g[row] = x + mx * outer;
            g[row + 1] = y + my * outer;
            g[row + 2] = x + mx * inner;
            g[row + 3] = y + my * inner;
            g[row + 4] = x - mx * inner;
            g[row + 5] = y - my * inner;
            g[row + 6] = x - mx * outer;
            g[row + 7] = y - my * outer;
        }
        if (butt) {
            shiftRow(g, 1, 0, -dir[0] * px, -dir[1] * px);
            int last = segmentCount - 1;
            shiftRow(g, n, n + 1, dir[last * 2] * px, dir[last * 2 + 1] * px);
        }
        begin();
        solid(color);
        int spans = closed ? rows : rows - 1;
        for (int r = 0; r < spans; r++) {
            int r2 = (r + 1) % rows;
            float rowAlpha0 = butt && (r == 0 || r == rows - 1) ? 0f : 1f;
            float rowAlpha1 = butt && (r2 == 0 || r2 == rows - 1) ? 0f : 1f;
            int a = r * 8;
            int b = r2 * 8;
            for (int j = 0; j < 3; j++) {
                int c0 = j * 2;
                int c1 = (j + 1) * 2;
                float f0 = STROKE_COLUMN_ALPHA[j];
                float f1 = STROKE_COLUMN_ALPHA[j + 1];
                quad(g[a + c0], g[a + c0 + 1], colorAt(g[a + c0 + 1], f0 * rowAlpha0),
                        g[a + c1], g[a + c1 + 1], colorAt(g[a + c1 + 1], f1 * rowAlpha0),
                        g[b + c1], g[b + c1 + 1], colorAt(g[b + c1 + 1], f1 * rowAlpha1),
                        g[b + c0], g[b + c0 + 1], colorAt(g[b + c0 + 1], f0 * rowAlpha1));
            }
        }
        if (!closed && roundCaps) {
            // Start cap sweeps from the left normal through -direction; end cap from the right normal through
            // +direction, so both meet the strip's edge vertices exactly (no overdraw).
            // 起点圆头从左法线经反方向扫过；终点圆头从右法线经正方向扫过，与描边边缘顶点严丝合缝（无重绘）。
            roundCap(p[0], p[1], (float) Math.atan2(dir[0], -dir[1]), half);
            int last = segmentCount - 1;
            roundCap(p[(n - 1) * 2], p[(n - 1) * 2 + 1], (float) Math.atan2(-dir[last * 2], dir[last * 2 + 1]), half);
        }
        end();
    }

    /** Fills the first {@code count} points of {@code xy} fanned from vertex 0. 从第 0 个顶点扇形填充。 */
    void fillPath(float[] xy, int count, int color) {
        fillPolygon(xy, count, false, 0f, 0f, color);
    }

    /** Fills a polygon star-shaped around (cx, cy), fanned from that centre. 以 (cx, cy) 为中心扇形填充星形多边形。 */
    void fillStar(float[] xy, int count, float cx, float cy, int color) {
        fillPolygon(xy, count, true, cx, cy, color);
    }

    // ------------------------------------------------------------------------------------------------ internals

    private void begin() {
        matrix = context.getMatrices().peek().getPositionMatrix();
        buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
    }

    private void end() {
        buffer = null;
        matrix = null;
        context.draw();
    }

    private void solid(int color) {
        paintTop = color;
        paintBottom = color;
        paintGradient = false;
    }

    private void gradient(int top, int bottom, float y, float h) {
        paintTop = top;
        paintBottom = bottom;
        paintY0 = y;
        paintInvHeight = h > 0f ? 1f / h : 0f;
        paintGradient = true;
    }

    private int colorAt(float y, float alphaFactor) {
        int color = paintGradient ? TabletTheme.mix(paintTop, paintBottom, (y - paintY0) * paintInvHeight) : paintTop;
        return TabletTheme.multiplyAlpha(color, alphaFactor);
    }

    private void fillRoundRect(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) * 0.5f);
        int segments = segments(r);
        float[] contour = scratch(SLOT_A, contourFloats(segments));
        float[] feather = scratch(SLOT_B, contourFloats(segments));
        int n = roundRectContour(contour, x, y, w, h, r, segments);
        roundRectContour(feather, x - px, y - px, w + 2f * px, h + 2f * px, r + px, segments);
        fan(x + w * 0.5f, y + h * 0.5f, 1f, contour, 0, 1f, n, true);
        band(contour, 0, 1f, feather, 0, 0f, n, true);
    }

    private void fillCircle(float cx, float cy, float radius) {
        int n = 4 * segments(radius);
        float[] contour = scratch(SLOT_A, n * 2);
        float[] feather = scratch(SLOT_B, n * 2);
        circleContour(contour, cx, cy, radius, n);
        circleContour(feather, cx, cy, radius + px, n);
        fan(cx, cy, 1f, contour, 0, 1f, n, true);
        band(contour, 0, 1f, feather, 0, 0f, n, true);
    }

    private void fillPolygon(float[] xy, int count, boolean useCenter, float cx, float cy, int color) {
        if (count < 3 || transparent(color)) {
            return;
        }
        float[] p = scratch(SLOT_POINTS, count * 2);
        int n = dedupe(xy, count, p);
        if (n > 1 && samePoint(p[0], p[1], p[2 * n - 2], p[2 * n - 1])) {
            n--;
        }
        if (n < 3) {
            return;
        }
        float area2 = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area2 += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        if (!(Math.abs(area2) > AREA_EPSILON)) {
            return;
        }
        // In y-down coordinates a positive area means clockwise on screen; outward normal is then (dy, -dx).
        // y 轴向下时面积为正表示屏幕顺时针，此时外法线为 (dy, -dx)。
        float sign = area2 > 0f ? 1f : -1f;
        float[] dir = scratch(SLOT_DIRECTIONS, n * 2);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float dx = p[j * 2] - p[i * 2];
            float dy = p[j * 2 + 1] - p[i * 2 + 1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            dir[i * 2] = sign * dy / len;
            dir[i * 2 + 1] = -sign * dx / len;
        }
        float[] feather = scratch(SLOT_B, n * 2);
        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            float mx = dir[prev * 2] + dir[i * 2];
            float my = dir[prev * 2 + 1] + dir[i * 2 + 1];
            float len = (float) Math.sqrt(mx * mx + my * my);
            float offset;
            if (len < 1.0e-3f) {
                mx = dir[i * 2];
                my = dir[i * 2 + 1];
                offset = px;
            } else {
                mx /= len;
                my /= len;
                offset = px / Math.max(MIN_MITER_DOT, mx * dir[i * 2] + my * dir[i * 2 + 1]);
            }
            feather[i * 2] = p[i * 2] + mx * offset;
            feather[i * 2 + 1] = p[i * 2 + 1] + my * offset;
        }
        begin();
        solid(color);
        if (useCenter) {
            fan(cx, cy, 1f, p, 0, 1f, n, true);
        } else {
            for (int i = 1; i < n - 1; i++) {
                tri(p[0], p[1], color, p[i * 2], p[i * 2 + 1], color, p[i * 2 + 2], p[i * 2 + 3], color);
            }
        }
        band(p, 0, 1f, feather, 0, 0f, n, true);
        end();
    }

    private void roundCap(float cx, float cy, float startAngle, float half) {
        int steps = 2 * Math.max(MIN_SEGMENTS, segments(half));
        float outer = half + px;
        float cos0 = (float) Math.cos(startAngle);
        float sin0 = (float) Math.sin(startAngle);
        int center = colorAt(cy, 1f);
        for (int k = 1; k <= steps; k++) {
            double angle = startAngle + Math.PI * k / steps;
            float cos1 = (float) Math.cos(angle);
            float sin1 = (float) Math.sin(angle);
            float ax = cx + cos0 * half;
            float ay = cy + sin0 * half;
            float bx = cx + cos1 * half;
            float by = cy + sin1 * half;
            float afx = cx + cos0 * outer;
            float afy = cy + sin0 * outer;
            float bfx = cx + cos1 * outer;
            float bfy = cy + sin1 * outer;
            tri(cx, cy, center, ax, ay, colorAt(ay, 1f), bx, by, colorAt(by, 1f));
            quad(ax, ay, colorAt(ay, 1f), afx, afy, colorAt(afy, 0f), bfx, bfy, colorAt(bfy, 0f), bx, by, colorAt(by, 1f));
            cos0 = cos1;
            sin0 = sin1;
        }
    }

    private void band(float[] a, int aOffset, float aAlpha, float[] b, int bOffset, float bAlpha, int n, boolean closed) {
        int spans = closed ? n : n - 1;
        for (int i = 0; i < spans; i++) {
            int j = i + 1 == n ? 0 : i + 1;
            int ai = (aOffset + i) * 2;
            int aj = (aOffset + j) * 2;
            int bi = (bOffset + i) * 2;
            int bj = (bOffset + j) * 2;
            quad(a[ai], a[ai + 1], colorAt(a[ai + 1], aAlpha),
                    b[bi], b[bi + 1], colorAt(b[bi + 1], bAlpha),
                    b[bj], b[bj + 1], colorAt(b[bj + 1], bAlpha),
                    a[aj], a[aj + 1], colorAt(a[aj + 1], aAlpha));
        }
    }

    private void fan(float cx, float cy, float centerAlpha, float[] pts, int offset, float alpha, int n, boolean closed) {
        int center = colorAt(cy, centerAlpha);
        int spans = closed ? n : n - 1;
        for (int i = 0; i < spans; i++) {
            int j = i + 1 == n ? 0 : i + 1;
            int pi = (offset + i) * 2;
            int pj = (offset + j) * 2;
            tri(cx, cy, center, pts[pi], pts[pi + 1], colorAt(pts[pi + 1], alpha),
                    pts[pj], pts[pj + 1], colorAt(pts[pj + 1], alpha));
        }
    }

    private void tri(float ax, float ay, int ac, float bx, float by, int bc, float cx, float cy, int cc) {
        if (((ac | bc | cc) >>> 24) == 0) {
            return;
        }
        float cross = cross(ax, ay, bx, by, cx, cy);
        if (!(Math.abs(cross) > AREA_EPSILON)) {
            return;
        }
        // Canonical winding = DrawContext.fill (TL, BL, BR, TR): cross < 0 in y-down coordinates.
        // 规范绕序与 DrawContext.fill 一致（左上、左下、右下、右上）：y 轴向下时叉积 < 0。
        if (cross < 0f) {
            vertex(ax, ay, ac);
            vertex(bx, by, bc);
            vertex(cx, cy, cc);
            vertex(cx, cy, cc);
        } else {
            vertex(ax, ay, ac);
            vertex(cx, cy, cc);
            vertex(bx, by, bc);
            vertex(bx, by, bc);
        }
    }

    // QUADS split along a-c into (a,b,c) and (c,d,a); both halves must share the canonical winding.
    // QUADS 沿 a-c 拆分为 (a,b,c) 与 (c,d,a)；两半都必须是规范绕序。
    private void quad(float ax, float ay, int ac, float bx, float by, int bc,
                      float cx, float cy, int cc, float dx, float dy, int dc) {
        if (((ac | bc | cc | dc) >>> 24) == 0) {
            return;
        }
        float c1 = cross(ax, ay, bx, by, cx, cy);
        float c2 = cross(ax, ay, cx, cy, dx, dy);
        if (c1 < -AREA_EPSILON && c2 < -AREA_EPSILON) {
            vertex(ax, ay, ac);
            vertex(bx, by, bc);
            vertex(cx, cy, cc);
            vertex(dx, dy, dc);
        } else if (c1 > AREA_EPSILON && c2 > AREA_EPSILON) {
            vertex(ax, ay, ac);
            vertex(dx, dy, dc);
            vertex(cx, cy, cc);
            vertex(bx, by, bc);
        } else {
            tri(ax, ay, ac, bx, by, bc, cx, cy, cc);
            tri(ax, ay, ac, cx, cy, cc, dx, dy, dc);
        }
    }

    private void vertex(float x, float y, int color) {
        buffer.vertex(matrix, x, y, 0f).color(color);
    }

    private void applyClip(int slot) {
        int x0 = clipStack[slot];
        int y0 = clipStack[slot + 1];
        int x1 = clipStack[slot + 2];
        int y1 = clipStack[slot + 3];
        // GL scissor origin is bottom-left. GL 裁剪原点在左下角。
        RenderSystem.enableScissor(x0, framebufferHeight - y1, x1 - x0, y1 - y0);
    }

    private int segments(float radius) {
        int segments = (int) Math.ceil(radius * pixelScale / 1.5f);
        return Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, segments));
    }

    private static int contourFloats(int segments) {
        return 8 * (segments + 1);
    }

    /** Writes 4 * (segments + 1) points clockwise from the left end of the top-left arc. */
    private static int roundRectContour(float[] out, float x, float y, float w, float h, float r, int segments) {
        if (w < 0f) {
            x += w * 0.5f;
            w = 0f;
        }
        if (h < 0f) {
            y += h * 0.5f;
            h = 0f;
        }
        r = clamp(r, 0f, Math.min(w, h) * 0.5f);
        float[] cos = QUARTER_COS[segments];
        float[] sin = QUARTER_SIN[segments];
        float left = x + r;
        float right = x + w - r;
        float top = y + r;
        float bottom = y + h - r;
        int i = 0;
        for (int k = 0; k <= segments; k++) {
            out[i++] = left - r * cos[k];
            out[i++] = top - r * sin[k];
        }
        for (int k = 0; k <= segments; k++) {
            out[i++] = right + r * sin[k];
            out[i++] = top - r * cos[k];
        }
        for (int k = 0; k <= segments; k++) {
            out[i++] = right + r * cos[k];
            out[i++] = bottom + r * sin[k];
        }
        for (int k = 0; k <= segments; k++) {
            out[i++] = left - r * sin[k];
            out[i++] = bottom + r * cos[k];
        }
        return 4 * (segments + 1);
    }

    private static void circleContour(float[] out, float cx, float cy, float r, int n) {
        for (int i = 0; i < n; i++) {
            double angle = Math.PI * 2.0 * i / n;
            out[i * 2] = cx + r * (float) Math.cos(angle);
            out[i * 2 + 1] = cy + r * (float) Math.sin(angle);
        }
    }

    private static float arcAngle(int index, int steps, float startDeg, float sweepDeg, float capDeg) {
        if (index < 0) {
            return startDeg - capDeg;
        }
        if (index > steps) {
            return startDeg + sweepDeg + capDeg;
        }
        return startDeg + sweepDeg * index / steps;
    }

    private static void shiftRow(float[] grid, int fromRow, int toRow, float dx, float dy) {
        int from = fromRow * 8;
        int to = toRow * 8;
        for (int c = 0; c < 8; c += 2) {
            grid[to + c] = grid[from + c] + dx;
            grid[to + c + 1] = grid[from + c + 1] + dy;
        }
    }

    private static int dedupe(float[] xy, int count, float[] out) {
        int n = 0;
        int limit = Math.min(count, xy.length / 2);
        for (int i = 0; i < limit; i++) {
            float x = xy[i * 2];
            float y = xy[i * 2 + 1];
            if (n > 0 && samePoint(x, y, out[n * 2 - 2], out[n * 2 - 1])) {
                continue;
            }
            out[n * 2] = x;
            out[n * 2 + 1] = y;
            n++;
        }
        return n;
    }

    private static boolean samePoint(float ax, float ay, float bx, float by) {
        return Math.abs(ax - bx) < POINT_EPSILON && Math.abs(ay - by) < POINT_EPSILON;
    }

    private static float cross(float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static float falloff(float f) {
        float inverse = 1f - f;
        return inverse * inverse;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static boolean transparent(int color) {
        return (color >>> 24) == 0;
    }

    // TextRenderer turns alpha < 4 into fully opaque, so near-invisible text must be skipped instead.
    // TextRenderer 会把 alpha < 4 的颜色视为完全不透明，因此近乎透明的文字需直接跳过。
    private static boolean drawableText(int color) {
        return (color >>> 24) >= 4;
    }

    private static float[] scratch(int slot, int floats) {
        float[] array = SCRATCH[slot];
        if (array == null || array.length < floats) {
            array = new float[Math.max(256, Math.max(floats, array == null ? 0 : array.length * 2))];
            SCRATCH[slot] = array;
        }
        return array;
    }
}
