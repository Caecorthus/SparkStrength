package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.SparkStrengthItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.util.HiddenEquipmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds SparkStrength tablet to NoellesRoles' hidden-equipment filter.
 * 将 SparkStrength 平板加入 NoellesRoles 的隐藏装备过滤器。
 */
@Mixin(value = HiddenEquipmentHelper.class, remap = false)
public abstract class HiddenEquipmentHelperMixin {
    @Inject(method = "shouldHideItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sparkstrength$hideTablet(
            ItemStack stack,
            PlayerEntity holder,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (stack.isOf(SparkStrengthItems.tablet())) {
            cir.setReturnValue(true);
        }
    }
}
