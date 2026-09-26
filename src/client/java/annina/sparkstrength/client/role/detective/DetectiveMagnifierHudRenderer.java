package annina.sparkstrength.client.role.detective;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.role.detective.DetectiveRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

/**
 * Shows the detective's selected case above the bottom-right ability row while a magnifier is held.
 * 侦探手持放大镜时，在右下角技能行上方显示当前调查的命案。
 *
 * <p>Reads only the owner-synced case component; the server stays authoritative for cases and the limit.
 * 仅读取同步给本人的命案组件；命案与上限均以服务端为准。</p>
 */
public final class DetectiveMagnifierHudRenderer {
    private static final int RIGHT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int ROW_GAP = 4;
    private static final int TEXT_COLOR = 0x6495ED;
    // Hotbar footprint: x in [w/2 - 91, w/2 + 91], top at h - 22. / 快捷栏范围：x ∈ [w/2 - 91, w/2 + 91]，顶部在 h - 22。
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_CLEARANCE = 2;
    // Wathe rows over the hotbar's right half, measured up from the screen bottom: StaminaRenderer icons at h - 39,
    // AirRenderer icons at h - 49 (9 px each), CooldownRenderer's selected-slot timer at h - 22 - 4 - fontHeight.
    // Wathe 在快捷栏右半侧上方的行（自屏幕底部向上）：体力图标 h - 39、氧气图标 h - 49（各高 9 像素），
    // 选中格冷却计时文字 h - 22 - 4 - 字高。
    private static final int WATHE_STAMINA_ROW_TOP = 39;
    private static final int WATHE_AIR_ROW_TOP = 49;
    private static final int WATHE_COOLDOWN_TEXT_GAP = 4;
    private static final int VICTIM_NAME_MAX_WIDTH = 96;
    // About four characters plus the ellipsis; anything narrower reads as "E…". / 约四个字符加省略号，更窄会变成“E…”。
    private static final int MIN_NAME_ROOM = 32;
    private static final String ELLIPSIS = "…";

    private DetectiveMagnifierHudRenderer() {
    }

    public static void render(DrawContext context, ClientPlayerEntity player) {
        // Spectators (e.g. Taotie-swallowed) cannot use the magnifier server-side. / 旁观者（如被饕餮吞下）无法使用放大镜。
        if (!GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator() || !isHoldingMagnifier(player)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!DetectiveRules.isDetective(role)) {
            return;
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        // The usual row shares its height with the hotbar, so the line must fit between the hotbar and the right edge.
        // 常规行与快捷栏同高，文字须放在快捷栏与屏幕右缘之间。
        int budget = screenWidth - RIGHT_PADDING - (screenWidth / 2 + HOTBAR_HALF_WIDTH + HOTBAR_CLEARANCE);
        Text line = stateText(DetectiveCasePlayerComponent.KEY.get(player), renderer, budget);
        int lineWidth = renderer.getWidth(line);
        int x = screenWidth - RIGHT_PADDING - lineWidth;
        int y = screenHeight - BOTTOM_PADDING - (renderer.fontHeight * 2) - ROW_GAP;
        // Only when even the fixed text cannot fit there, lift the line above every Wathe row over the hotbar.
        // 仅当固定文字本身也放不下时，才把整行抬到快捷栏上方所有 Wathe 行之上。
        if (lineWidth > budget) {
            y = Math.min(y, screenHeight - watheRowsTop(renderer) - ROW_GAP - renderer.fontHeight);
        }
        context.drawTextWithShadow(renderer, line, x, y, TEXT_COLOR);
    }

    private static boolean isHoldingMagnifier(ClientPlayerEntity player) {
        Item magnifier = SparkStrengthItems.magnifier();
        return player.getMainHandStack().isOf(magnifier) || player.getOffHandStack().isOf(magnifier);
    }

    private static int watheRowsTop(TextRenderer renderer) {
        int cooldownTextTop = HOTBAR_HEIGHT + WATHE_COOLDOWN_TEXT_GAP + renderer.fontHeight;
        return Math.max(WATHE_AIR_ROW_TOP, Math.max(WATHE_STAMINA_ROW_TOP, cooldownTextTop));
    }

    private static Text stateText(DetectiveCasePlayerComponent component, TextRenderer renderer, int budget) {
        DetectiveCasePlayerComponent.DetectiveCase selected = component.getSelectedCase();
        if (selected == null) {
            return Text.translatable("hud.sparkstrength.detective.no_case");
        }
        int suspects = selected.getSuspects().size();
        int limit = component.getSyncedSuspectLimit();
        // Shorten only the victim name so the line stays beside the hotbar, but never below a readable stub: when fewer
        // than MIN_NAME_ROOM pixels remain, keep the usual name cap and let the too-wide line be lifted instead.
        // 只缩短受害人姓名以保持在快捷栏旁，但不缩到无法辨认：剩余不足 MIN_NAME_ROOM 像素时沿用常规上限，让过宽的整行被抬起。
        int room = budget - renderer.getWidth(activeCaseText("", suspects, limit));
        int nameWidth = room >= MIN_NAME_ROOM
                ? Math.min(VICTIM_NAME_MAX_WIDTH, room)
                : VICTIM_NAME_MAX_WIDTH;
        return activeCaseText(trimName(renderer, selected.getVictimName(), nameWidth), suspects, limit);
    }

    private static Text activeCaseText(String victimName, int suspects, int limit) {
        return Text.translatable("hud.sparkstrength.detective.active_case", victimName, suspects, limit);
    }

    private static String trimName(TextRenderer renderer, String name, int maxWidth) {
        if (renderer.getWidth(name) <= maxWidth) {
            return name;
        }
        return renderer.trimToWidth(name, Math.max(0, maxWidth - renderer.getWidth(ELLIPSIS))) + ELLIPSIS;
    }
}
