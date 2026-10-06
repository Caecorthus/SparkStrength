package annina.sparkstrength.item;

import annina.sparkstrength.role.bartender.BartenderCooldownService;
import annina.sparkstrength.role.bartender.BartenderRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.item.IngredientItem;

/**
 * Bartender spice A (Ghostflame Bitters): when the drink takes effect, every item cooldown in the drinker's
 * inventory is cleared. Any glass holding it is named 冠死以冕 (生死相依 alongside spice B), so it needs no suffix.
 * 酒保调料 A（幽焰苦精）：酒效生效时清空饮用者背包内所有物品冷却。含它的酒一律名为冠死以冕（与调料 B 同杯为生死相依），因此不需要后缀。
 */
public final class GhostflameBittersItem extends IngredientItem {
    public GhostflameBittersItem(Settings settings) {
        super(settings, BartenderRules.GHOSTFLAME_BITTERS_ID);
    }

    @Override
    public void applyEffect(ServerPlayerEntity player, float durationMultiplier) {
        BartenderCooldownService.clearInventoryCooldowns(player);
    }

    @Override
    public boolean isModifier() {
        return true;
    }

    @Override
    public int getDisplayColorRgb() {
        return BartenderRules.GHOSTFLAME_BITTERS_RGB;
    }

    @Override
    public int getShopPrice() {
        return BartenderRules.GHOSTFLAME_BITTERS_PRICE;
    }
}
