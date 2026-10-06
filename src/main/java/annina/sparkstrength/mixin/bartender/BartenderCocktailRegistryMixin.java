package annina.sparkstrength.mixin.bartender;

import annina.sparkstrength.role.bartender.BartenderCocktailNames;
import annina.sparkstrength.role.bartender.BartenderRules;
import net.minecraft.text.MutableText;
import org.agmas.noellesroles.bartender.CocktailRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Names the signature cocktails (冠死以冕, 冠生以花, 生死相依) ahead of upstream's ingredient-set lookup. The item name and
 * the replay's drink lines both go through these two methods, so they always agree. Signature names carry no
 * modifier suffix; the tooltip still lists every ingredient.
 * 在上游按调剂集合查表之前为特调（冠死以冕、冠生以花、生死相依）命名。物品名与回放中的饮品行都经过这两个方法，因此始终一致。
 * 特调名不带修饰后缀，提示中仍列出全部调剂。
 */
@Mixin(value = CocktailRegistry.class, remap = false)
public abstract class BartenderCocktailRegistryMixin {
    @Inject(method = "getEntry", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$signatureEntry(
            List<String> ingredients, CallbackInfoReturnable<CocktailRegistry.CocktailEntry> cir
    ) {
        BartenderRules.Signature signature = BartenderRules.signatureOf(ingredients);
        if (signature != null) {
            cir.setReturnValue(new CocktailRegistry.CocktailEntry(signature.translationKey(), signature.mainRgb()));
        }
    }

    @Inject(method = "getCocktailName", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$signatureName(List<String> ingredients, CallbackInfoReturnable<MutableText> cir) {
        BartenderRules.Signature signature = BartenderRules.signatureOf(ingredients);
        if (signature != null) {
            cir.setReturnValue(BartenderCocktailNames.signatureName(signature));
        }
    }
}
