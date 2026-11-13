package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.CloseBreakerRepairS2CPacket;
import annina.sparkstrength.network.sabotage.OpenEngineRepairS2CPacket;
import annina.sparkstrength.sabotage.EngineCondition;
import annina.sparkstrength.sabotage.EngineRepairMode;
import annina.sparkstrength.sabotage.EngineRules;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static annina.sparkstrength.client.sabotage.EngineRepairGame.END_FAILURE;
import static annina.sparkstrength.client.sabotage.EngineRepairGame.END_SUCCESS;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.BUTTON_H;
import static annina.sparkstrength.client.sabotage.EngineRepairLayout.BUTTON_W;

/**
 * Engine unit panel. Quick repair: turn every pressure needle into its green band, then hold Restart until the
 * tachometer climbs into the green. Overhaul: replace the burnt fuses first (the gauges stay dark until then), then the
 * same gauges and a longer restart. Status (session 0): the same panel, read only, mirroring the live block state.
 * The server only accepts the result after its minimum time and re-checks the session.
 * 动力装置面板。抢修：把每根压力表指针调进绿区，再按住「重启」直到转速表爬进绿区。大修：先换掉烧黑的保险丝（在此之前
 * 压力表不通电），再调表、更长的重启。状态面板（会话 0）：同一块面板，只能看，跟随方块的实时状态。服务端只在最短用时
 * 之后才接受结果，并重新校验会话。
 * <p>
 * This class is the Minecraft screen: the session hooks, the input and whatever needs the client. The geometry lives in
 * {@link EngineRepairLayout}, the mini-game in {@link EngineRepairGame} and the painting in {@link EngineRepairPainter}.
 * 本类是 Minecraft 界面：会话钩子、输入以及需要客户端的部分。几何布局在 {@link EngineRepairLayout}，小游戏在
 * {@link EngineRepairGame}，绘制在 {@link EngineRepairPainter}。
 */
final class EngineRepairScreen extends AbstractRepairScreen {
    private static final float DRAG_PER_PX = 0.01F;
    private static final float SCROLL_STEP = 0.045F;
    private static final float RATCHET_STEP = 0.05F;

    private final BlockPos pos;
    final EngineRepairMode mode;
    /** A real repair session; otherwise a read-only status panel that never sends anything. / 真正的维修会话；否则是只读的状态面板，不发送任何包。 */
    final boolean repair;
    final int gaugeCount;
    private final EngineRepairLayout layout;
    private final EngineRepairGame game;
    private final EngineRepairPainter painter;

    private double lastMouseX = -1;
    private double lastMouseY = -1;
    private float ratchet;
    private int soundCooldown;

