package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.role.serialkiller.SerialPistolInventoryRules;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A serial pistol never becomes an item entity, whatever the drop path (vanilla death drops, screen-close cursor
 * returns, server code). A refused copy is simply gone: the pistols exist only during psycho and are never re-granted.
 * 连环手枪无论经由何种丢弃路径（原版死亡掉落、关闭界面退回光标物品、服务端代码）都不会变成掉落物。
 * 被拒绝的副本直接消失：手枪只在疯魔期间存在，也不会补发。
 */
@Mixin(PlayerEntity.class)
public abstract class SerialPistolDropGuardMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void sparkstrength$keepSerialPistolBound(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                                     CallbackInfoReturnable<ItemEntity> cir) {
        if (SerialPistolInventoryRules.blocksDrop(stack)) {
            cir.setReturnValue(null);
        }
    }
}
