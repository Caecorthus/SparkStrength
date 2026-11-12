package annina.sparkstrength.client.ui.shop;

import annina.sparkstrength.role.bandit.BanditRevolverRules;
import dev.doctor4t.wathe.util.ShopEntry;

/**
 * Client-only split of a Wathe shop into two pages. Wathe draws every entry in one row; the Bandit's parts and rounds
 * add seven, so they go on a second page behind a switch button ({@code ShopPageMixin}). Purely presentational: the
 * entry list and the purchase indexes are untouched. The page survives closing and reopening the inventory.
 * 仅客户端把 Wathe 商店分成两页。Wathe 把所有商品画在一行；强盗的配件与子弹多出七个，因此放到第二页，由切换按钮翻页
 * （见 {@code ShopPageMixin}）。纯展示：商品列表与购买下标都不变。关闭再打开背包时保持在刚才那一页。
 */
public final class ShopPages {
    private static int page;

    private ShopPages() {
    }

    /** 0 for the regular shop, 1 for the workbench page. / 0 为常规商店，1 为改装页。 */
    public static int pageOf(ShopEntry entry) {
        return entry.id().startsWith(BanditRevolverRules.SHOP_ENTRY_PREFIX) ? 1 : 0;
    }

    public static int current() {
        return page;
    }

    public static void toggle() {
        page = page == 0 ? 1 : 0;
    }
}
