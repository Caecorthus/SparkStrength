package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.OpenEngineRepairS2CPacket;
import annina.sparkstrength.sabotage.EngineCondition;
import annina.sparkstrength.sabotage.EngineRepairMode;
import annina.sparkstrength.sabotage.EngineRules;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static annina.sparkstrength.client.sabotage.EngineRepairLayout.FUSE_SLOTS;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.PANEL_W;

/**
 * The engine panel's mini-game: the seeded set-up (green bands, start readings, burnt fuses and the scorch marks
 * drawn from the same seed), the live readings and holds, the rules on them, and each tick's simulation. The read-only
 * status panel runs here too, mirroring the block. Session facts, sounds and the submit come from the screen.
 * 动力装置面板的小游戏：由 seed 决定的初始布局（绿区、初始读数、烧坏的保险丝，以及同一 seed 画出的焦痕）、实时读数与
 * 按住状态、相关规则，以及每个 tick 的模拟。只读的状态面板也在这里运行，跟随方块状态。会话信息、音效与提交由界面提供。
 */
final class EngineRepairGame {
    private static final float BAND_MIN = 0.22F;
    private static final float BAND_SPREAD = 0.04F;
    /** Start positions keep at least this far from the green band and from the stops. / 初始位置与绿区、两端止点至少相隔这么远。 */
    private static final float START_GAP = 0.12F;
    private static final float START_EDGE = 0.03F;
    private static final float NEEDLE_EASE = 0.3F;
    private static final float TACH_GOAL = 0.80F;
    private static final float POWER_OFF = 0.12F;

    static final int END_NONE = 0;
    static final int END_SUCCESS = 1;
    static final int END_FAILURE = 2;

    private final EngineRepairScreen screen;
    private final EngineRepairLayout layout;

    final float[] bandLo = new float[EngineRules.MAX_GAUGES];
    final float[] bandHi = new float[EngineRules.MAX_GAUGES];
    private final float[] start = new float[EngineRules.MAX_GAUGES];
    private final float[] phase = new float[EngineRules.MAX_GAUGES];
    final float[] target = new float[EngineRules.MAX_GAUGES];
    final float[] needle = new float[EngineRules.MAX_GAUGES];
    final float[] prevNeedle = new float[EngineRules.MAX_GAUGES];
    final boolean[] set = new boolean[EngineRules.MAX_GAUGES];
    /** Glass crack per gauge, unit-radius points (x0, y0, x1, y1, x2, y2). / 每块表的玻璃裂纹，单位半径坐标。 */
    final float[][] cracks = new float[EngineRules.MAX_GAUGES][6];

    final boolean[] burnt = new boolean[FUSE_SLOTS];
    private final boolean[] wasBurnt = new boolean[FUSE_SLOTS];
    final float[] fuseCharge = new float[FUSE_SLOTS];
    final float[] prevFuseCharge = new float[FUSE_SLOTS];
    final float[] fuseFlash = new float[FUSE_SLOTS];
    final int burntTotal;

    /** Soot blotches (x, y, radius, alpha) in panel space, for the scorched look. / 焦黑外观的烟灰斑（x、y、半径、alpha），面板坐标。 */
    final float[] soot;
    final float[] panelCrack;

    @Nullable
    EngineCondition condition;
    int endState = END_NONE;
    float tach;
    float prevTach;
    float power;
    float prevPower;
    boolean holdingRestart;
    boolean restarted;
    boolean fuseHold;
    int dragGauge = -1;
    private int chugTicks;
    boolean bannerRetry;

    EngineRepairGame(EngineRepairScreen screen, EngineRepairLayout layout, OpenEngineRepairS2CPacket packet) {
        this.screen = screen;
        this.layout = layout;

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
        int fuses = screen.mode == EngineRepairMode.OVERHAUL
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
            soot[i * 4 + 1] = 26 + fuseRandom.nextFloat() * (layout.panelH - 52);
            soot[i * 4 + 2] = 18 + fuseRandom.nextFloat() * 16;
            soot[i * 4 + 3] = 0x48 + fuseRandom.nextInt(0x40);
        }
        panelCrack = new float[10];
        float cx = 2.0F;
        float cy = layout.panelH - 30 - fuseRandom.nextFloat() * 30;
        for (int i = 0; i < 5; i++) {
            panelCrack[i * 2] = cx;
            panelCrack[i * 2 + 1] = cy;
            cx += 6 + fuseRandom.nextFloat() * 7;
            cy += (fuseRandom.nextFloat() - 0.65F) * 9;
        }