    EngineRepairScreen(OpenEngineRepairS2CPacket packet) {
        super(Text.translatable(titleKey(EngineRepairMode.byWire(packet.modeWire()), packet.sessionId())),
                packet.sessionId(), packet.holdTicks(), Math.max(0, packet.minTicks()));
        this.pos = packet.pos();
        EngineRepairMode wireMode = EngineRepairMode.byWire(packet.modeWire());
        this.repair = wireMode != EngineRepairMode.STATUS && packet.sessionId() != 0;
        this.mode = repair ? wireMode : EngineRepairMode.STATUS;
        this.gaugeCount = MathHelper.clamp(packet.gauges(), 1, EngineRules.MAX_GAUGES);
        // The parts read the fields above, so they are built after them. / 各部分会读取上面的字段，因此在其后创建。
        this.layout = new EngineRepairLayout(this);
        this.game = new EngineRepairGame(this, layout, packet);
        this.painter = new EngineRepairPainter(this, game, layout);
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
    protected void init() {
        super.init();
        game.condition = liveCondition();
    }

    // ------------------------------------------------------------------------------------------------ state

    @Override
    protected boolean hasSession() {
        return repair;
    }

    @Nullable
    private EngineCondition liveCondition() {
        if (client == null || client.world == null) {
            return null;
        }
        BlockState state = client.world.getBlockState(pos);
        return state.isOf(SparkStrengthBlocks.engineUnit()) ? state.get(EngineUnitBlock.CONDITION) : null;
    }

    // ------------------------------------------------------------------------------------------------ tick

    @Override
    protected void tickPanel() {
        if (soundCooldown > 0) {
            soundCooldown--;
        }
        game.condition = liveCondition();
        if (!leftButtonDown()) {
            // A release can be swallowed (stun mixins cancel every mouse event, overlays eat releases) or the window
            // can lose focus mid-hold, so the physical button state is the source of truth for every hold.
            // 松开事件可能被吞掉（眩晕 mixin 会取消所有鼠标事件、覆盖层会吃掉松开），也可能在按住时窗口失焦，
            // 因此所有按住操作都以物理按键状态为准。
            releaseHolds();
        }
        game.tickEasing();
    }

    @Override
    protected void tickClosing() {
        game.tickClosing();
    }

    @Override
    protected void tickReadOnly() {
        game.tickReadOnly();
    }

    @Override
    protected void onReplyTimeout() {
        // The completion never arrived; the server session is still open, so restart again.
        // 完成包没有送达；服务端会话仍然有效，重新按住重启即可。
        game.restarted = false;
        banner = Text.translatable("screen.sparkstrength.engine.retry");
        bannerColor = TabletTheme.DANGER;
        game.bannerRetry = true;
    }

    @Override
    protected void tickRepair() {
        game.tickFuses(lastMouseX, lastMouseY);
        game.tickGauges();
        game.tickRestart();
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (button == 0 && repair && !closing()) {
            if (mode == EngineRepairMode.OVERHAUL && layout.fuseAt(mouseX, mouseY) >= 0) {
                if (game.live() && !game.fusesDone()) {
                    game.fuseHold = true;
                }
                return true;
            }
            for (int i = 0; i < gaugeCount; i++) {
                if (layout.overGauge(mouseX, mouseY, i)) {
                    if (game.live() && game.gaugesPowered()) {
                        game.dragGauge = i;
                        ratchet = 0.0F;
                    } else {
                        playSound(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6F, 0.25F);
                    }
                    return true;
                }
            }
            if (inside(mouseX, mouseY, layout.buttonX() - 4, layout.buttonY() - 4, BUTTON_W + 8, BUTTON_H + 8)) {
                if (game.restartReady()) {
                    game.holdingRestart = true;
                    if (game.bannerRetry) {
                        banner = null;
                        game.bannerRetry = false;
                    }
                } else if (!game.restarted && !submitted()) {
                    playSound(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6F, 0.25F);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void releaseHolds() {
        game.holdingRestart = false;
        game.fuseHold = false;
        game.dragGauge = -1;
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
        if (button == 0 && game.dragGauge >= 0) {
            if (game.live() && game.gaugesPowered()) {
                turn(game.dragGauge, (float) deltaX * DRAG_PER_PX);
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
        if (repair && !closing()) {
            double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
            for (int i = 0; i < gaugeCount; i++) {
                if (layout.overGauge(mouseX, mouseY, i)) {
                    if (game.live() && game.gaugesPowered() && amount != 0.0) {
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
        float before = game.target[index];
        game.target[index] = MathHelper.clamp(before + delta, 0.0F, 1.0F);
        ratchet += Math.abs(game.target[index] - before);
        if (ratchet >= RATCHET_STEP) {
            ratchet = 0.0F;
            if (soundCooldown == 0) {
                soundCooldown = 1;
                playSound(SoundEvents.BLOCK_COMPARATOR_CLICK, 1.6F + 0.4F * game.target[index], 0.2F);
            }
        }
    }

    @Override
    protected void closedByServer(int reason) {
        switch (reason) {
            case CloseBreakerRepairS2CPacket.REASON_REPAIRED -> {
                banner = Text.translatable("screen.sparkstrength.engine.repaired");
                bannerColor = TabletTheme.SUCCESS;
                game.endState = END_SUCCESS;
            }
            case CloseBreakerRepairS2CPacket.REASON_POWER_BACK -> {
                banner = Text.translatable("screen.sparkstrength.engine.power_back");
                bannerColor = TabletTheme.SUCCESS;
                game.endState = END_SUCCESS;
            }
            case CloseBreakerRepairS2CPacket.REASON_PHASE_CHANGED -> {
                banner = Text.translatable("screen.sparkstrength.engine.phase_changed");
                bannerColor = TabletTheme.DANGER;
                game.endState = END_FAILURE;
            }
            default -> {
                banner = Text.translatable("screen.sparkstrength.engine.interrupted");
                bannerColor = TabletTheme.DANGER;
                game.endState = END_FAILURE;
            }
        }
        game.bannerRetry = false;
    }

    // ------------------------------------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void drawScreen(TabletCanvas canvas, int mouseX, int mouseY, float delta) {
        painter.paint(canvas, textRenderer, mouseX, mouseY, delta);
    }

    void playSound(SoundEvent sound, float pitch, float volume) {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
        }
    }
}
