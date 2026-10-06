package annina.sparkstrength.mixin.bartender;

import annina.sparkstrength.role.bartender.BartenderRules;
import org.agmas.noellesroles.item.IngredientItem;
import org.agmas.noellesroles.item.ingredient.GinItem;
import org.agmas.noellesroles.item.ingredient.RumItem;
import org.agmas.noellesroles.item.ingredient.TequilaItem;
import org.agmas.noellesroles.item.ingredient.VodkaItem;
import org.agmas.noellesroles.item.ingredient.WhiskeyItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Lengthens the five spirits' effects. Each upstream applyEffect starts from one float tick constant
 * (rum/tequila 140, gin 200, vodka 300, whiskey 400) that is then scaled by the special-liqueur multiplier,
 * so swapping that constant keeps the liqueur doubling intact.
 * 延长五种基础调剂的效果。上游每个 applyEffect 都从一个浮点刻数常量起算（朗姆/龙舌兰 140、金酒 200、
 * 伏特加 300、威士忌 400），再乘特调利口酒倍率，因此只替换该常量即可保留利口酒翻倍。
 * Each target declares a single applyEffect(ServerPlayerEntity, float), so the bare name is unambiguous.
 * 每个目标类只声明一个 applyEffect(ServerPlayerEntity, float)，因此仅写方法名不会有歧义。
 */
@Mixin(value = {RumItem.class, GinItem.class, TequilaItem.class, VodkaItem.class, WhiskeyItem.class}, remap = false)
public abstract class BartenderIngredientDurationMixin {
    @ModifyConstant(
            method = "applyEffect",
            constant = {
                    @Constant(floatValue = 140.0F),
                    @Constant(floatValue = 200.0F),
                    @Constant(floatValue = 300.0F),
                    @Constant(floatValue = 400.0F)
            }
    )
    private float sparkstrength$retuneEffectTicks(float upstreamTicks) {
        return BartenderRules.effectTicks(((IngredientItem) (Object) this).getIngredientId(), upstreamTicks);
    }
}
