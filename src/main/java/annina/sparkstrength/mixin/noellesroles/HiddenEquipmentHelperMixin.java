package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.role.professor.ProfessorSerumRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.util.HiddenEquipmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds SparkStrength hidden items to NoellesRoles' hidden-equipment filter.
 * 将 SparkStrength 平板和教授试剂加入 NoellesRoles 的隐藏装备过滤器。
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
            return;
        }

        if ((stack.isOf(SparkStrengthItems.invisibilitySerum())
                || stack.isOf(SparkStrengthItems.doorpassingPotion())
                || stack.isOf(SparkStrengthItems.sedative())
                || stack.isOf(SparkStrengthItems.truthSerum()))
                && ProfessorSerumRules.isProfessor(GameWorldComponent.KEY.get(holder.getWorld()).getRole(holder))) {
            // 用户需求是“教授手持四种试剂时不可见”，所以这里额外校验持有者确实是教授。
            cir.setReturnValue(true);
        }
    }
}
