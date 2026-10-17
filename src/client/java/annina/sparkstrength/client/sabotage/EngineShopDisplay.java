package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.client.screen.tablet.TabletTheme;
import annina.sparkstrength.sabotage.EngineEconomy;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Wathe's shop while the engine is broken. The discounted number itself comes from {@code ShopEntry.price()} on the
 * client thread; this only tints it green on Wathe's price tag (the coin glyph keeps its own look, no strikethrough of
 * the old price) and rebuilds an open shop when the bonus starts or ends, since SparkTraits' Charisma entries store the
 * price they were built with.
 * 动力损毁期间的 Wathe 商店。折后的数字本身来自客户端线程上的 ShopEntry.price()；这里只把 Wathe 价签上的数字染成绿色（金币图标
 * 保持原样，不给原价划线），并在加成开始或结束时重建打开着的商店，因为 SparkTraits 魅力条目保存的是构建时的价格。
 */
public final class EngineShopDisplay {
    /** Wathe's coin glyph after the number on its price tag. / Wathe 价签上数字后面的金币字形。 */
    private static final String COIN = "\uE781";
    private static final int DISCOUNT_GREEN = TabletTheme.SUCCESS & 0xFFFFFF;

    private EngineShopDisplay() {
    }

    /**
     * Wathe's price tag for {@code entry}, its number green when the discount changed it. Any label other than Wathe's
     * own "number + coin" (SparkWitch's mana and stamp labels) is left alone.
     * entry 的 Wathe 价签，折扣改变了价格时数字变绿。不是 Wathe 自己的「数字 + 金币」的标签（SparkWitch 的魔力与邮票标签）原样保留。
     */
    public static Text tintPrice(ShopEntry entry, Text label) {
        if (entry == null || label == null || !EngineEconomy.shownDiscounted(entry)) {
            return label;
        }
        String number = String.valueOf(entry.price());
        if (!label.getString().equals(number + COIN)) {
            return label;
        }
        return Text.empty().append(Text.literal(number).styled(style -> style.withColor(DISCOUNT_GREEN))).append(COIN);
    }

    /**
     * The bonus just started or ended: an open shop rebuilds its entries, exactly like a window resize does.
     * 加成刚开始或刚结束：打开着的商店重建条目，和调整窗口大小时完全一样。
     */
    static void refreshOpenShop(MinecraftClient client) {
        if (client.currentScreen instanceof LimitedInventoryScreen screen) {
            screen.resize(client, screen.width, screen.height);
        }
    }
}
