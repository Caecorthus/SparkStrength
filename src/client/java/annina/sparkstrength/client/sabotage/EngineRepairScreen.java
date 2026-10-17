package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.CancelBreakerRepairC2SPacket;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.CompleteBreakerRepairC2SPacket;
import annina.sparkstrength.network.sabotage.OpenEngineRepairS2CPacket;
import annina.sparkstrength.sabotage.EngineCondition;
import annina.sparkstrength.sabotage.EngineRepairMode;
import annina.sparkstrength.sabotage.EngineRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Engine unit panel. Quick repair: turn every pressure needle into its green band, then hold Restart until the
 * tachometer climbs into the green. Overhaul: replace the burnt fuses first (the gauges stay dark until then), then the
 * same gauges and a longer restart. Status (session 0): the same panel, read only, mirroring the live block state.
 * The server only accepts the result after its minimum time and re-checks the session.
 * 动力装置面板。抢修：把每根压力表指针调进绿区，再按住「重启」直到转速表爬进绿区。大修：先换掉烧黑的保险丝（在此之前
 * 压力表不通电），再调表、更长的重启。状态面板（会话 0）：同一块面板，只能看，跟随方块的实时状态。服务端只在最短用时
 * 之后才接受结果，并重新校验会话。
 */
final class EngineRepairScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 190;
    /** The status panel has no knobs or restart button, so it is shorter. / 状态面板没有旋钮和重启按钮，因此更矮。 */
    private static final int STATUS_PANEL_H = 156;
    private static final int PAD = 14;
    /** Width of the left area (fuses and gauges). / 左侧区域（保险丝与压力表）的宽度。 */
    private static final int LEFT_W = 204;
    /** Centre of the right column (tachometer and restart), relative to the panel. / 右栏（转速表与重启）中线，相对面板。 */
    private static final int COLUMN_CX = 268;
    private static final int CAPTION_Y = 44;
    private static final int FUSE_SLOTS = 5;
    private static final int FUSE_W = 36;
    private static final int FUSE_H = 18;
    private static final int FUSE_GAP = (LEFT_W - FUSE_SLOTS * FUSE_W) / (FUSE_SLOTS - 1);
    private static final int FUSE_Y = 55;
    private static final float TACH_R = 25.0F;
    private static final int BUTTON_W = 64;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_Y = 121;
    private static final float KNOB_R = 7.0F;
    private static final int CLOSE_DELAY_TICKS = 16;
    /** No answer this long after submitting: the packet was dropped (e.g. dazed), so let the player try again. / 提交后这么久没有回复：包被丢弃（例如被眩晕），允许玩家重试。 */
    private static final int REPLY_TIMEOUT_TICKS = 40;
    /** Margin over the server's minimum time. / 相对服务端最短用时的余量。 */
    private static final int SUBMIT_MARGIN_TICKS = 2;

    /** Dial sweep: 0 at 8 o'clock, 1 at 4 o'clock. / 表盘量程：0 在 8 点方向，1 在 4 点方向。 */
    private static final float START_DEG = -120.0F;
    private static final float SWEEP_DEG = 240.0F;
    private static final float BAND_MIN = 0.22F;
    private static final float BAND_SPREAD = 0.04F;
    /** Start positions keep at least this far from the green band and from the stops. / 初始位置与绿区、两端止点至少相隔这么远。 */
    private static final float START_GAP = 0.12F;
    private static final float START_EDGE = 0.03F;
    private static final float DRAG_PER_PX = 0.01F;
    private static final float SCROLL_STEP = 0.045F;
    private static final float RATCHET_STEP = 0.05F;
    private static final float NEEDLE_EASE = 0.3F;
    private static final float TACH_GOAL = 0.80F;
    private static final float TACH_GREEN_LO = 0.70F;
    private static final float TACH_GREEN_HI = 0.90F;
    private static final float TACH_REDLINE = 0.92F;
    private static final float POWER_OFF = 0.12F;

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

    private static final int END_NONE = 0;
    private static final int END_SUCCESS = 1;
    private static final int END_FAILURE = 2;

    private final int sessionId;
    private final BlockPos pos;
    private final EngineRepairMode mode;
    /** A real repair session; otherwise a read-only status panel that never sends anything. / 真正的维修会话；否则是只读的状态面板，不发送任何包。 */
    private final boolean repair;
    private final int panelH;
    private final int gaugeCount;
    private final int holdTicks;
    private final int minTicks;

    private final float[] bandLo = new float[EngineRules.MAX_GAUGES];
    private final float[] bandHi = new float[EngineRules.MAX_GAUGES];
    private final float[] start = new float[EngineRules.MAX_GAUGES];
    private final float[] phase = new float[EngineRules.MAX_GAUGES];
    private final float[] target = new float[EngineRules.MAX_GAUGES];
    private final float[] needle = new float[EngineRules.MAX_GAUGES];
    private final float[] prevNeedle = new float[EngineRules.MAX_GAUGES];
    private final boolean[] set = new boolean[EngineRules.MAX_GAUGES];
    /** Glass crack per gauge, unit-radius points (x0, y0, x1, y1, x2, y2). / 每块表的玻璃裂纹，单位半径坐标。 */
    private final float[][] cracks = new float[EngineRules.MAX_GAUGES][6];

    private final boolean[] burnt = new boolean[FUSE_SLOTS];
    private final boolean[] wasBurnt = new boolean[FUSE_SLOTS];
    private final float[] fuseCharge = new float[FUSE_SLOTS];
    private final float[] prevFuseCharge = new float[FUSE_SLOTS];
    private final float[] fuseFlash = new float[FUSE_SLOTS];
    private final int burntTotal;

    /** Soot blotches (x, y, radius, alpha) in panel space, for the scorched look. / 焦黑外观的烟灰斑（x、y、半径、alpha），面板坐标。 */
    private final float[] soot;
    private final float[] panelCrack;

    @Nullable
    private EngineCondition condition;
    private int openTicks;
    private int awaitTicks;
    private int closeTicks = -1;
    private int endState = END_NONE;
    private float charge;
    private float prevCharge;
    private float tach;
    private float prevTach;
    private float power;
    private float prevPower;
    private boolean holdingRestart;
    private boolean restarted;
    private boolean submitted;
    private boolean cancelSent;
    private boolean fuseHold;
    private int dragGauge = -1;
    private double lastMouseX = -1;
    private double lastMouseY = -1;
    private float ratchet;
    private int soundCooldown;
    private int chugTicks;
    @Nullable
    private Text banner;
    private int bannerColor;
    private boolean bannerRetry;

    EngineRepairScreen(OpenEngineRepairS2CPacket packet) {
        super(Text.translatable(titleKey(EngineRepairMode.byWire(packet.modeWire()), packet.sessionId())));
        this.sessionId = packet.sessionId();
        this.pos = packet.pos();
        EngineRepairMode wireMode = EngineRepairMode.byWire(packet.modeWire());
        this.repair = wireMode != EngineRepairMode.STATUS && packet.sessionId() != 0;
        this.mode = repair ? wireMode : EngineRepairMode.STATUS;
        this.panelH = repair ? PANEL_H : STATUS_PANEL_H;
        this.gaugeCount = MathHelper.clamp(packet.gauges(), 1, EngineRules.MAX_GAUGES);
        this.holdTicks = Math.max(1, packet.holdTicks());
        this.minTicks = Math.max(0, packet.minTicks());

        // Needles: one draw sequence per possible gauge, so the count never reshuffles the others.
        // 指针：每个可能的表位各取一组随机数，表的数量不会打乱其他表的布局。
        Random random = new Random(packet.seed());
        for (int i = 0; i < EngineRules.MAX_GAUGES; i++) {
            float width = BAND_MIN + random.nextFloat() * BAND_SPREAD;
            float center = 0.30F + random.nextFloat() * 0.40F;
            bandLo[i] = center - width / 2.0F;
            bandHi[i] = center + width / 2.0F;
            float leftRoom = bandLo[i] - START_GAP - START_EDGE;
            float rightRoom = 1.0F - START_EDGE - (bandHi[i] + START_GAP);
            boolean left = random.nextBoolean();
            if (left && leftRoom < 0.05F) {
                left = false;
            } else if (!left && rightRoom < 0.05F) {
                left = true;
            }
            float u = random.nextFloat();
            start[i] = left
                    ? START_EDGE + u * Math.max(0.0F, leftRoom)
                    : bandHi[i] + START_GAP + u * Math.max(0.0F, rightRoom);
            phase[i] = random.nextFloat() * MathHelper.TAU;
            float crackAngle = random.nextFloat() * MathHelper.TAU;
            float bend = (random.nextFloat() - 0.5F) * 1.2F;
            cracks[i][0] = MathHelper.sin(crackAngle) * 0.95F;
            cracks[i][1] = -MathHelper.cos(crackAngle) * 0.95F;
            cracks[i][2] = MathHelper.sin(crackAngle + bend) * 0.45F;
            cracks[i][3] = -MathHelper.cos(crackAngle + bend) * 0.45F;
            cracks[i][4] = MathHelper.sin(crackAngle + bend * 2.0F + 0.6F) * 0.2F;
            cracks[i][5] = -MathHelper.cos(crackAngle + bend * 2.0F + 0.6F) * 0.2F;
        }

        // Burnt fuses: a row of slots like the breaker's switches, the burnt ones picked by the seed.
        // 烧坏的保险丝：和电闸的空开一样排成一排，由 seed 决定哪几根烧坏。
        int fuses = mode == EngineRepairMode.OVERHAUL
                ? MathHelper.clamp(packet.fuses(), 0, Math.min(EngineRules.MAX_FUSES, FUSE_SLOTS))
                : 0;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < FUSE_SLOTS; i++) {
            slots.add(i);
        }
        Random fuseRandom = new Random(packet.seed() ^ 0x5F3759DFL);
        Collections.shuffle(slots, fuseRandom);
        for (int i = 0; i < fuses; i++) {
            burnt[slots.get(i)] = true;
            wasBurnt[slots.get(i)] = true;
        }
        this.burntTotal = fuses;

        // Soot and a panel crack, only drawn when the panel is scorched.
        // 烟灰与面板裂纹，只在面板焦黑时绘制。
        int blotches = 7;
        soot = new float[blotches * 4];
        for (int i = 0; i < blotches; i++) {
            soot[i * 4] = 26 + fuseRandom.nextFloat() * (PANEL_W - 52);
            soot[i * 4 + 1] = 26 + fuseRandom.nextFloat() * (panelH - 52);
            soot[i * 4 + 2] = 18 + fuseRandom.nextFloat() * 16;
            soot[i * 4 + 3] = 0x48 + fuseRandom.nextInt(0x40);
        }
        panelCrack = new float[10];
        float cx = 2.0F;
        float cy = panelH - 30 - fuseRandom.nextFloat() * 30;
        for (int i = 0; i < 5; i++) {
            panelCrack[i * 2] = cx;
            panelCrack[i * 2 + 1] = cy;
            cx += 6 + fuseRandom.nextFloat() * 7;
            cy += (fuseRandom.nextFloat() - 0.65F) * 9;
        }

        // Needles boot from their stops: a powered repair panel swings them up to their (red) readings on open.
        // 指针从止点启动：通电的维修面板一打开，指针就摆到各自（红区的）读数。
        if (repair && gaugesPowered()) {
            System.arraycopy(start, 0, target, 0, gaugeCount);
        }
        this.power = gaugesPowered() ? 1.0F : POWER_OFF;
        this.prevPower = power;
    }

    private static String titleKey(EngineRepairMode mode, int sessionId) {
        if (sessionId == 0) {
            return "screen.sparkstrength.engine.title";
        }
        return switch (mode) {
            case QUICK -> "screen.sparkstrength.engine.title_quick";
            case OVERHAUL -> "screen.sparkstrength.engine.title_overhaul";
            case STATUS -> "screen.sparkstrength.engine.title";
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        condition = liveCondition();
    }

    // ------------------------------------------------------------------------------------------------ layout

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - panelH) / 2;
    }

    private float slotW() {
        return LEFT_W / (float) gaugeCount;
    }

    private float gaugeCx(int index) {
        return left() + PAD + slotW() * (index + 0.5F);
    }

    /** Dial radius: as large as the slot and the mode's vertical room allow. / 表盘半径：在槽宽与该模式的纵向空间内尽量大。 */
    private float gaugeR() {
        float max = switch (mode) {
            case OVERHAUL -> 17.0F;
            case QUICK -> 24.0F;
            case STATUS -> 22.0F;
        };
        return Math.min(max, slotW() / 2.0F - 7.0F);
    }

    /** Overhaul sits below the fuse row; the quick repair's knobs line up with the restart button. / 大修位于保险丝排下方；抢修的旋钮与重启按钮对齐。 */
    private float gaugeCy() {
        return top() + switch (mode) {
            case OVERHAUL -> 109;
            case QUICK -> 86;
            case STATUS -> 90;
        };
    }

    private float lampCy() {
        return gaugeCy() + gaugeR() + 9;
    }

    private float knobCy() {
        return gaugeCy() + gaugeR() + 23;
    }

    private boolean overGauge(double mx, double my, int index) {
        float half = slotW() / 2.0F;
        float y0 = gaugeCy() - gaugeR() - 4;
        return inside(mx, my, gaugeCx(index) - half, y0, slotW(), knobCy() + KNOB_R + 3 - y0);
    }

    private float fuseX(int slot) {
        return left() + PAD + slot * (FUSE_W + FUSE_GAP);
    }

    private float fuseY() {
        return top() + FUSE_Y;
    }

    private int fuseAt(double mx, double my) {
        for (int s = 0; s < FUSE_SLOTS; s++) {
            if (inside(mx, my, fuseX(s), fuseY(), FUSE_W, FUSE_H)) {
                return s;
            }
        }
        return -1;
    }

    private float columnCx() {
        return left() + COLUMN_CX;
    }

    private float tachCy() {
        return top() + (mode == EngineRepairMode.STATUS ? 90 : 85);
    }

    private float buttonX() {
        return columnCx() - BUTTON_W / 2.0F;
    }

    private float buttonY() {
        return top() + BUTTON_Y;
    }

    // ------------------------------------------------------------------------------------------------ state

    private boolean fusesDone() {
        for (boolean value : burnt) {
            if (value) {
                return false;
            }
        }
        return true;
    }

    private boolean gaugesPowered() {
        return mode != EngineRepairMode.OVERHAUL || fusesDone();
    }

    private boolean inBand(int index, float value) {
        return value >= bandLo[index] && value <= bandHi[index];
    }

    private boolean allSet() {
        for (int i = 0; i < gaugeCount; i++) {
            if (!set[i]) {
                return false;
            }
        }
        return true;
    }

    private int setCount() {
        int count = 0;
        for (int i = 0; i < gaugeCount; i++) {
            if (set[i]) {
                count++;
            }
        }
        return count;
    }

    private int fusesReplaced() {
        int count = 0;
        for (int s = 0; s < FUSE_SLOTS; s++) {
            if (wasBurnt[s] && !burnt[s]) {
                count++;
            }
        }
        return count;
    }

    /** Controls take input only while a live repair is in progress. / 只有维修进行中时控件才接受输入。 */
    private boolean live() {
        return repair && !submitted && !restarted && closeTicks < 0;
    }

    private boolean restartReady() {
        return live() && gaugesPowered() && allSet();
    }

    /**
     * The engine caught (restarted, awaiting the reply, or confirmed). A failed close overrides it, because onServerClose
     * marks every session submitted.
     * 引擎已点着（已重启、等待回复或已确认）。失败的关闭优先于它，因为 onServerClose 会把所有会话标为已提交。
     */
    private boolean engineCaught() {
        return endState == END_SUCCESS || (endState == END_NONE && (restarted || submitted));
    }

    @Nullable
    private EngineCondition liveCondition() {
        if (client == null || client.world == null) {
            return null;
        }
        BlockState state = client.world.getBlockState(pos);
        return state.isOf(SparkStrengthBlocks.engineUnit()) ? state.get(EngineUnitBlock.CONDITION) : null;
    }

    private boolean scorched() {
        if (endState == END_SUCCESS) {
            return false;
        }
        return mode == EngineRepairMode.OVERHAUL
                || (mode == EngineRepairMode.STATUS && condition == EngineCondition.WRECKED);
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        openTicks++;
        if (soundCooldown > 0) {
            soundCooldown--;
        }
        condition = liveCondition();
        if (!leftButtonDown()) {
            // A release can be swallowed (stun mixins cancel every mouse event, overlays eat releases) or the window
            // can lose focus mid-hold, so the physical button state is the source of truth for every hold.
            // 松开事件可能被吞掉（眩晕 mixin 会取消所有鼠标事件、覆盖层会吃掉松开），也可能在按住时窗口失焦，
            // 因此所有按住操作都以物理按键状态为准。
            releaseHolds();
        }
        prevCharge = charge;
        prevTach = tach;
        prevPower = power;
        System.arraycopy(needle, 0, prevNeedle, 0, needle.length);
        System.arraycopy(fuseCharge, 0, prevFuseCharge, 0, fuseCharge.length);
        for (int s = 0; s < FUSE_SLOTS; s++) {
            fuseFlash[s] = Math.max(0.0F, fuseFlash[s] - 0.12F);
        }

        updateTargets();
        for (int i = 0; i < gaugeCount; i++) {
            needle[i] += (target[i] - needle[i]) * NEEDLE_EASE;
        }
        float powerTarget = endState == END_FAILURE ? POWER_OFF : gaugesPowered() ? 1.0F : POWER_OFF;
        power += (powerTarget - power) * 0.2F;
        tach += (tachTarget() - tach) * 0.45F;

        if (closeTicks >= 0) {
            // Closing: success swings every needle to the middle of its green, failure lets them drop.
            // 关闭中：成功时每根指针摆到绿区中央，失败时指针落回止点。
            for (int i = 0; i < gaugeCount; i++) {
                set[i] = endState == END_SUCCESS && inBand(i, needle[i]);
            }
            if (--closeTicks <= 0) {
                close();
            }
            return;
        }
        if (!repair) {
            for (int i = 0; i < gaugeCount; i++) {
                set[i] = inBand(i, needle[i]);
            }
            return;
        }
        if (submitted) {
            if (awaitTicks > 0 && --awaitTicks == 0) {
                // The completion never arrived; the server session is still open, so restart again.
                // 完成包没有送达；服务端会话仍然有效，重新按住重启即可。
                submitted = false;
                restarted = false;
                charge = 0.0F;
                prevCharge = 0.0F;
                banner = Text.translatable("screen.sparkstrength.engine.retry");
                bannerColor = TabletTheme.DANGER;
                bannerRetry = true;
            }
            return;
        }

        tickFuses();
        tickGauges();
        tickRestart();
    }

    /** Where every needle wants to be this tick. / 本 tick 每根指针的目标位置。 */
    private void updateTargets() {
        if (endState == END_SUCCESS) {
            for (int i = 0; i < gaugeCount; i++) {
                target[i] = (bandLo[i] + bandHi[i]) / 2.0F;
            }
            return;
        }
        if (endState == END_FAILURE) {
            for (int i = 0; i < gaugeCount; i++) {
                target[i] = 0.0F;
            }
            return;
        }
        if (repair) {
            // Repair panels: the player sets target[]; a dead overhaul panel rests every needle on its stop until the
            // fuses are in, then they swing up to their (red) readings.
            // 维修面板：target[] 由玩家调；大修面板在换好保险丝之前不通电，指针都靠在止点，通电后摆到各自（红区的）读数。
            if (!gaugesPowered()) {
                for (int i = 0; i < gaugeCount; i++) {
                    target[i] = 0.0F;
                }
            }
            return;
        }
        float t = openTicks;
        for (int i = 0; i < gaugeCount; i++) {
            if (condition == EngineCondition.INTACT) {
                float mid = (bandLo[i] + bandHi[i]) / 2.0F;
                target[i] = mid + 0.035F * MathHelper.sin(t * 0.05F + phase[i]);
            } else if (condition == EngineCondition.DAMAGED) {
                target[i] = start[i];
            } else if (condition == EngineCondition.WRECKED) {
                target[i] = start[i] < bandLo[i] ? 0.0F : 1.0F;
            } else {
                target[i] = 0.0F;
            }
        }
    }

    private float tachTarget() {
        if (endState == END_SUCCESS) {
            return TACH_GOAL;
        }
        if (endState == END_FAILURE) {
            return 0.0F;
        }
        float t = openTicks;
        if (repair) {
            if (restarted || submitted) {
                return TACH_GOAL + 0.012F * MathHelper.sin(t * 0.4F);
            }
            return charge / holdTicks * TACH_GOAL;
        }
        if (condition == EngineCondition.INTACT) {
            return TACH_GOAL + 0.02F * MathHelper.sin(t * 0.07F);
        }
        if (condition == EngineCondition.DAMAGED) {
            return 0.28F + 0.06F * MathHelper.sin(t * 0.31F) + 0.03F * MathHelper.sin(t * 0.83F);
        }
        return 0.0F;
    }

    private void tickFuses() {
        if (mode != EngineRepairMode.OVERHAUL || fusesDone()) {
            return;
        }
        int hovered = fuseHold ? fuseAt(lastMouseX, lastMouseY) : -1;
        for (int s = 0; s < FUSE_SLOTS; s++) {
            if (!burnt[s]) {
                continue;
            }
            if (s == hovered) {
                fuseCharge[s] = Math.min(EngineRules.FUSE_HOLD_TICKS, fuseCharge[s] + 1.0F);
                if (fuseCharge[s] >= EngineRules.FUSE_HOLD_TICKS) {
                    burnt[s] = false;
                    fuseFlash[s] = 1.0F;
                    playSound(SparkStrengthSounds.CIRCUIT_BREAKER_SWITCH, 1.1F + 0.08F * fusesReplaced(), 0.6F);
                    if (fusesDone()) {
                        fuseHold = false;
                        // Power is back on the gauges: their needles now swing to their readings.
                        // 压力表重新通电：指针摆到各自的读数。
                        System.arraycopy(start, 0, target, 0, gaugeCount);
                        playSound(SoundEvents.BLOCK_COPPER_BULB_TURN_ON, 0.8F, 0.8F);
                    }
                }
            } else {
                fuseCharge[s] = Math.max(0.0F, fuseCharge[s] - 1.0F);
            }
        }
    }

    private void tickGauges() {
        boolean powered = gaugesPowered();
        for (int i = 0; i < gaugeCount; i++) {
            // Set only when both the knob and the needle sit in the band, so a needle swinging past on power-up does
            // not light the lamp.
            // 只有旋钮目标和指针都在绿区内才算调好，避免通电时指针掠过绿区误亮灯。
            boolean now = powered && inBand(i, target[i]) && inBand(i, needle[i]);
            if (now && !set[i]) {
                playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.5F + 0.1F * i, 0.35F);
            }
            set[i] = now;
        }
    }

    private void tickRestart() {
        if (!restarted) {
            boolean ready = restartReady();
            if (holdingRestart && ready) {
                charge = Math.min(holdTicks, charge + 1.0F);
                float progress = charge / holdTicks;
                if (--chugTicks <= 0) {
                    // The engine turning over: faster and higher as it spins up.
                    // 引擎在盘车：转得越快，声音越密越高。
                    playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), 0.5F + 0.7F * progress, 0.45F);
                    chugTicks = Math.max(2, 6 - (int) (progress * 5.0F));
                }
            } else {
                charge = Math.max(0.0F, charge - 2.0F);
                chugTicks = 0;
            }
            if (charge >= holdTicks) {
                restarted = true;
                holdingRestart = false;
                playSound(SoundEvents.ENTITY_IRON_GOLEM_REPAIR, 1.0F, 0.7F);
                if (openTicks < minTicks + SUBMIT_MARGIN_TICKS) {
                    banner = Text.translatable("screen.sparkstrength.engine.settling");
                    bannerColor = TabletTheme.WARNING;
                    bannerRetry = false;
                }
            }
        }
        // Done but faster than the server's minimum: keep the engine idling until that time has passed (the server
        // starts its clock before this screen opens, so waiting here is always enough).
        // 已完成但快于服务端最短用时：让引擎保持怠速直到时间够了（服务端的计时早于界面打开，因此在这里等就足够）。
        if (restarted && openTicks >= minTicks + SUBMIT_MARGIN_TICKS) {
            submitted = true;
            awaitTicks = REPLY_TIMEOUT_TICKS;
            banner = Text.translatable("screen.sparkstrength.engine.restarting");
            bannerColor = TabletTheme.SUCCESS;
            bannerRetry = false;
            ClientPlayNetworking.send(new CompleteBreakerRepairC2SPacket(sessionId));
        }
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 0 && repair && closeTicks < 0) {
            if (mode == EngineRepairMode.OVERHAUL && fuseAt(mouseX, mouseY) >= 0) {
                if (live() && !fusesDone()) {
                    fuseHold = true;
                }
                return true;
            }
            for (int i = 0; i < gaugeCount; i++) {
                if (overGauge(mouseX, mouseY, i)) {
                    if (live() && gaugesPowered()) {
                        dragGauge = i;
                        ratchet = 0.0F;
                    } else {
                        playSound(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6F, 0.25F);
                    }
                    return true;
                }
            }
            if (inside(mouseX, mouseY, buttonX() - 4, buttonY() - 4, BUTTON_W + 8, BUTTON_H + 8)) {
                if (restartReady()) {
                    holdingRestart = true;
                    if (bannerRetry) {
                        banner = null;
                        bannerRetry = false;
                    }
                } else if (!restarted && !submitted) {
                    playSound(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6F, 0.25F);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            releaseHolds();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void releaseHolds() {
        holdingRestart = false;
        fuseHold = false;
        dragGauge = -1;
    }

    /** Whether the left button is physically down in a focused window. / 窗口有焦点且左键确实处于按下状态。 */
    private boolean leftButtonDown() {
        return client != null && client.isWindowFocused()
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 0 && dragGauge >= 0) {
            if (live() && gaugesPowered()) {
                turn(dragGauge, (float) deltaX * DRAG_PER_PX);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (repair && closeTicks < 0) {
            double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
            for (int i = 0; i < gaugeCount; i++) {
                if (overGauge(mouseX, mouseY, i)) {
                    if (live() && gaugesPowered() && amount != 0.0) {
                        turn(i, (float) amount * SCROLL_STEP);
                    }
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return false;
    }

    /** Turns a knob; the needle follows with easing. Ratchet clicks every few percent. / 转动旋钮，指针缓动跟随；每转一小格响一下棘轮声。 */
    private void turn(int index, float delta) {
        float before = target[index];
        target[index] = MathHelper.clamp(before + delta, 0.0F, 1.0F);
        ratchet += Math.abs(target[index] - before);
        if (ratchet >= RATCHET_STEP) {
            ratchet = 0.0F;
            if (soundCooldown == 0) {
                soundCooldown = 1;
                playSound(SoundEvents.BLOCK_COMPARATOR_CLICK, 1.6F + 0.4F * target[index], 0.2F);
            }
        }
    }

    /**
     * Every way off this screen ends here: close() goes through setScreen(null), and another screen replacing this one
     * calls removed() without close(). So the cancel lives here, sent at most once.
     * 离开此界面的所有路径都会到这里：close() 走 setScreen(null)，被其他界面替换时只调用 removed() 而不调用 close()。
     * 因此取消包放在这里发送，且最多发送一次。
     */
    @Override
    public void removed() {
        releaseHolds();
        sendCancelOnce();
        super.removed();
    }

    /**
     * Drops the server session unless there is none (status panel), it is already answered (submitted, awaiting the
     * reply) or the server closed it, or the connection is gone.
     * 放弃服务端会话；没有会话（状态面板）、已提交等待回复、服务端已关闭、或连接已断开时不发送。
     */
    private void sendCancelOnce() {
        if (cancelSent || !repair || submitted || closeTicks >= 0) {
            return;
        }
        cancelSent = true;
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        ClientPlayNetworking.send(new CancelBreakerRepairC2SPacket(sessionId));
    }

    void onServerClose(int closedSession, int reason) {
        if (closedSession != sessionId || closeTicks >= 0) {
            return;
        }
        submitted = true;
        releaseHolds();
        switch (reason) {
            case CloseBreakerRepairS2CPacket.REASON_REPAIRED -> {
                banner = Text.translatable("screen.sparkstrength.engine.repaired");
                bannerColor = TabletTheme.SUCCESS;
                endState = END_SUCCESS;
            }
            case CloseBreakerRepairS2CPacket.REASON_POWER_BACK -> {
                banner = Text.translatable("screen.sparkstrength.engine.power_back");
                bannerColor = TabletTheme.SUCCESS;
                endState = END_SUCCESS;
            }
            case CloseBreakerRepairS2CPacket.REASON_PHASE_CHANGED -> {
                banner = Text.translatable("screen.sparkstrength.engine.phase_changed");
                bannerColor = TabletTheme.DANGER;
                endState = END_FAILURE;
            }
            default -> {
                banner = Text.translatable("screen.sparkstrength.engine.interrupted");
                bannerColor = TabletTheme.DANGER;
                endState = END_FAILURE;
            }
        }
        bannerRetry = false;
        closeTicks = CLOSE_DELAY_TICKS;
    }

    // ------------------------------------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        int scale = Math.max(1, (int) Math.round(client.getWindow().getScaleFactor()));
        TabletCanvas canvas = new TabletCanvas(context, scale, client.getWindow().getFramebufferHeight());
        int x = left();
        int y = top();
        boolean scorched = scorched();
        float time = openTicks + delta;
        float lit = MathHelper.lerp(delta, prevPower, power);

        drawPanel(canvas, x, y, scorched);
        drawHeader(canvas, x, y, scorched, time);

        canvas.rect(x + 222, y + CAPTION_Y + 2, 1, panelH - CAPTION_Y - (repair ? 36 : 24), TabletTheme.HAIRLINE);
        if (mode == EngineRepairMode.OVERHAUL) {
            boolean done = fusesDone();
            caption(canvas, "screen.sparkstrength.engine.fuses", fusesReplaced() + "/" + burntTotal, x + PAD,
                    y + CAPTION_Y, done ? TabletTheme.SUCCESS : TabletTheme.TEXT_2);
            int hovered = live() && !done ? fuseAt(mouseX, mouseY) : -1;
            for (int s = 0; s < FUSE_SLOTS; s++) {
                drawFuse(canvas, s, delta, s == hovered && burnt[s]);
            }
        }
        int gaugeCaptionY = mode == EngineRepairMode.OVERHAUL ? y + 79 : y + CAPTION_Y;
        int gaugeCaptionColor = !gaugesPowered() ? TabletTheme.TEXT_3
                : repair && allSet() ? TabletTheme.SUCCESS : TabletTheme.TEXT_2;
        caption(canvas, "screen.sparkstrength.engine.gauges",
                repair ? setCount() + "/" + gaugeCount : null, x + PAD, gaugeCaptionY, gaugeCaptionColor);
        for (int i = 0; i < gaugeCount; i++) {
            boolean hover = live() && gaugesPowered() && (dragGauge == i || dragGauge < 0 && overGauge(mouseX, mouseY, i));
            drawGauge(canvas, i, delta, time, lit, scorched, hover);
        }

        String rpm = Text.translatable("screen.sparkstrength.engine.tachometer").getString();
        canvas.textCentered(textRenderer, rpm, (int) columnCx(), y + CAPTION_Y,
                engineCaught() ? TabletTheme.SUCCESS : TabletTheme.TEXT_2);
        drawTachometer(canvas, delta, scorched, lit);
        if (repair) {
            drawRestartButton(canvas, delta, time, mouseX, mouseY);
        }

        if (banner != null) {
            int bw = textRenderer.getWidth(banner) + 20;
            float bx = x + (PANEL_W - bw) / 2.0F;
            float by = y + panelH - 24;
            canvas.roundRect(bx, by, bw, 16, 8, 0xE0101215);
            canvas.roundRect(bx, by, bw, 16, 8, TabletTheme.withAlpha(bannerColor, 0x33));
            canvas.roundRectOutline(bx, by, bw, 16, 8, 1.0F, bannerColor);
            canvas.textCentered(textRenderer, banner.getString(), x + PANEL_W / 2, (int) by + 4, bannerColor);
        }
        canvas.flush();
    }

    private void drawPanel(TabletCanvas canvas, int x, int y, boolean scorched) {
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
        if (mode == EngineRepairMode.STATUS) {
            canvas.roundRect(x, y, PANEL_W, 20, 10, scorched ? BRASS_BURNT : BRASS_DARK);
        } else {
            canvas.roundRect(x, y, PANEL_W, 20, 10, 0xFF111214);
            int stripe = mode == EngineRepairMode.OVERHAUL ? RUST : HAZARD;
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

    private void drawHeader(TabletCanvas canvas, int x, int y, boolean scorched, float time) {
        drawGear(canvas, x + 20, y + 17, 5.5F, scorched ? 0xFFB8783A : GEAR, 0xFF101214, time);
        canvas.text(textRenderer, title.getString(), x + 30, y + 13, TabletTheme.TEXT);
        drawLamps(canvas, x + PANEL_W - 18, y + 17);

        String hint;
        int hintColor = TabletTheme.TEXT_2;
        if (!repair) {
            // The condition ids are a saved contract (intact / damaged / wrecked), so they double as key suffixes.
            // condition 的 id 是保存契约（intact / damaged / wrecked），可直接作为键名后缀。
            String state = Text.translatable("screen.sparkstrength.engine.status."
                    + (condition == null ? "unknown" : condition.asString())).getString();
            hint = Text.translatable("screen.sparkstrength.engine.status", state).getString();
            hintColor = condition == EngineCondition.INTACT ? TabletTheme.SUCCESS
                    : condition == EngineCondition.DAMAGED ? TabletTheme.WARNING
                    : condition == EngineCondition.WRECKED ? TabletTheme.DANGER : TabletTheme.TEXT_3;
        } else if (endState != END_NONE && banner != null) {
            // Closing: the step hints no longer apply, so repeat the outcome. / 关闭中：步骤提示已不适用，改为重复结果。
            hint = banner.getString();
            hintColor = bannerColor;
        } else if (restarted || submitted) {
            hint = Text.translatable("screen.sparkstrength.engine.hint_wait").getString();
        } else if (!gaugesPowered()) {
            hint = Text.translatable("screen.sparkstrength.engine.hint_fuses").getString();
        } else if (!allSet()) {
            hint = Text.translatable("screen.sparkstrength.engine.hint_gauges").getString();
        } else {
            hint = Text.translatable("screen.sparkstrength.engine.hint_restart").getString();
        }
        canvas.text(textRenderer, TabletCanvas.trim(textRenderer, hint, PANEL_W - 28), x + PAD, y + 27, hintColor);
        canvas.rect(x + 12, y + 39, PANEL_W - 24, 1, TabletTheme.HAIRLINE);
    }

    /** The engine junction box's gear, turning while the engine runs. / 动力接线箱上的齿轮，引擎运转时会转动。 */
    private void drawGear(TabletCanvas canvas, float cx, float cy, float r, int color, int hole, float time) {
        boolean running = repair ? engineCaught() : condition == EngineCondition.INTACT;
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
        for (int k = 0; k < 3; k++) {
            float lx = rightX - k * 11;
            int color;
            float on;
            if (endState == END_SUCCESS || condition == EngineCondition.INTACT) {
                color = TabletTheme.SUCCESS;
                on = 1.0F;
            } else if (condition == EngineCondition.DAMAGED) {
                color = TabletTheme.DANGER;
                on = (openTicks / 8) % 2 == 0 ? 1.0F : 0.0F;
            } else if (condition == EngineCondition.WRECKED) {
                color = TabletTheme.DANGER;
                on = switch (k) {
                    case 0 -> ((openTicks * 7 + 3) % 11) < 3 ? 0.8F : 0.0F;
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

    private void caption(TabletCanvas canvas, String key, @Nullable String count, int x, int y, int color) {
        int w = canvas.text(textRenderer, Text.translatable(key).getString(), x, y, color);
        if (count != null) {
            canvas.text(textRenderer, count, x + w + 5, y, color == TabletTheme.TEXT_2 ? TabletTheme.TEXT_3 : color);
        }
    }

    private void drawFuse(TabletCanvas canvas, int slot, float delta, boolean hover) {
        float fx = fuseX(slot);
        float fy = fuseY();
        boolean isBurnt = burnt[slot];
        float progress = isBurnt
                ? MathHelper.lerp(delta, prevFuseCharge[slot], fuseCharge[slot]) / EngineRules.FUSE_HOLD_TICKS
                : 1.0F;
        boolean held = isBurnt && hover && fuseHold;
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
        if (fuseFlash[slot] > 0.0F) {
            canvas.glow(fx + FUSE_W / 2.0F, fy + FUSE_H / 2.0F, 16,
                    TabletTheme.withAlpha(TabletTheme.SUCCESS, (int) (0x70 * fuseFlash[slot])));
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
        float cx = gaugeCx(index);
        float cy = gaugeCy();
        float r = gaugeR();
        float raw = MathHelper.lerp(delta, prevNeedle[index], needle[index]);
        float value = raw;
        boolean shaking = (repair && gaugesPowered() && !set[index] && endState == END_NONE)
                || (!repair && condition == EngineCondition.DAMAGED);
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
            float[] c = cracks[index];
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
        float ly = lampCy();
        canvas.circle(cx, ly, 3.4F, INK);
        if (set[index]) {
            canvas.glow(cx, ly, 8.0F, TabletTheme.withAlpha(TabletTheme.SUCCESS, 0x60));
            canvas.circle(cx, ly, 2.4F, TabletTheme.SUCCESS);
        } else {
            canvas.circle(cx, ly, 2.4F, dim(0xFF5A2326, lit));
        }

        if (repair) {
            drawKnob(canvas, cx, knobCy(), hover, lit);
            float k = (float) Math.toRadians(-135.0F + 270.0F * target[index]);
            canvas.capsule(cx, knobCy(), cx + MathHelper.sin(k) * 4.6F, knobCy() - MathHelper.cos(k) * 4.6F, 1.4F,
                    dim(GEAR, lit));
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

    private void drawTachometer(TabletCanvas canvas, float delta, boolean scorched, float lit) {
        float cx = columnCx();
        float cy = tachCy();
        float r = TACH_R;
        float tachLit = repair ? lit : 1.0F;
        int brass = scorched ? TabletTheme.mix(BRASS, BRASS_BURNT, 0.45F) : BRASS;
        canvas.circle(cx, cy + 1, r + 3.5F, 0x70000000);
        canvas.circle(cx, cy, r + 3, BRASS_DARK);
        canvas.ring(cx, cy, r + 3, 2.4F, brass);
        canvas.arc(cx, cy, r + 3, 1.0F, -80, 70, TabletTheme.withAlpha(BRASS_LIGHT, scorched ? 0x50 : 0xA0));
        canvas.circle(cx, cy, r, scorched ? DIAL_FACE_CHAR : DIAL_FACE);
        canvas.glow(cx, cy - r * 0.35F, r * 0.9F, 0x12FFFFFF);

        float value = MathHelper.lerp(delta, prevTach, tach);
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
            float[] c = cracks[EngineRules.MAX_GAUGES - 1];
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

    private void drawRestartButton(TabletCanvas canvas, float delta, float time, int mouseX, int mouseY) {
        float bx = buttonX();
        float by = buttonY();
        boolean ready = restartReady();
        boolean done = engineCaught();
        // A failed close (interrupted, or wrecked mid quick repair) empties the button and tints it red, never green.
        // 失败的关闭（中断，或抢修途中动力损毁）会清空按钮并染成暗红，绝不显示为绿色。
        boolean failed = endState == END_FAILURE;
        boolean pressed = holdingRestart && ready;
        boolean hover = ready && inside(mouseX, mouseY, bx - 4, by - 4, BUTTON_W + 8, BUTTON_H + 8);
        float progress = failed ? 0.0F : done ? 1.0F : MathHelper.lerp(delta, prevCharge, charge) / holdTicks;

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
            canvas.textCentered(textRenderer, note, (int) columnCx(), (int) (by + BUTTON_H + 6), noteColor);
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

    private void playSound(SoundEvent sound, float pitch, float volume) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
        }
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
