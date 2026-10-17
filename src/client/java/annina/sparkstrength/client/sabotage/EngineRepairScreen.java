package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.network.sabotage.CancelBreakerRepairC2SPacket;
import annina.sparkstrength.network.sabotage.CompleteBreakerRepairC2SPacket;
import annina.sparkstrength.network.sabotage.OpenEngineRepairS2CPacket;
import annina.sparkstrength.sabotage.EngineRepairMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * PLACEHOLDER engine unit panel, to be replaced wholesale by the real vector-drawn panel. It only exercises the server
 * flow: a repair session submits itself just after the server's minimum time, and closing it early cancels the
 * session. A status panel (session 0) submits nothing.
 * 占位用的动力装置面板，会被真正的矢量面板整体替换。它只用来走通服务端流程：维修会话在服务端最短用时之后自动提交，提前关闭则
 * 放弃会话。状态面板（会话 0）不提交任何东西。
 */
final class EngineRepairScreen extends Screen {
    private static final int SUBMIT_MARGIN_TICKS = 2;

    private final OpenEngineRepairS2CPacket packet;
    private int openTicks;
    private boolean submitted;
    private boolean closedByServer;

    EngineRepairScreen(OpenEngineRepairS2CPacket packet) {
        super(Text.translatable("block.sparkstrength.engine_unit"));
        this.packet = packet;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        openTicks++;
        if (packet.sessionId() != 0 && !submitted && openTicks >= packet.minTicks() + SUBMIT_MARGIN_TICKS) {
            submitted = true;
            ClientPlayNetworking.send(new CompleteBreakerRepairC2SPacket(packet.sessionId()));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(EngineRepairMode.byWire(packet.modeWire()).name()), width / 2, height / 2 + 4, 0xAAAAAA);
    }

    @Override
    public void close() {
        if (packet.sessionId() != 0 && !closedByServer) {
            ClientPlayNetworking.send(new CancelBreakerRepairC2SPacket(packet.sessionId()));
        }
        super.close();
    }

    void onServerClose(int sessionId, int reason) {
        if (sessionId != packet.sessionId() || closedByServer) {
            return;
        }
        closedByServer = true;
        close();
    }
}