        // Needles boot from their stops: a powered repair panel swings them up to their (red) readings on open.
        // 指针从止点启动：通电的维修面板一打开，指针就摆到各自（红区的）读数。
        if (screen.repair && gaugesPowered()) {
            System.arraycopy(start, 0, target, 0, screen.gaugeCount);
        }
        this.power = gaugesPowered() ? 1.0F : POWER_OFF;
        this.prevPower = power;
    }

    // ------------------------------------------------------------------------------------------------ rules

    boolean fusesDone() {
        for (boolean value : burnt) {
            if (value) {
                return false;
            }
        }
        return true;
    }

    boolean gaugesPowered() {
        return screen.mode != EngineRepairMode.OVERHAUL || fusesDone();
    }

    private boolean inBand(int index, float value) {
        return value >= bandLo[index] && value <= bandHi[index];
    }

    boolean allSet() {
        for (int i = 0; i < screen.gaugeCount; i++) {
            if (!set[i]) {
                return false;
            }
        }
        return true;
    }

    int setCount() {
        int count = 0;
        for (int i = 0; i < screen.gaugeCount; i++) {
            if (set[i]) {
                count++;
            }
        }
        return count;
    }

    int fusesReplaced() {
        int count = 0;
        for (int s = 0; s < FUSE_SLOTS; s++) {
            if (wasBurnt[s] && !burnt[s]) {
                count++;
            }
        }
        return count;
    }

    /** Controls take input only while a live repair is in progress. / 只有维修进行中时控件才接受输入。 */
    boolean live() {
        return screen.repair && !screen.submitted() && !restarted && !screen.closing();
    }

    boolean restartReady() {
        return live() && gaugesPowered() && allSet();
    }

    /**
     * The engine caught (restarted, awaiting the reply, or confirmed). A failed close overrides it, because onServerClose
     * marks every session submitted.
     * 引擎已点着（已重启、等待回复或已确认）。失败的关闭优先于它，因为 onServerClose 会把所有会话标为已提交。
     */
    boolean engineCaught() {
        return endState == END_SUCCESS || (endState == END_NONE && (restarted || screen.submitted()));
    }

    boolean scorched() {
        if (endState == END_SUCCESS) {
            return false;
        }
        return screen.mode == EngineRepairMode.OVERHAUL
                || (screen.mode == EngineRepairMode.STATUS && condition == EngineCondition.WRECKED);
    }

    // ------------------------------------------------------------------------------------------------ tick

    /** Every tick, closing or not: needles, power and tachometer ease towards their targets. / 每个 tick（包括关闭中）：指针、电量与转速表向目标缓动。 */
    void tickEasing() {
        prevTach = tach;
        prevPower = power;
        System.arraycopy(needle, 0, prevNeedle, 0, needle.length);
        System.arraycopy(fuseCharge, 0, prevFuseCharge, 0, fuseCharge.length);
        for (int s = 0; s < FUSE_SLOTS; s++) {
            fuseFlash[s] = Math.max(0.0F, fuseFlash[s] - 0.12F);
        }

        updateTargets();
        for (int i = 0; i < screen.gaugeCount; i++) {
            needle[i] += (target[i] - needle[i]) * NEEDLE_EASE;
        }
        float powerTarget = endState == END_FAILURE ? POWER_OFF : gaugesPowered() ? 1.0F : POWER_OFF;
        power += (powerTarget - power) * 0.2F;
        tach += (tachTarget() - tach) * 0.45F;
    }

    void tickClosing() {
        // Closing: success swings every needle to the middle of its green, failure lets them drop.
        // 关闭中：成功时每根指针摆到绿区中央，失败时指针落回止点。
        for (int i = 0; i < screen.gaugeCount; i++) {
            set[i] = endState == END_SUCCESS && inBand(i, needle[i]);
        }
    }

    void tickReadOnly() {
        for (int i = 0; i < screen.gaugeCount; i++) {
            set[i] = inBand(i, needle[i]);
        }
    }

    /** Where every needle wants to be this tick. / 本 tick 每根指针的目标位置。 */
    private void updateTargets() {
        if (endState == END_SUCCESS) {
            for (int i = 0; i < screen.gaugeCount; i++) {
                target[i] = (bandLo[i] + bandHi[i]) / 2.0F;
            }
            return;
        }
        if (endState == END_FAILURE) {
            for (int i = 0; i < screen.gaugeCount; i++) {
                target[i] = 0.0F;
            }
            return;
        }
        if (screen.repair) {
            // Repair panels: the player sets target[]; a dead overhaul panel rests every needle on its stop until the
            // fuses are in, then they swing up to their (red) readings.
            // 维修面板：target[] 由玩家调；大修面板在换好保险丝之前不通电，指针都靠在止点，通电后摆到各自（红区的）读数。
            if (!gaugesPowered()) {
                for (int i = 0; i < screen.gaugeCount; i++) {
                    target[i] = 0.0F;
                }
            }
            return;
        }
        float t = screen.openTicks();
        for (int i = 0; i < screen.gaugeCount; i++) {
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
        float t = screen.openTicks();
        if (screen.repair) {
            if (restarted || screen.submitted()) {
                return TACH_GOAL + 0.012F * MathHelper.sin(t * 0.4F);
            }
            return screen.chargeFraction() * TACH_GOAL;
        }
        if (condition == EngineCondition.INTACT) {
            return TACH_GOAL + 0.02F * MathHelper.sin(t * 0.07F);
        }
        if (condition == EngineCondition.DAMAGED) {
            return 0.28F + 0.06F * MathHelper.sin(t * 0.31F) + 0.03F * MathHelper.sin(t * 0.83F);
        }
        return 0.0F;
    }

    /** Replaces the burnt fuse held under the cursor. / 更换光标下按住的烧坏保险丝。 */
    void tickFuses(double mouseX, double mouseY) {
        if (screen.mode != EngineRepairMode.OVERHAUL || fusesDone()) {
            return;
        }
        int hovered = fuseHold ? layout.fuseAt(mouseX, mouseY) : -1;
        for (int s = 0; s < FUSE_SLOTS; s++) {
            if (!burnt[s]) {
                continue;
            }
            if (s == hovered) {
                fuseCharge[s] = Math.min(EngineRules.FUSE_HOLD_TICKS, fuseCharge[s] + 1.0F);
                if (fuseCharge[s] >= EngineRules.FUSE_HOLD_TICKS) {
                    burnt[s] = false;
                    fuseFlash[s] = 1.0F;
                    screen.playSound(SparkStrengthSounds.CIRCUIT_BREAKER_SWITCH, 1.1F + 0.08F * fusesReplaced(), 0.6F);
                    if (fusesDone()) {
                        fuseHold = false;
                        // Power is back on the gauges: their needles now swing to their readings.
                        // 压力表重新通电：指针摆到各自的读数。
                        System.arraycopy(start, 0, target, 0, screen.gaugeCount);
                        screen.playSound(SoundEvents.BLOCK_COPPER_BULB_TURN_ON, 0.8F, 0.8F);
                    }
                }
            } else {
                fuseCharge[s] = Math.max(0.0F, fuseCharge[s] - 1.0F);
            }
        }
    }

    void tickGauges() {
        boolean powered = gaugesPowered();
        for (int i = 0; i < screen.gaugeCount; i++) {
            // Set only when both the knob and the needle sit in the band, so a needle swinging past on power-up does
            // not light the lamp.
            // 只有旋钮目标和指针都在绿区内才算调好，避免通电时指针掠过绿区误亮灯。
            boolean now = powered && inBand(i, target[i]) && inBand(i, needle[i]);
            if (now && !set[i]) {
                screen.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.5F + 0.1F * i, 0.35F);
            }
            set[i] = now;
        }
    }

    void tickRestart() {
        if (!restarted) {
            boolean held = holdingRestart && restartReady();
            screen.stepCharge(held);
            if (held) {
                float progress = screen.chargeFraction();
                if (--chugTicks <= 0) {
                    // The engine turning over: faster and higher as it spins up.
                    // 引擎在盘车：转得越快，声音越密越高。
                    screen.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), 0.5F + 0.7F * progress, 0.45F);
                    chugTicks = Math.max(2, 6 - (int) (progress * 5.0F));
                }
            } else {
                chugTicks = 0;
            }
            if (screen.charged()) {
                restarted = true;
                holdingRestart = false;
                screen.playSound(SoundEvents.ENTITY_IRON_GOLEM_REPAIR, 1.0F, 0.7F);
                if (!screen.submitDue()) {
                    screen.banner = Text.translatable("screen.sparkstrength.engine.settling");
                    screen.bannerColor = TabletTheme.WARNING;
                    bannerRetry = false;
                }
            }
        }
        // Done but faster than the server's minimum: keep the engine idling until that time has passed (the server
        // starts its clock before this screen opens, so waiting here is always enough).
        // 已完成但快于服务端最短用时：让引擎保持怠速直到时间够了（服务端的计时早于界面打开，因此在这里等就足够）。
        if (restarted && screen.submitDue()) {
            bannerRetry = false;
            screen.submit(Text.translatable("screen.sparkstrength.engine.restarting"));
        }
    }
}
