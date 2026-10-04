package annina.sparkstrength.client.role.bomber;

import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import annina.sparkstrength.role.bomber.drone.DroneState;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Locale;

/**
 * Pilot HUD: an FPV "camera feed" lens (vignette, scanlines, boot fade, static) under the whole HUD, plus the
 * instruments (reticle, horizon, drop marker, telemetry, hints) in the main HUD pass. Pure client presentation in the
 * tablet's "Nightline" palette; it reads only the piloted drone's tracked data and local prediction.
 * 驾驶 HUD：整套 HUD 之下的 FPV“图传”镜头层（暗角、扫描线、开机淡入、雪花），以及主 HUD 阶段的仪表（准星、地平线、投弹点、
 * 遥测、操作提示）。纯客户端表现，沿用平板的“Nightline”配色；只读取所驾驶无人机的追踪数据与本地预测。
 */
public final class DronePilotHud {
    static final int WARNING = TabletTheme.WARNING;
    private static final int PANEL = 0xB80B0D12;
    private static final int PANEL_EDGE = 0x2EFFFFFF;
    private static final int KEYCAP = 0x30FFFFFF;
    private static final int MARGIN = 10;
    private static final int LOW_BATTERY_PERCENT = 20;
    private static final long BOOT_MS = 450L;

    // World->screen projection captured at the end of the world pass. / 世界渲染结束时捕获的世界到屏幕投影。
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static Vec3d projectionCamera = Vec3d.ZERO;
    private static boolean projectionValid;

    private DronePilotHud() {
    }

    static int accent(@Nullable DroneKind kind) {
        return kind == DroneKind.BOMB ? TabletTheme.KILLER.base() : TabletTheme.MONITOR.base();
    }

    private static int accentPale(@Nullable DroneKind kind) {
        return kind == DroneKind.BOMB ? TabletTheme.KILLER.pale() : TabletTheme.MONITOR.pale();
    }

    static void captureProjection(WorldRenderContext context) {
        if (!DronePilotClient.isViewingDrone()) {
            projectionValid = false;
            return;
        }
        VIEW.set(context.positionMatrix());
        PROJECTION.set(context.projectionMatrix());
        projectionCamera = context.camera().getPos();
        projectionValid = true;
    }

    // ------------------------------------------------------------------------------------------------ lens layer

    /** First thing in the HUD pass (under every HUD element, survives F1 like any camera effect). / HUD 阶段最先绘制（位于所有 HUD 元素之下）。 */
    public static void renderLens(DrawContext context, RenderTickCounter tickCounter) {
        if (!DronePilotClient.isViewingDrone()) {
            return;
        }
        DroneEntity drone = DronePilotClient.drone();
        MinecraftClient client = MinecraftClient.getInstance();
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        long now = Util.getMeasuringTimeMs();
        int scale = pixelScale(client);
        TabletCanvas c = new TabletCanvas(context, scale, client.getWindow().getFramebufferHeight());

        // Vignette. / 暗角。
        int edge = 0x80000000;
        float vh = h * 0.22F;
        float vw = w * 0.15F;
        context.fillGradient(0, 0, w, (int) vh, edge, 0);
        context.fillGradient(0, h - (int) vh, w, h, 0, edge);
        c.gradientQuad(0, 0, vw, h, edge, 0, edge, 0);
        c.gradientQuad(w - vw, 0, w, h, 0, edge, 0, edge);

        // Low battery: slow red edge pulse. / 低电量：边缘红色缓慢脉动。
        if (drone != null && lowBattery(drone)) {
            float pulse = 0.5F + 0.5F * MathHelper.sin(now / 220.0F);
            int red = TabletTheme.withAlpha(TabletTheme.DANGER, Math.round(70 * pulse));
            context.fillGradient(0, 0, w, (int) (vh * 0.7F), red, 0);
            context.fillGradient(0, h - (int) (vh * 0.7F), w, h, 0, red);
            c.gradientQuad(0, 0, vw * 0.7F, h, red, 0, red, 0);
            c.gradientQuad(w - vw * 0.7F, 0, w, h, 0, red, 0, red);
        }

        // Scanlines in physical pixels, plus one soft sweep band. / 以物理像素绘制的扫描线，外加一条柔和扫光。
        context.getMatrices().push();
        context.getMatrices().scale(1.0F / scale, 1.0F / scale, 1.0F);
        int pw = w * scale;
        int ph = h * scale;
        for (int y = 0; y < ph; y += 3) {
            context.fill(0, y, pw, y + 1, 0x16000000);
        }
        context.getMatrices().pop();
        int sweep = (int) ((now % 5200L) / 5200.0F * (h + 80)) - 40;
        context.fillGradient(0, sweep - 28, w, sweep, 0, 0x0AFFFFFF);
        context.fillGradient(0, sweep, w, sweep + 4, 0x0AFFFFFF, 0);

        // Static while the signal is lost, and a short burst + fade-in on boot. / 信号丢失时满屏雪花；开机时短暂雪花并淡入。
        float boot = MathHelper.clamp((now - DronePilotClient.bootStartedMs()) / (float) BOOT_MS, 0.0F, 1.0F);
        if (DronePilotClient.signalLost()) {
            context.fill(0, 0, w, h, 0x8C05070A);
            noise(context, w, h, now, 520, 150);
        } else if (boot < 1.0F) {
            noise(context, w, h, now, (int) (420 * (1.0F - boot)), 130);
            float fade = 1.0F - boot;
            context.fill(0, 0, w, h, TabletTheme.withAlpha(0xFF05070A, Math.round(255 * fade * fade)));
        }
    }

