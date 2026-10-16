package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.client.screen.tablet.TabletCanvas;
import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.network.sabotage.ChannelProgressS2CPacket;
import annina.sparkstrength.sabotage.SabotageRules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;

import java.lang.reflect.Method;

/**
 * HUD of the sabotage system: the prying/hacking bar under the crosshair, and a darkness veil while an Iris shader pack
 * is active (packs add their own minimum light and may let sunlight through windows, which the sky-light block alone
 * cannot remove).
 * 破坏系统的 HUD：准星下方的撬开/入侵进度条，以及 Iris 光影包启用时的黑暗遮罩（光影包会加入自己的最低亮度，
 * 还可能让阳光穿过车窗，仅靠屏蔽天空光照无法去除）。
 */
final class SabotageHud {
    private static final int BAR_WIDTH = 92;
    private static final int BAR_HEIGHT = 5;
    private static final int SHADER_VEIL = 0x99000000;

    private static boolean irisResolved;
    private static Object irisApi;
    private static Method irisShaderPackInUse;

    private SabotageHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        if (SabotageSkyLight.isActive() && !player.hasStatusEffect(StatusEffects.NIGHT_VISION) && shaderPackInUse()) {
            context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), SHADER_VEIL);
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

    private static boolean shaderPackInUse() {
        if (!irisResolved) {
            irisResolved = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisApi = api.getMethod("getInstance").invoke(null);
                    irisShaderPackInUse = api.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    irisApi = null;
                    irisShaderPackInUse = null;
                }
            }
        }
        if (irisShaderPackInUse == null) {
            return false;
        }
        try {
            // Asked every frame: the player can toggle shaders at any time. / 每帧询问：玩家随时可以开关光影。
            return Boolean.TRUE.equals(irisShaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
