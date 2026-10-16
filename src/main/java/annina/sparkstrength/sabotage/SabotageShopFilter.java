package annina.sparkstrength.sabotage;

import annina.sparkstrength.role.engineer.EngineerRules;
import dev.doctor4t.wathe.util.ShopEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Lights sabotage moved to the tablet, so Wathe's {@code blackout} shop entry leaves every final shop list, including
 * the SparkWitch shops that put Wathe's entry back. Pure id filter, the same on client and server (purchases resolve by
 * index).
 * 灯光破坏已移到平板，所以 Wathe 的 blackout 商店条目从所有最终商店列表中移除，包括会把 Wathe 条目放回去的 SparkWitch 商店。
 * 纯 id 过滤，客户端与服务端结果一致（购买按下标解析）。
 */
public final class SabotageShopFilter {
    private SabotageShopFilter() {
    }

    public static List<ShopEntry> withoutBlackout(List<ShopEntry> entries) {
        if (entries == null || entries.stream().noneMatch(SabotageShopFilter::isBlackout)) {
            return entries;
        }
        List<ShopEntry> filtered = new ArrayList<>(entries.size());
        for (ShopEntry entry : entries) {
            if (!isBlackout(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private static boolean isBlackout(ShopEntry entry) {
        return entry != null && EngineerRules.WATHE_BLACKOUT_ENTRY_ID.equals(entry.id());
    }
}
