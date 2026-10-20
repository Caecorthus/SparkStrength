package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wathe drops some items on death for others to loot; the Undercover's mask, tomahawk and outfit stay with the body.
 * Wathe 会让部分物品在死亡时掉落供人拾取；卧底的面具、战术斧与黑衣留在尸体身上。
 */
@Mixin(GameFunctions.class)
public abstract class UndercoverDeathDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$undercoverGearStays(ItemStack stack, PlayerEntity victim,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (UndercoverService.isBound(stack)) {
            cir.setReturnValue(false);
        }
    }
}
