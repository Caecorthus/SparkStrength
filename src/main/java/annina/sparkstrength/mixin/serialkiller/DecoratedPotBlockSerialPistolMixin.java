package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.role.serialkiller.SerialPistolInventoryRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a held serial pistol out of a decorated pot on both sides: the pot answers
 * {@code SKIP_DEFAULT_BLOCK_INTERACTION}, so it never takes the pistol, while vanilla still runs the pistol's own use
 * (it fires). Guarding the pot itself also covers Wathe ornaments that forward the click to the pot they hang on.
 * 在双端阻止饰纹陶罐收走手持的连环手枪：陶罐返回 {@code SKIP_DEFAULT_BLOCK_INTERACTION}，不会收走手枪，而原版仍会执行
 * 手枪自身的使用（照常开火）。拦截陶罐本身也覆盖了把点击转发给所挂陶罐的 Wathe 装饰物。
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockSerialPistolMixin {
    @Inject(
            method = "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ItemActionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sparkstrength$keepSerialPistolOutOfPot(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand, BlockHitResult hit,
                                                       CallbackInfoReturnable<ItemActionResult> cir) {
        if (SerialPistolInventoryRules.blocksBlockUse(stack, state)) {
            cir.setReturnValue(ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
