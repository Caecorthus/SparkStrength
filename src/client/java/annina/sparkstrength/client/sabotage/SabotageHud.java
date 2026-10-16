package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.ChannelProgressS2CPacket;
import annina.sparkstrength.sabotage.SabotageRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * HUD of the sabotage system: the prying/hacking bar under the crosshair. Darkness comes only from the sky-light block
 * ({@link SabotageSkyLight}), shader packs included; a screen-wide veil would also dim the daytime windows and the
 * breaker outline players need to find the repair.
 * 破坏系统的 HUD：准星下方的撬开/入侵进度条。黑暗只来自屏蔽天空光照（{@link SabotageSkyLight}），光影包下也一样；
 * 全屏遮罩会连白天的车窗和玩家找电闸要靠的红框一起压暗。
 */
final class SabotageHud {
    private static final int BAR_WIDTH = 92;
    private static final int BAR_HEIGHT = 5;

    private SabotageHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        SabotageClientState.Channel channel = SabotageClientState.channel();
        if (channel != null) {
            renderBar(client, context, channel, client.world.getTime(), tickCounter.getTickDelta(true));
        }
    }

    private static void renderBar(MinecraftClient client, DrawContext context, SabotageClientState.Channel channel,
                                  long now, float tickDelta) {
        TextRenderer textRenderer = client.textRenderer;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        float fraction = channel.fraction(now, tickDelta);
        boolean pry = channel.kind == ChannelProgressS2CPacket.KIND_PRY;
        boolean finished = channel.finishedFlashTicks > 0;
        int accent = finished ? TabletTheme.SUCCESS : (pry ? TabletTheme.WARNING : TabletTheme.KILLER.base());
        Text label;
        if (finished) {
            label = Text.translatable(pry ? "hud.sparkstrength.sabotage.pried" : "hud.sparkstrength.sabotage.hacked");
        } else if (pry) {
            int durability = Math.round((1.0F - fraction) * SabotageRules.DURABILITY_MAX);
            label = Text.translatable("hud.sparkstrength.sabotage.prying", SabotageRules.percent(durability));
        } else {
            float seconds = Math.max(0.0F, (1.0F - fraction) * channel.total / 20.0F);
            label = Text.translatable("hud.sparkstrength.sabotage.hacking", String.format("%.1f", seconds));
        }
        int scale = Math.max(1, (int) Math.round(client.getWindow().getScaleFactor()));
        TabletCanvas canvas = new TabletCanvas(context, scale, client.getWindow().getFramebufferHeight());
        float x = (w - BAR_WIDTH) / 2.0F;
        float y = h / 2.0F + 14.0F;
        canvas.roundRect(x - 1, y - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, 3.5F, 0xB0000000);
        canvas.roundRect(x, y, BAR_WIDTH, BAR_HEIGHT, 2.5F, TabletTheme.SURFACE_SUNKEN);
        float fill = BAR_WIDTH * fraction;
        if (fill > 0.5F) {
            canvas.roundRect(x, y, fill, BAR_HEIGHT, 2.5F, accent);
        }
        canvas.textCentered(textRenderer, label.getString(), w / 2, (int) (y + BAR_HEIGHT + 4), TabletTheme.TEXT);
        canvas.flush();
    }
}
