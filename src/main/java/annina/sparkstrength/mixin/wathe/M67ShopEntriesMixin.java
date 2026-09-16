package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.item.m67.M67ShopService;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.doctor4t.wathe.util.ShopUtils;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = ShopUtils.class, remap = false)
public abstract class M67ShopEntriesMixin {
    // Append after all provider listeners, respecting their final grenade permissions.
    // 在提供方所有监听器之后追加，遵守最终的手雷购买权限。
    @ModifyReturnValue(method = "getShopEntriesForPlayer", at = @At("RETURN"))
    private static List<ShopEntry> sparkstrength$appendM67(
            List<ShopEntry> original, @Local(argsOnly = true) PlayerEntity player
    ) {
        return M67ShopService.appendToFinalEntries(player, original);
    }
}
