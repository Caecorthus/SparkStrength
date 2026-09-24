package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.item.m67.M67ShopService;
import annina.sparkstrength.tablet.TabletShopService;
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
    // Append after all provider listeners (including list-clearing shops), respecting their final grenade
    // permissions. The tablet goes last: purchases resolve by index, so a client/server tablet eligibility
    // mismatch can never shift any other entry. Also feeds round-start stock (initializeShopsForPlayers).
    // 在提供方所有监听器（包括会清空列表的商店）之后追加，遵守最终的手雷购买权限。平板放在最后：
    // 购买按下标解析，客户端/服务端平板资格不一致也绝不会使其他条目错位。开局库存初始化同样经过此处。
    @ModifyReturnValue(method = "getShopEntriesForPlayer", at = @At("RETURN"))
    private static List<ShopEntry> sparkstrength$appendM67(
            List<ShopEntry> original, @Local(argsOnly = true) PlayerEntity player
    ) {
        return TabletShopService.appendToFinalEntries(player, M67ShopService.appendToFinalEntries(player, original));
    }
}
