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
 * style and colour. Ride time and the cooldown (ride + rest, or the round-start lock) both come from the synced ride
 * component, read on {@link VultureClock}, the clock the Super Curse line uses too.
 * 秃鹫滑板状态行，以相同风格与颜色绘制在 NoellesRoles 右下角秃鹫提示的上一行。滑行剩余时间与冷却（滑行 + 冷却，
 * 或开局锁）都来自同步的滑行组件，并按 {@link VultureClock} 读取，超级骂一行也用这个时钟。
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
            // The synced lock on the shared Vulture clock; never "ready" while the server still reports the lock. Only
            // a cooldown that is not ours (no lock window) falls back to the vanilla entry.
            // 按秃鹫共用时钟读取同步的锁；服务端仍报告有锁时绝不显示“就绪”。只有不属于本模组的冷却（无锁区间）才回退到原版条目。
            int cooldownTicks = SkateboardRideComponent.KEY.get(player).hasLock()
                    ? Math.max(1, SkateboardCooldownClient.lockRemaining(player))
                    : cooldownTicks(player.getItemCooldownManager(), board);
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
        return VultureSkateboardRules.displaySeconds(ticks);
    }
}
