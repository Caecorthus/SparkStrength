package annina.sparkstrength.mixin.serialkiller;

import annina.sparkstrength.role.serialkiller.SerialPistolInventoryRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Excludes the serial pistols from Wathe's death-drop loop, so no ShouldDropOnDeath listener can turn one into a
 * pickup; killPlayer's psycho stop and the guard sweep remove them instead.
 * 将连环手枪排除出 Wathe 的死亡掉落流程，任何 ShouldDropOnDeath 监听都无法让其变成可拾取物；改由 killPlayer 的疯魔结束与防护清扫移除。
 */
@Mixin(GameFunctions.class)
public abstract class SerialPistolDeathDropMixin {
    @Inject(method = "shouldDropOnDeath", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$excludeSerialPistol(ItemStack stack, PlayerEntity victim,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (SerialPistolInventoryRules.blocksDeathDrop(stack)) {
            cir.setReturnValue(false);
        }
    }
}
