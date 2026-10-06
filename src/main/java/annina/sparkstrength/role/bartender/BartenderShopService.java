package annina.sparkstrength.role.bartender;

import annina.sparkstrength.SparkStrengthItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.item.IngredientItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the Bartender buff to a final shop list: reprices the base spirit and every ingredient, and lists spice A
 * and spice B right after the special spice. Keyed on the list itself (any shop selling the special spice is a
 * Bartender shop), so the real Bartender and the Coroner's Bartender disguise both get it, and client and server
 * build the same list, which Wathe buys from by index.
 * 将酒保增强应用到最终商店列表：重新定价基酒与所有调剂，并把调料 A、B 排在特调香料之后。按列表本身判定
 * （出售特调香料的商店即酒保商店），因此真实酒保与验尸官的酒保伪装都会生效，且客户端与服务端得到相同列表
 * （Wathe 按下标购买）。
 */
public final class BartenderShopService {
    private BartenderShopService() {
    }

    public static List<ShopEntry> applyToFinalEntries(List<ShopEntry> entries) {
        int spiceIndex = -1;
        boolean touchesBartender = false;
        for (int i = 0; i < entries.size(); i++) {
            Item item = entries.get(i).stack().getItem();
            if (item == ModItems.BASE_SPIRIT || item instanceof IngredientItem) {
                touchesBartender = true;
            }
            if (item == ModItems.SPECIAL_SPICE) {
                spiceIndex = i;
            }
        }
        if (!touchesBartender) {
            return entries;
        }

        List<ShopEntry> result = new ArrayList<>(entries.size() + 2);
        for (int i = 0; i < entries.size(); i++) {
            result.add(repriced(entries.get(i)));
            if (i == spiceIndex) {
                addSpice(result, entries, SparkStrengthItems.ghostflameBitters(), BartenderRules.GHOSTFLAME_BITTERS_ENTRY_ID,
                        BartenderRules.GHOSTFLAME_BITTERS_PRICE);
                addSpice(result, entries, SparkStrengthItems.emberSugar(), BartenderRules.EMBER_SUGAR_ENTRY_ID,
                        BartenderRules.EMBER_SUGAR_PRICE);
            }
        }
        return result;
    }

    /**
     * Rebuilds a base-spirit or ingredient entry at the buff price. Upstream lists them with the legacy
     * {@code ShopEntry(stack, price, type)} constructor, so there is no custom buy handler to carry over. Subclassed
     * entries (another mod's wrapper) are left alone rather than unwrapped.
     * 以增强后的价格重建基酒或调剂条目。上游用旧版 ShopEntry(stack, price, type) 构造器上架它们，因此没有自定义购买处理需要保留。
     * 子类条目（其他模组的包装）原样保留，不拆包。
     */
    private static ShopEntry repriced(ShopEntry entry) {
        int price = priceFor(entry.stack().getItem());
        if (price < 0 || price == entry.price() || entry.getClass() != ShopEntry.class) {
            return entry;
        }
        ShopEntry.Builder builder = new ShopEntry.Builder(entry.id(), entry.stack(), price, entry.type())
                .cooldown(entry.cooldownTicks())
                .initialCooldown(entry.initialCooldownTicks())
                .stock(entry.maxStock());
        if (entry.getActualStack() != entry.stack()) {
            builder.actualStack(entry.getActualStack());
        }
        return builder.build();
    }

    private static int priceFor(Item item) {
        if (item == ModItems.BASE_SPIRIT) {
            return BartenderRules.BASE_SPIRIT_PRICE;
        }
        return item instanceof IngredientItem ingredient ? BartenderRules.ingredientPrice(ingredient.getIngredientId()) : -1;
    }

    private static void addSpice(List<ShopEntry> result, List<ShopEntry> original, Item spice, String entryId, int price) {
        for (ShopEntry entry : original) {
            if (entryId.equals(entry.id()) || entry.stack().isOf(spice)) {
                return;
            }
        }
        result.add(new ShopEntry.Builder(entryId, new ItemStack(spice), price, ShopEntry.Type.POISON).build());
    }
}
