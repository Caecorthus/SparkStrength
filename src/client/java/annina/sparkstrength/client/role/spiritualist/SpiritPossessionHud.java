package annina.sparkstrength.client.role.spiritualist;

import annina.sparkstrength.client.compat.SparkWitchSecondaryKeyCompat;
import annina.sparkstrength.component.spiritualist.SpiritPossessionPlayerComponent;
import annina.sparkstrength.role.spiritualist.SpiritPossessionRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.spiritualist.SpiritPlayerComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the possession line right-aligned, directly above NoellesRoles' single Spiritualist HUD line. Outside a
 * possession it only shows while this client knows an active Wraith, so rounds without Wraiths stay quiet.
 * 在 NoellesRoles 灵界行者单行 HUD 的正上方右对齐绘制附身行。非附身期间只有本客户端已知活跃冤魂时才显示，没有冤魂的对局保持安静。
 *
 * <p>NoellesRoles' {@code SpiritHudMixin} has no layout API, so its line is rebuilt here from the same synced state
 * (projecting, cooldown, ready) to measure it: {@code getWrappedLinesHeight(text, 999999)} from the screen bottom, no
 * padding. Keep this in sync if NoellesRoles changes that HUD.
 * NoellesRoles 的 SpiritHudMixin 没有布局接口，这里按同一份已同步状态（出窍中、冷却、可用）重建其文本来测量高度：
 * 从屏幕底部算起 getWrappedLinesHeight(text, 999999)，无边距。若 NoellesRoles 修改该 HUD，这里需同步调整。</p>
 */
public final class SpiritPossessionHud {
    private static final int WRAP_WIDTH = 999999;
    private static final int LINE_GAP = 2;

    private SpiritPossessionHud() {
    }

    public static void render(DrawContext context, @Nullable ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == null || !SpiritPossessionClient.isKeyAvailable() || !SpiritPossessionClient.isLocalSpiritualist(player)) {
            return;
        }
        Text line = stateText(client, player);
        if (line == null) {
            return;
        }
        TextRenderer renderer = client.textRenderer;
        int y = context.getScaledWindowHeight()
                - renderer.getWrappedLinesHeight(noellesSpiritLine(player), WRAP_WIDTH) - LINE_GAP
                - renderer.getWrappedLinesHeight(line, WRAP_WIDTH);
        int x = context.getScaledWindowWidth() - renderer.getWidth(line);
        context.drawTextWithShadow(renderer, line, x, y, Noellesroles.SPIRIT_WALKER.color());
    }

    private static @Nullable Text stateText(MinecraftClient client, ClientPlayerEntity player) {
        Text key = keyText();
        if (SpiritPossessionClient.isPossessing()) {
            return Text.translatable("hud.sparkstrength.spirit_possession.active",
                    SpiritPossessionRules.displaySeconds(SpiritPossessionClient.remainingTicks()), key);
        }
        if (!SpiritPossessionClient.anyWraithKnown(client)) {
            return null;
        }
        int cooldown = SpiritPossessionPlayerComponent.KEY.get(player).getCooldownTicks();
        if (cooldown > 0) {
            return Text.translatable("hud.sparkstrength.spirit_possession.cooldown",
                    SpiritPossessionRules.displaySeconds(cooldown));
        }
        return SpiritPossessionClient.aimedWraith() != null
                ? Text.translatable("hud.sparkstrength.spirit_possession.aimed", key)
                : Text.translatable("hud.sparkstrength.spirit_possession.ready", key);
    }

    /** Mirrors NoellesRoles' SpiritHudMixin text choice. / 镜像 NoellesRoles SpiritHudMixin 的文本选择。 */
    private static Text noellesSpiritLine(ClientPlayerEntity player) {
        Text ability = NoellesrolesClient.abilityBind != null
                ? NoellesrolesClient.abilityBind.getBoundKeyLocalizedText()
                : Text.literal("G");
        if (SpiritPlayerComponent.KEY.get(player).isProjecting()) {
            return Text.translatable("tip.spiritualist.active", ability);
        }
        int cooldown = AbilityPlayerComponent.KEY.get(player).cooldown;
        return cooldown > 0
                ? Text.translatable("tip.noellesroles.cooldown", cooldown / 20)
                : Text.translatable("tip.spiritualist", ability);
    }

    private static Text keyText() {
        Text key = SparkWitchSecondaryKeyCompat.boundKeyText();
        return key != null ? key : Text.literal("N");
    }
}
