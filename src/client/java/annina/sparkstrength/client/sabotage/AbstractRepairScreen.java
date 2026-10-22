package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.CancelBreakerRepairC2SPacket;
import annina.sparkstrength.network.sabotage.CompleteBreakerRepairC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The session plumbing both sabotage repair panels share (circuit breaker and engine unit): the hold that fills the
 * charge, the submit once the server's minimum time has passed and the retry when its answer never comes, the server's
 * close, and the cancel sent on the way out. Each panel keeps its own layout, painting, rules and sounds.
 * 两种破坏维修面板（电闸与动力装置）共用的会话管线：按住充能、过了服务端最短用时才提交、收不到回复时允许重试、
 * 服务端关闭，以及离开时发送的取消包。各面板保留自己的布局、绘制、规则与音效。
 */
abstract class AbstractRepairScreen extends Screen {
    private static final int CLOSE_DELAY_TICKS = 16;
    /** No answer this long after submitting: the packet was dropped (e.g. dazed), so let the player try again. / 提交后这么久没有回复：包被丢弃（例如被眩晕），允许玩家重试。 */
    private static final int REPLY_TIMEOUT_TICKS = 40;
    /** Margin over the server's minimum time. / 相对服务端最短用时的余量。 */
    private static final int SUBMIT_MARGIN_TICKS = 2;

    private final int sessionId;
    private final int holdTicks;
    private final int minTicks;
    private int openTicks;
    private int awaitTicks;
    private int closeTicks = -1;
    private float charge;
    private float prevCharge;
    private boolean submitted;
    private boolean cancelSent;
    @Nullable
    protected Text banner;
    protected int bannerColor;

    protected AbstractRepairScreen(Text title, int sessionId, int holdTicks, int minTicks) {
        super(title);
        this.sessionId = sessionId;
        this.holdTicks = Math.max(1, holdTicks);
        this.minTicks = minTicks;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Whether a server session stands behind this panel; without one it is read only and never sends anything. / 面板背后是否有服务端会话；没有会话时只读，不发送任何包。 */
    protected boolean hasSession() {
        return true;
    }

    /** Lets go of every hold. / 松开所有按住操作。 */
    protected abstract void releaseHolds();

    protected final int openTicks() {
        return openTicks;
    }

    /** Submitted and awaiting the reply, or closed by the server. / 已提交等待回复，或已被服务端关闭。 */
    protected final boolean submitted() {
        return submitted;
    }

    /** The server closed the session: the panel shows the outcome, then closes. / 服务端已关闭会话：面板显示结果后关闭。 */
    protected final boolean closing() {
        return closeTicks >= 0;
    }

    // ------------------------------------------------------------------------------------------------ tick

    /**
     * The order both panels rely on: the panel's own easing, then the close delay, then (with a session) the wait for
     * the server's answer, and only then the repair itself.
     * 两种面板依赖的顺序：先是面板自身的缓动，然后是关闭延迟，再是（有会话时）等待服务端回复，最后才是维修本身。
     */
    @Override
    public final void tick() {
        openTicks++;
        prevCharge = charge;
        tickPanel();
        if (closeTicks >= 0) {
            tickClosing();
            if (--closeTicks <= 0) {
                close();
            }
            return;
        }
        if (!hasSession()) {
            tickReadOnly();
            return;
        }
        if (submitted) {
            if (awaitTicks > 0 && --awaitTicks == 0) {
                submitted = false;
                charge = 0.0F;
                prevCharge = 0.0F;
                onReplyTimeout();
            }
            return;
        }
        tickRepair();
    }

    /** Every tick, closing or not: the panel's easing and live state. / 每个 tick（包括关闭中）：面板的缓动与实时状态。 */
    protected abstract void tickPanel();

    /** Each tick of the close delay, before it counts down. / 关闭延迟中的每个 tick，在倒计时之前。 */
    protected void tickClosing() {
    }

    /** Each tick of a panel without a session (not closing). / 无会话面板的每个 tick（未在关闭）。 */
    protected void tickReadOnly() {
    }

    /** Each tick of a live repair: the controls, the hold and the submit. / 维修进行中的每个 tick：控件、按住与提交。 */
    protected abstract void tickRepair();

    /** The completion never arrived and the charge is empty again; the server session is still open. / 完成包没有送达，充能已清空；服务端会话仍然有效。 */
    protected abstract void onReplyTimeout();

    // ------------------------------------------------------------------------------------------------ hold and submit

    /** One tick of the hold: fills by one while held, drains twice as fast otherwise. / 按住一 tick：按住时加一，否则以两倍速度回落。 */
    protected final void stepCharge(boolean held) {
        if (held) {
            charge = Math.min(holdTicks, charge + 1.0F);
        } else {
            charge = Math.max(0.0F, charge - 2.0F);
        }
    }

    protected final boolean charged() {
        return charge >= holdTicks;
    }

    protected final float chargeFraction() {
        return charge / holdTicks;
    }

    /** The charge between ticks, as a fraction of the hold. / tick 之间插值后的充能，占按住时长的比例。 */
    protected final float chargeProgress(float delta) {
        return MathHelper.lerp(delta, prevCharge, charge) / holdTicks;
    }

    /** The server's minimum time, plus a margin, has passed since the panel opened. / 面板打开后已过服务端最短用时加余量。 */
    protected final boolean submitDue() {
        return openTicks >= minTicks + SUBMIT_MARGIN_TICKS;
    }

    /** Sends the completion and waits for the server's answer under this banner. / 发送完成包，并在此横幅下等待服务端回复。 */
    protected final void submit(Text waiting) {
        submitted = true;
        awaitTicks = REPLY_TIMEOUT_TICKS;
        banner = waiting;
        bannerColor = TabletTheme.SUCCESS;
        ClientPlayNetworking.send(new CompleteBreakerRepairC2SPacket(sessionId));
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            releaseHolds();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------------------------------------ session end

    /**
     * Every way off this screen ends here: close() goes through setScreen(null), and another screen replacing this one
     * calls removed() without close(). So the cancel lives here, sent at most once.
     * 离开此界面的所有路径都会到这里：close() 走 setScreen(null)，被其他界面替换时只调用 removed() 而不调用 close()。
     * 因此取消包放在这里发送，且最多发送一次。
     */
    @Override
    public final void removed() {
        releaseHolds();
        sendCancelOnce();
        super.removed();
    }

    /**
     * Drops the server session unless there is none, it is already answered (submitted, awaiting the reply) or the
     * server closed it, or the connection is gone.
     * 放弃服务端会话；没有会话、已提交等待回复、服务端已关闭、或连接已断开时不发送。
     */
    private void sendCancelOnce() {
        if (cancelSent || !hasSession() || submitted || closeTicks >= 0) {
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
        closedByServer(reason);
        closeTicks = CLOSE_DELAY_TICKS;
    }

    /** Shows the outcome for a CloseBreakerRepairS2CPacket reason. / 按 CloseBreakerRepairS2CPacket 的原因显示结果。 */
    protected abstract void closedByServer(int reason);

    // ------------------------------------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int scale = Math.max(1, (int) Math.round(client.getWindow().getScaleFactor()));
        TabletCanvas canvas = new TabletCanvas(context, scale, client.getWindow().getFramebufferHeight());
        // One draw batch for the panel (its clips still flush). / 整个面板一个绘制批次（裁剪仍会提交）。
        canvas.batch(() -> drawScreen(canvas, mouseX, mouseY, delta));
    }

    protected abstract void drawScreen(TabletCanvas canvas, int mouseX, int mouseY, float delta);
}
