package annina.sparkstrength.item;

import annina.sparkstrength.role.bartender.BartenderRules;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.item.IngredientItem;

/**
 * Bartender spice B (Ember Sugar): the drink is downed instantly (see {@code BartenderCocktailUseTimeMixin}).
 * It has no effect of its own and does not lift the base spirit's debuff; that is still the ice cube's job. Any
 * glass holding it is named 冠生以花 (生死相依 alongside spice A), so it needs no suffix.
 * 酒保调料 B（火种方糖）：这杯酒瞬间喝完（见 BartenderCocktailUseTimeMixin）。自身无效果，也不消除基酒负面效果，那仍是冰块的作用。
 * 含它的酒一律名为冠生以花（与调料 A 同杯为生死相依），因此不需要后缀。
 */
public final class EmberSugarItem extends IngredientItem {
    public EmberSugarItem(Settings settings) {
        super(settings, BartenderRules.EMBER_SUGAR_ID);
    }

    @Override
    public void applyEffect(ServerPlayerEntity player, float durationMultiplier) {
    }

    @Override
    public boolean isModifier() {
        return true;
    }

    @Override
    public int getDisplayColorRgb() {
        return BartenderRules.EMBER_SUGAR_RGB;
    }

    @Override
    public int getShopPrice() {
        return BartenderRules.EMBER_SUGAR_PRICE;
    }
}
