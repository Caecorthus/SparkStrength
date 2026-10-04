package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.vulture.SkateboardRideComponent;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import annina.sparkstrength.role.vulture.VultureSkateboardRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.text.Text;

/**
 * The Vulture's skateboard state line, drawn one line above NoellesRoles' own bottom-right Vulture line in the same
 * style and colour. Ride time comes from the synced ride component, the cooldown (ride + rest, or the round-start lock)
 * from the server-set vanilla item cooldown.
 * 秃鹫滑板状态行，以相同风格与颜色绘制在 NoellesRoles 右下角秃鹫提示的上一行。滑行剩余时间来自同步的滑行组件，
 * 冷却（滑行 + 冷却，或开局锁）来自服务端设置的原版物品冷却。
 */
public final class SkateboardHud {
    /** NoellesRoles' Vulture role colour. / NoellesRoles 秃鹫职业颜色。 */
    private static final int VULTURE_COLOR = 0xB56700;

    private SkateboardHud() {
    }

    public static void render(TextRenderer renderer, ClientPlayerEntity player, DrawContext context) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !VultureSkateboardRules.isVulture(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        Item board = SparkStrengthItems.skateboard();
        if (!player.getInventory().contains(stack -> stack.isOf(board))) {
            return;
        }

        SkateboardRideComponent ride = SkateboardRideComponent.KEY.get(player);
        Text text;
        if (ride.isRiding()) {
            // Never show 0 while the server still reports the ride. / 服务端仍报告滑行时绝不显示 0。
            text = Text.translatable("tip.sparkstrength.skateboard.riding", Math.max(1, seconds(ride.remainingTicks())));
        } else {
            int cooldownTicks = cooldownTicks(player.getItemCooldownManager(), board);
            text = cooldownTicks > 0
                    ? Text.translatable("tip.sparkstrength.skateboard.cooldown", seconds(cooldownTicks))
                    : Text.translatable("tip.sparkstrength.skateboard.ready");
        }
        int x = context.getScaledWindowWidth() - renderer.getWidth(text);
        int y = context.getScaledWindowHeight() - renderer.fontHeight * 2;
        context.drawTextWithShadow(renderer, text, x, y, VULTURE_COLOR);
    }

    private static int cooldownTicks(ItemCooldownManager manager, Item item) {
        Object entry = ((ItemCooldownManagerAccessor) manager).sparkstrength$getEntries().get(item);
        return entry == null ? 0 : ((ItemCooldownEntryAccessor) entry).sparkstrength$getEndTick()
                - ((ItemCooldownManagerAccessor) manager).sparkstrength$getTick();
    }

    private static int seconds(int ticks) {
        return Math.ceilDiv(ticks, 20);
    }
}
