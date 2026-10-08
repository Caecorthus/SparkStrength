package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.client.compat.SparkWitchSecondaryKeyCompat;
import annina.sparkstrength.component.vulture.VultureSuperCursePlayerComponent;
import annina.sparkstrength.network.vulture.VultureSuperCurseC2SPacket;
import annina.sparkstrength.role.vulture.VultureSuperCurseRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.Objects;

/**
 * 秃鹫“超级骂”状态行，右对齐画在右下角第三行：第一行是 NoellesRoles 的秃鹫提示，第二行是滑板状态
 * （开局即发放，通常常驻），因此固定占第三行，滑板行出现或消失时本行不跳动。
 * The Vulture's Super Curse line, right-aligned on the third bottom-right row: row one is NoellesRoles' Vulture tip,
 * row two the skateboard line (granted at round start, so usually present). A fixed row keeps this line still when the
 * board line comes and goes.
 *
 * <p>只渲染服务端同步的冷却；按键不可用（无 SparkWitch 钩子或服务端不收此包）时整行隐藏。
 * Renders only the server-synced cooldown; hidden entirely when the key cannot reach the skill.</p>
 */
public final class VultureSuperCurseHud {
    /** Rows counted from the bottom: NoellesRoles tip, skateboard, this. / 自底向上：NoellesRoles 提示、滑板、本行。 */
    private static final int ROW_FROM_BOTTOM = 3;

    private VultureSuperCurseHud() {
    }

    public static void render(TextRenderer renderer, ClientPlayerEntity player, DrawContext context) {
        if (!VultureSuperCurseClientHooks.isKeyAvailable()
                || !ClientPlayNetworking.canSend(VultureSuperCurseC2SPacket.ID)
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!VultureSuperCurseRules.isVulture(role)) {
            return;
        }

        int cooldownTicks = VultureSuperCursePlayerComponent.KEY.get(player).getCooldownTicks();
        Text text = cooldownTicks > 0
                ? Text.translatable("hud.sparkstrength.vulture_super_curse.cooldown", Math.ceilDiv(cooldownTicks, 20))
                : Text.translatable("hud.sparkstrength.vulture_super_curse.ready", keyText());
        int x = context.getScaledWindowWidth() - renderer.getWidth(text);
        int y = context.getScaledWindowHeight() - renderer.fontHeight * ROW_FROM_BOTTOM;
        context.drawTextWithShadow(renderer, text, x, y, role.color());
    }

    private static Text keyText() {
        // SparkWitch's default binding if its key name cannot be read. / 无法读取按键名时显示 SparkWitch 默认键。
        return Objects.requireNonNullElseGet(SparkWitchSecondaryKeyCompat.boundKeyText(), () -> Text.literal("N"));
    }
}
