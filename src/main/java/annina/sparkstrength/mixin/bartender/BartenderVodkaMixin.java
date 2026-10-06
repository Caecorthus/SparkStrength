package annina.sparkstrength.mixin.bartender;

import annina.sparkstrength.role.bartender.BartenderRules;
import org.agmas.noellesroles.item.ingredient.VodkaItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Vodka's on-drink cooldown cut: every running cooldown ends at {@link BartenderRules#VODKA_INSTANT_COOLDOWN_KEEP}
 * (see {@link BartenderRules#vodkaInstantCutFactor} for why the factor is not that value itself).
 * 伏特加生效瞬间的冷却削减：所有进行中的冷却最终保留 VODKA_INSTANT_COOLDOWN_KEEP（倍率为何不直接等于该值见 vodkaInstantCutFactor）。
 */
@Mixin(value = VodkaItem.class, remap = false)
public abstract class BartenderVodkaMixin {
    @ModifyConstant(method = "reduceAllCooldowns", constant = @Constant(floatValue = 0.8F))
    private static float sparkstrength$deeperInstantCut(float upstreamFactor) {
        return BartenderRules.vodkaInstantCutFactor();
    }
}
