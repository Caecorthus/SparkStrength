package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.util.ShopStacks;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the Party Balloon (25) to the real Party Animal's shop, right after Wathe's firecracker. Built on both sides
 * from the synced role alone, because Wathe buys by index; not gated on round state, like the other shops.
 * 在真实派对狂的商店中加入派对气球（25），紧跟在 Wathe 鞭炮之后。两端只依据已同步的身份构建，因为 Wathe 按下标购买；
 * 与其他商店一样不依赖对局状态。
 */
public final class PartyBalloonShopService {
    private static boolean registered;

    private PartyBalloonShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(PartyBalloonShopService::buildShopEntries);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (player == null
                || !PartyAnimalRules.isPartyAnimal(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        List<String> ids = new ArrayList<>(context.size());
        for (int i = 0; i < context.size(); i++) {
            ids.add(context.getEntry(i).id());
        }
        Item balloon = SparkStrengthItems.partyBalloon();
        context.addEntry(PartyBalloonRules.shopInsertIndex(ids),
                SparkTraitsCompat.discountShopEntryForCharisma(player, new ShopEntry.Builder(
                        PartyBalloonRules.ENTRY_ID,
                        ShopStacks.display(balloon.getDefaultStack(), "shop.sparkstrength.party_balloon"),
                        PartyBalloonRules.PRICE,
                        ShopEntry.Type.TOOL
                ).actualStack(balloon.getDefaultStack())
                        .onBuy(buyer -> ShopStacks.giveStackable(buyer, balloon))
                        .build()));
    }
}
