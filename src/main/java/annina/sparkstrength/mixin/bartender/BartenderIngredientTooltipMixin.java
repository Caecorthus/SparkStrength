package annina.sparkstrength.mixin.bartender;

import annina.sparkstrength.role.bartender.BartenderRules;
import org.agmas.noellesroles.item.IngredientItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Shows the retuned effect text for the ingredients this buff changes (see {@link BartenderRules#effectTextKey}).
 * 为本次增强改动过的调剂显示新的效果说明（见 BartenderRules#effectTextKey）。
 */
@Mixin(IngredientItem.class)
public abstract class BartenderIngredientTooltipMixin {
    @ModifyArg(
            method = "appendTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;)Lnet/minecraft/text/MutableText;")
    )
    private String sparkstrength$retunedEffectText(String key) {
        return BartenderRules.effectTextKey(key);
    }
}
