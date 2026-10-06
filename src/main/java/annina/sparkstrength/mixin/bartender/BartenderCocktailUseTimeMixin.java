package annina.sparkstrength.mixin.bartender;

import annina.sparkstrength.role.bartender.BartenderRules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.item.CocktailItem;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.item.BaseSpiritItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spice B (Ember Sugar): a base spirit containing it is downed in one tick instead of Wathe's 40. The answer only
 * reads the stack's synced NBT, so client prediction and server agree. Drinking still finishes through
 * finishUsing, keeping the drinker glow and the Coroner disguise checks.
 * 调料 B（火种方糖）：含它的基酒 1 刻喝完，而非 Wathe 的 40 刻。结果只读物品已同步的 NBT，客户端预测与服务端一致。
 * 饮用仍经由 finishUsing 完成，保留喝酒发光与验尸官伪装检查。
 */
@Mixin(CocktailItem.class)
public abstract class BartenderCocktailUseTimeMixin {
    @ModifyReturnValue(method = "getMaxUseTime", at = @At("RETURN"))
    private int sparkstrength$instantWithEmberSugar(int useTicks, @Local(argsOnly = true) ItemStack stack) {
        if (stack.getItem() instanceof BaseSpiritItem
                && BartenderRules.drinksInstantly(BaseSpiritItem.getIngredients(stack))) {
            return BartenderRules.INSTANT_DRINK_USE_TICKS;
        }
        return useTicks;
    }
}