    private static void noise(DrawContext context, int w, int h, long now, int count, int maxAlpha) {
        if (count <= 0) {
            return;
        }
        Random random = Random.create(now / 45L);
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(Math.max(1, w));
            int y = random.nextInt(Math.max(1, h));
            int len = 1 + random.nextInt(4);
            int grey = 120 + random.nextInt(136);
            int alpha = 30 + random.nextInt(Math.max(1, maxAlpha - 30));
            context.fill(x, y, x + len, y + 1, (alpha << 24) | (grey << 16) | (grey << 8) | grey);
        }
    }

    // ------------------------------------------------------------------------------------------------ instruments

    /** Main HUD pass (hidden with F1 like the rest of the HUD). / 主 HUD 阶段（与其他 HUD 一样随 F1 隐藏）。 */
    public static void renderInstruments(DrawContext context, RenderTickCounter tickCounter) {
        if (!DronePilotClient.isPiloting()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        long now = Util.getMeasuringTimeMs();
        TabletCanvas c = new TabletCanvas(context, pixelScale(client), client.getWindow().getFramebufferHeight());
        float thin = Math.max(c.px(), Math.min(1.0F, 2.0F * c.px()));
        DroneKind kind = DronePilotClient.kind();
        int accent = accent(kind);

        if (DronePilotClient.connecting()) {
            statusPill(context, c, tr, w / 2.0F, h / 2.0F + 30, Text.translatable("hud.sparkstrength.drone.connecting"),
                    accent, now, true);
            return;
        }
        DroneEntity drone = DronePilotClient.drone();
        if (drone == null || !DronePilotClient.isViewingDrone()) {
            return;
        }
        float tickDelta = tickCounter.getTickDelta(true);
        float cx = w / 2.0F;
        float cy = h / 2.0F;

        focusFrame(c, w, h, thin);
        horizon(context, c, tr, drone, w, h, tickDelta, thin);
        reticle(c, cx, cy, accent, kind, now, thin);
        Box left = telemetryPanel(context, c, tr, drone, h, now, thin);
        Box right = rangePanel(context, c, tr, drone, player, w, h, tickDelta, thin);
        float hintsTop = hints(context, c, tr, client, drone, w, h, left, right, thin);
        if (kind == DroneKind.GRENADE && drone.hasPayload()) {
            dropMarker(context, c, tr, drone, w, h, Math.min(h * 0.74F, hintsTop - 8), tickDelta, now, thin);
        }

        if (DronePilotClient.signalLost()) {
            statusPill(context, c, tr, cx, cy + 30, Text.translatable("hud.sparkstrength.drone.signal_lost"),
                    WARNING, now, false);
        } else if (DronePilotClient.landed() && DronePilotClient.displayState(drone) == DroneState.GROUNDED) {
            Text text = Text.translatable("hud.sparkstrength.drone.takeoff_hint",
                    client.options.jumpKey.getBoundKeyLocalizedText());
            statusPill(context, c, tr, cx, cy + 30, text, accent, now, false);
        }

        Text feedback = DronePilotClient.feedbackText(now);
        if (feedback != null) {
            float alpha = DronePilotClient.feedbackAlpha(now);
            int tw = tr.getWidth(feedback);
            float pillW = tw + 16;
            c.roundRect(cx - pillW / 2, cy - 44, pillW, 15, 7.5F, TabletTheme.multiplyAlpha(PANEL, alpha));
            text(context, tr, feedback, Math.round(cx - tw / 2.0F), Math.round(cy - 40),
                    TabletTheme.multiplyAlpha(DronePilotClient.feedbackColor(), alpha));
        }
    }

    /** Camera focus brackets around the centre (clear of Wathe's corner HUD). / 中央取景框角标（避开 Wathe 的角落 HUD）。 */
    private static void focusFrame(TabletCanvas c, int w, int h, float thin) {
        float x0 = w * 0.2F;
        float x1 = w * 0.8F;
        float y0 = h * 0.2F;
        float y1 = h * 0.74F;
        float arm = Math.max(8.0F, Math.min(w, h) * 0.04F);
        int color = TabletTheme.withAlpha(TabletTheme.TEXT, 0x70);
        c.polyline(new float[]{x0, y0 + arm, x0, y0, x0 + arm, y0}, thin, color);
        c.polyline(new float[]{x1 - arm, y0, x1, y0, x1, y0 + arm}, thin, color);
        c.polyline(new float[]{x0, y1 - arm, x0, y1, x0 + arm, y1}, thin, color);
        c.polyline(new float[]{x1 - arm, y1, x1, y1, x1, y1 - arm}, thin, color);
    }

    private static void reticle(TabletCanvas c, float cx, float cy, int accent, @Nullable DroneKind kind, long now,
                                float thin) {
        int tick = TabletTheme.withAlpha(TabletTheme.TEXT, 0xC0);
        c.ring(cx, cy, 6.5F, thin, TabletTheme.withAlpha(accent, 0xE6));
        c.line(cx, cy - 14, cx, cy - 9, thin, tick);
        c.line(cx, cy + 9, cx, cy + 14, thin, tick);
        c.line(cx - 14, cy, cx - 9, cy, thin, tick);
        c.line(cx + 9, cy, cx + 14, cy, thin, tick);
        c.circle(cx, cy, 1.1F, accent);
        // Slow orbit: the feed is live (faster and red for the bomb drone). / 缓慢旋转的弧：画面实时（炸弹无人机更快且为红色）。
        float speed = kind == DroneKind.BOMB ? 0.18F : 0.06F;
        float spin = (now * speed) % 360.0F;
        int orbit = TabletTheme.withAlpha(accent, 0x70);
        c.arc(cx, cy, 19.0F, thin, spin, 36.0F, orbit);
        c.arc(cx, cy, 19.0F, thin, spin + 180.0F, 36.0F, orbit);
    }

    /** Artificial horizon ticks from the projected level line ahead. / 由前方水平线投影得到的人工地平线刻度。 */
    private static void horizon(DrawContext context, TabletCanvas c, TextRenderer tr, DroneEntity drone, int w, int h,
                                float tickDelta, float thin) {
        float yaw = drone.getYaw(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
        Vec3d ahead = projectionCamera.add(-MathHelper.sin(yaw) * 64.0, 0.0, MathHelper.cos(yaw) * 64.0);
        float[] at = project(ahead, w, h);
        if (at == null || Math.abs(at[1] - h / 2.0F) > h * 0.36F) {
            return;
        }
        float cx = w / 2.0F;
        float y = at[1];
        int color = TabletTheme.withAlpha(TabletTheme.TEXT, 0x80);
        c.polyline(new float[]{cx - 54, y + 3, cx - 54, y, cx - 30, y}, thin, color);
        c.polyline(new float[]{cx + 30, y, cx + 54, y, cx + 54, y + 3}, thin, color);
        String pitch = String.format(Locale.ROOT, "%+d°", Math.round(-drone.getPitch(tickDelta)));
        text(context, tr, Text.literal(pitch), Math.round(cx + 58), Math.round(y - 4),
                TabletTheme.withAlpha(TabletTheme.TEXT_2, 0xC0));
    }

    /**
     * Grenade drone: where a released M67 starts falling, straight under the hull. Off-screen (usual when looking ahead)
     * it becomes a down chevron at the bottom of the frame.
     * 投弹无人机：投下的 M67 落点（机身正下方）。离屏时（平视时常见）显示为画面下方的向下箭头。
     */
    private static void dropMarker(DrawContext context, TabletCanvas c, TextRenderer tr, DroneEntity drone, int w, int h,
                                   float bottom, float tickDelta, long now, float thin) {
        double ground = DronePilotClient.groundY();
        if (Double.isNaN(ground)) {
            return;
        }
        Vec3d pos = drone.getLerpedPos(tickDelta);
        double altitude = Math.max(0.0, pos.y - ground);
        Text label = meters(altitude);
        int color = WARNING;
        float pulse = 0.75F + 0.25F * MathHelper.sin(now / 160.0F);
        float[] at = project(new Vec3d(pos.x, ground, pos.z), w, h);
        if (at != null && at[0] > 16 && at[0] < w - 16 && at[1] > 16 && at[1] < bottom) {
            float x = at[0];
            float y = at[1];
            c.ring(x, y, 5.5F, thin, TabletTheme.withAlpha(color, Math.round(230 * pulse)));
            c.line(x - 10, y, x - 7, y, thin, color);
            c.line(x + 7, y, x + 10, y, thin, color);
            c.line(x, y - 10, x, y - 7, thin, color);
            c.line(x, y + 7, x, y + 10, thin, color);
            c.circle(x, y, 1.2F, color);
            int tw = tr.getWidth(label);
            text(context, tr, label, Math.round(x - tw / 2.0F), Math.round(y + 12), TabletTheme.withAlpha(color, 0xE6));
            return;
        }
        float x = MathHelper.clamp(at != null ? at[0] : w / 2.0F, w * 0.25F, w * 0.75F);
        float y = bottom - 4;
        int chevron = TabletTheme.withAlpha(color, Math.round(220 * pulse));
        c.polyline(new float[]{x - 6, y - 7, x, y - 2, x + 6, y - 7}, thin * 1.5F, chevron);
        c.polyline(new float[]{x - 6, y - 2, x, y + 3, x + 6, y - 2}, thin * 1.5F, chevron);
        int tw = tr.getWidth(label);
        text(context, tr, label, Math.round(x - tw / 2.0F), Math.round(y - 18), TabletTheme.withAlpha(color, 0xD0));
    }

    /** Bottom-left: kind, flight state, battery, payload. / 左下：型号、飞行状态、电量、挂载。 */
    private static Box telemetryPanel(DrawContext context, TabletCanvas c, TextRenderer tr, DroneEntity drone, int h,
                                        long now, float thin) {
        DroneKind kind = drone.kind();
        int accent = accent(kind);
        Text name = Text.translatable("entity.sparkstrength." + kind.id());
        DroneState state = DronePilotClient.displayState(drone);
        Text stateText = Text.translatable("hud.sparkstrength.drone.state." + state.name().toLowerCase(Locale.ROOT));
        int percent = DroneRules.percent(drone.charge());
        boolean low = percent <= LOW_BATTERY_PERCENT;
        Text payload = kind == DroneKind.BOMB ? Text.translatable("hud.sparkstrength.drone.payload.bomb")
                : drone.hasPayload() ? Text.translatable("hud.sparkstrength.drone.payload.armed")
                : Text.translatable("hud.sparkstrength.drone.payload.empty");

        int stateW = tr.getWidth(stateText) + 9;
        float pw = Math.max(140, 13 + tr.getWidth(name) + 12 + stateW + 9);
        pw = Math.max(pw, 13 + 12 + tr.getWidth(payload) + 9);
        float ph = 50;
        float x = MARGIN;
        float y = h - MARGIN - ph;
        panel(c, x, y, pw, ph, thin);
        c.roundRect(x + 4, y + 6, 2, ph - 12, 1, accent);

        // Row 1: name + state. / 第一行：型号与状态。
        text(context, tr, name, Math.round(x + 13), Math.round(y + 7), accentPale(kind));
        int stateColor = switch (state) {
            case FLYING -> accent;
            case HOVERING -> TabletTheme.SUCCESS;
            case GROUNDED -> TabletTheme.TEXT_2;
            case FALLING -> TabletTheme.DANGER;
        };
        float stateX = x + pw - 9 - tr.getWidth(stateText);
        c.circle(stateX - 5, y + 10.5F, 2.0F, stateColor);
        text(context, tr, stateText, Math.round(stateX), Math.round(y + 7), TabletTheme.TEXT_2);

        // Row 2: battery glyph, ten segments, percent (blinks under 20%). / 第二行：电池图标、十格电量、百分比（低于 20% 闪烁）。
        float blink = low ? (0.55F + 0.45F * MathHelper.cos(now / 130.0F)) : 1.0F;
        int level = percent > 50 ? TabletTheme.SUCCESS : low ? TabletTheme.DANGER : TabletTheme.WARNING;
        float rowY = y + 21;
        float gx = x + 13;
        c.roundRectOutline(gx, rowY, 11, 7, 1.5F, thin, TabletTheme.withAlpha(TabletTheme.TEXT_2, 0xC0));
        c.rect(gx + 11, rowY + 2, 1.5F, 3, TabletTheme.withAlpha(TabletTheme.TEXT_2, 0xC0));
        c.rect(gx + 2, rowY + 2, Math.max(0.0F, 7.0F * percent / 100.0F), 3, TabletTheme.multiplyAlpha(level, blink));
        String percentText = percent + "%";
        int percentW = tr.getWidth(percentText);
        float barX = gx + 17;
        float barEnd = x + pw - 12 - Math.max(percentW, tr.getWidth("100%"));
        float segGap = 1.0F;
        float segW = Math.max(2.0F, (barEnd - barX - segGap * 9) / 10.0F);
        int lit = (percent + 9) / 10;
        for (int i = 0; i < 10; i++) {
            int segColor = i < lit ? TabletTheme.multiplyAlpha(level, blink) : 0x26FFFFFF;
            c.roundRect(barX + i * (segW + segGap), rowY + 1, segW, 5, 1.0F, segColor);
        }
        text(context, tr, Text.literal(percentText), Math.round(x + pw - 9 - percentW), Math.round(rowY),
                low ? TabletTheme.multiplyAlpha(TabletTheme.DANGER, Math.max(0.35F, blink)) : TabletTheme.TEXT);

        // Row 3: payload. / 第三行：挂载。
        float py = y + 35;
        boolean armed = kind == DroneKind.BOMB || drone.hasPayload();
        int payloadColor = kind == DroneKind.BOMB ? TabletTheme.KILLER.pale() : armed ? TabletTheme.TEXT : TabletTheme.TEXT_3;
        int iconColor = kind == DroneKind.BOMB ? TabletTheme.DANGER : armed ? WARNING : TabletTheme.TEXT_3;
        grenadeIcon(c, x + 18, py + 4, iconColor, armed, thin);
        text(context, tr, payload, Math.round(x + 26), Math.round(py), payloadColor);
        return new Box(x, y, x + pw);
    }

    /** Bottom-right: altitude above ground and distance back to the body. / 右下：离地高度与回到本体的距离。 */
    private static Box rangePanel(DrawContext context, TabletCanvas c, TextRenderer tr, DroneEntity drone,
                                    ClientPlayerEntity player, int w, int h, float tickDelta, float thin) {
        Vec3d pos = drone.getLerpedPos(tickDelta);
        double ground = DronePilotClient.groundY();
        Text altLabel = Text.translatable("hud.sparkstrength.drone.altitude");
        Text altValue = Double.isNaN(ground) ? Text.literal("--") : meters(Math.max(0.0, pos.y - ground));
        Text bodyLabel = Text.translatable("hud.sparkstrength.drone.body_distance");
        Text bodyValue = meters(pos.distanceTo(player.getLerpedPos(tickDelta)));
        int labelW = Math.max(tr.getWidth(altLabel), tr.getWidth(bodyLabel));
        int valueW = Math.max(Math.max(tr.getWidth(altValue), tr.getWidth(bodyValue)), tr.getWidth("000.0m"));
        float pw = Math.max(96, 11 + labelW + 12 + valueW + 9);
        float ph = 36;
        float x = w - MARGIN - pw;
        float y = h - MARGIN - ph;
        panel(c, x, y, pw, ph, thin);
        text(context, tr, altLabel, Math.round(x + 11), Math.round(y + 8), TabletTheme.TEXT_3);
        text(context, tr, altValue, Math.round(x + pw - 9 - tr.getWidth(altValue)), Math.round(y + 8), TabletTheme.TEXT);
        c.rect(x + 11, y + 18, pw - 20, thin, TabletTheme.HAIRLINE);
        text(context, tr, bodyLabel, Math.round(x + 11), Math.round(y + 22), TabletTheme.TEXT_3);
        text(context, tr, bodyValue, Math.round(x + pw - 9 - tr.getWidth(bodyValue)), Math.round(y + 22), TabletTheme.TEXT);
        return new Box(x, y, x + pw);
    }

    /**
     * Bottom-centre key hints: "[attack] drop" (dim without an M67) or red "[attack] detonate", and "[use] disconnect".
     * Key names follow the player's bindings. Moves up a row when the corner panels leave too little room. Returns
     * the hint row's top.
     * 底部中央按键提示：“[攻击键] 投弹”（未挂载时变暗）或红色“[攻击键] 引爆”，以及“[使用键] 断开”。键名随玩家绑定变化。
     * 两侧面板挤占空间时上移一行。返回提示行顶部。
     */
    private static float hints(DrawContext context, TabletCanvas c, TextRenderer tr, MinecraftClient client,
                               DroneEntity drone, int w, int h, Box left, Box right, float thin) {
        DroneKind kind = drone.kind();
        boolean bomb = kind == DroneKind.BOMB;
        boolean canFire = bomb || drone.hasPayload();
        Text fireKey = client.options.attackKey.getBoundKeyLocalizedText();
        Text fireLabel = Text.translatable(bomb ? "hud.sparkstrength.drone.action.detonate"
                : "hud.sparkstrength.drone.action.drop");
        Text exitKey = client.options.useKey.getBoundKeyLocalizedText();
        Text exitLabel = Text.translatable("hud.sparkstrength.drone.action.exit");
        float fireW = hintWidth(tr, fireKey, fireLabel);
        float exitW = hintWidth(tr, exitKey, exitLabel);
        float gap = 6;
        float total = fireW + gap + exitW;
        float x = (w - total) / 2.0F;
        float y = h - MARGIN - 16;
        if (x < left.right() + gap || x + total > right.left() - gap) {
            y = Math.min(left.top(), right.top()) - 6 - 16;
        }
        int fireFill = bomb ? 0x4DF2555A : PANEL;
        int fireEdge = bomb ? 0xA6F2555A : PANEL_EDGE;
        int fireText = bomb ? TabletTheme.KILLER.pale() : canFire ? TabletTheme.TEXT : TabletTheme.TEXT_3;
        hint(context, c, tr, x, y, fireW, fireKey, fireLabel, fireFill, fireEdge, fireText, canFire ? 1.0F : 0.5F, thin);
        hint(context, c, tr, x + fireW + gap, y, exitW, exitKey, exitLabel, PANEL, PANEL_EDGE, TabletTheme.TEXT_2, 1.0F,
                thin);
        return y;
    }

    private static float hintWidth(TextRenderer tr, Text key, Text label) {
        return 4 + tr.getWidth(key) + 8 + 5 + tr.getWidth(label) + 7;
    }

    private static void hint(DrawContext context, TabletCanvas c, TextRenderer tr, float x, float y, float width, Text key,
                             Text label, int fill, int edge, int textColor, float alpha, float thin) {
        c.roundRect(x, y, width, 16, 4, TabletTheme.multiplyAlpha(fill, alpha));
        c.roundRectOutline(x, y, width, 16, 4, thin, TabletTheme.multiplyAlpha(edge, alpha));
        float keyW = tr.getWidth(key) + 8;
        c.roundRect(x + 3, y + 3, keyW, 10, 2.5F, TabletTheme.multiplyAlpha(KEYCAP, alpha));
        text(context, tr, key, Math.round(x + 7), Math.round(y + 4), TabletTheme.multiplyAlpha(TabletTheme.TEXT, alpha));
        text(context, tr, label, Math.round(x + 3 + keyW + 5), Math.round(y + 4), TabletTheme.multiplyAlpha(textColor, alpha));
    }

    /** Centred status pill (connecting / signal lost / landed hint). / 居中状态胶囊（连接中、信号丢失、着陆提示）。 */
    private static void statusPill(DrawContext context, TabletCanvas c, TextRenderer tr, float cx, float cy, Text text,
                                   int color, long now, boolean spinner) {
        int tw = tr.getWidth(text);
        float pw = tw + 30;
        float x = cx - pw / 2.0F;
        float y = cy - 8;
        c.roundRect(x, y, pw, 16, 8, PANEL);
        c.roundRectOutline(x, y, pw, 16, 8, Math.max(c.px(), Math.min(1.0F, 2.0F * c.px())),
                TabletTheme.withAlpha(color, 0x80));
        if (spinner) {
            c.arc(x + 10, cy, 4.0F, 1.2F, (now * 0.4F) % 360.0F, 270.0F, color);
        } else {
            float blink = 0.5F + 0.5F * MathHelper.sin(now / 160.0F);
            c.circle(x + 10, cy, 2.2F, TabletTheme.multiplyAlpha(color, 0.4F + 0.6F * blink));
        }
        text(context, tr, text, Math.round(x + 19), Math.round(cy - 4), TabletTheme.TEXT);
    }

    private static void panel(TabletCanvas c, float x, float y, float w, float h, float thin) {
        c.roundRect(x, y, w, h, 5, PANEL);
        c.roundRectOutline(x, y, w, h, 5, thin, PANEL_EDGE);
    }

    private static void grenadeIcon(TabletCanvas c, float cx, float cy, int color, boolean filled, float thin) {
        if (filled) {
            c.circle(cx, cy + 0.5F, 3.0F, color);
        } else {
            c.ring(cx, cy + 0.5F, 3.0F, thin, color);
        }
        c.rect(cx - 1.0F, cy - 4.0F, 2.0F, 1.5F, color);
        c.line(cx + 1.0F, cy - 3.5F, cx + 3.0F, cy - 1.5F, thin, color);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Horizontal extent and top of a corner panel. / 角落面板的水平范围与顶部。 */
    private record Box(float left, float top, float right) {
    }

    private static boolean lowBattery(DroneEntity drone) {
        return drone.state() != DroneState.FALLING && DroneRules.percent(drone.charge()) <= LOW_BATTERY_PERCENT;
    }

    private static Text meters(double value) {
        String number = value < 10.0 ? String.format(Locale.ROOT, "%.1f", value)
                : Integer.toString((int) Math.round(value));
        return Text.translatable("hud.sparkstrength.drone.meters", number);
    }

    /** Skips near-transparent text (vanilla would draw alpha 0..3 fully opaque). / 跳过近乎透明的文字（原版会把 alpha 0..3 画成不透明）。 */
    private static void text(DrawContext context, TextRenderer tr, Text text, int x, int y, int color) {
        if ((color >>> 24) >= 8) {
            context.drawText(tr, text, x, y, color, false);
        }
    }

    private static int pixelScale(MinecraftClient client) {
        return Math.max(1, (int) Math.round(client.getWindow().getScaleFactor()));
    }

    /** World point to scaled-GUI coordinates, or null when behind the camera. / 世界坐标转 GUI 坐标；位于镜头后方时返回 null。 */
    private static float @Nullable [] project(Vec3d world, int w, int h) {
        if (!projectionValid) {
            return null;
        }
        Vector4f v = SCRATCH.set((float) (world.x - projectionCamera.x), (float) (world.y - projectionCamera.y),
                (float) (world.z - projectionCamera.z), 1.0F);
        VIEW.transform(v);
        PROJECTION.transform(v);
        if (v.w <= 0.05F) {
            return null;
        }
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        return new float[]{(nx * 0.5F + 0.5F) * w, (0.5F - ny * 0.5F) * h};
    }
}
