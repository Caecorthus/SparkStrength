package annina.sparkstrength.client.role.taotie;

import annina.sparkstrength.component.taotie.TaotieHeadPlayerComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the Taotie head line right-aligned, directly above NoellesRoles' Taotie HUD stack.
 * 在 NoellesRoles 饕餮 HUD 栈正上方右对齐绘制头颅技能行。
 *
 * <p>NoellesRoles' {@code TaotieHudMixin} has no public layout API, so its stack height is replicated here from the
 * same synced client state and in the same order: moment, swallowed count, swallow cooldown, swallow hint. Each
 * visible line takes {@code getWrappedLinesHeight(text, 999999)} plus a 2 px gap, measured from the screen bottom with
 * no padding. Keep this in sync if NoellesRoles changes that HUD.
 * NoellesRoles 的 TaotieHudMixin 没有公开布局接口，这里按相同顺序（时刻、吞噬数、吞噬冷却、吞噬提示）从同一份
 * 已同步客户端状态复刻其高度：每个可见行占 getWrappedLinesHeight(text, 999999) 加 2 像素间隔，从屏幕底部算起，
 * 无边距。若 NoellesRoles 修改该 HUD，这里需同步调整。</p>
 */
public final class TaotieHeadHudRenderer {
    private static final int WRAP_WIDTH = 999999;
    private static final int LINE_GAP = 2;
    private static final double SWALLOW_HINT_RANGE = 3.0;

    private TaotieHeadHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.TAOTIE)) {
            return;
        }

        TaotiePlayerComponent taotie = TaotiePlayerComponent.KEY.get(player);
        Text line = stateText(TaotieHeadPlayerComponent.KEY.get(player).getCooldownTicks(), taotie.getSwallowedCount());
        if (line == null) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int y = context.getScaledWindowHeight()
                - noellesTaotieStackHeight(renderer, taotie)
                - renderer.getWrappedLinesHeight(line, WRAP_WIDTH);
        int x = context.getScaledWindowWidth() - renderer.getWidth(line);
        context.drawTextWithShadow(renderer, line, x, y, Noellesroles.TAOTIE.color());
    }

    private static @Nullable Text stateText(int cooldownTicks, int swallowedCount) {
        if (cooldownTicks > 0) {
            return Text.translatable("hud.sparkstrength.taotie_head.cooldown", (cooldownTicks + 19) / 20);
        }
        if (swallowedCount > 0) {
            return Text.translatable("hud.sparkstrength.taotie_head.ready",
                    TaotieHeadClientHooks.secondarySkillKeyText());
        }
        return null;
    }

    /** Mirrors NoellesRoles' TaotieHudMixin line by line. / 逐行镜像 NoellesRoles 的 TaotieHudMixin。 */
    private static int noellesTaotieStackHeight(TextRenderer renderer, TaotiePlayerComponent taotie) {
        int height = 0;
        if (taotie.isTaotieMomentActive()) {
            height += lineHeight(renderer, Text.translatable("tip.taotie.moment_active",
                    taotie.getTaotieMomentTicks() / 20));
        }
        int swallowedCount = taotie.getSwallowedCount();
        if (swallowedCount > 0) {
            height += lineHeight(renderer, Text.translatable("tip.taotie.swallowed_count", swallowedCount));
        }
        int swallowCooldown = taotie.getSwallowCooldown();
        if (swallowCooldown > 0) {
            height += lineHeight(renderer, Text.translatable("tip.noellesroles.cooldown", swallowCooldown / 20));
        }
        PlayerEntity target = NoellesrolesClient.crosshairTarget;
        if (target != null
                && NoellesrolesClient.crosshairTargetDistance <= SWALLOW_HINT_RANGE
                && !SwallowedPlayerComponent.KEY.get(target).isSwallowed()
                && swallowCooldown <= 0) {
            KeyBinding abilityBind = NoellesrolesClient.abilityBind;
            String keyName = abilityBind != null ? abilityBind.getBoundKeyLocalizedText().getString() : "G";
            height += lineHeight(renderer, Text.translatable("tip.taotie.swallow",
                    keyName, target.getName().getString()));
        }
        return height;
    }

    private static int lineHeight(TextRenderer renderer, Text text) {
        return renderer.getWrappedLinesHeight(text, WRAP_WIDTH) + LINE_GAP;
    }
}
