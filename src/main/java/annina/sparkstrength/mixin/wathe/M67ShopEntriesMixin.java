package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.item.m67.M67ShopService;
import annina.sparkstrength.role.bomber.drone.DroneShopService;
import annina.sparkstrength.role.perfumer.PerfumerShopService;
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
    // permissions. Perfumer kit first (real Perfumer only), then M67, then the bomb drone (real Bomber only);
    // purchases resolve by index, so all three must give the same answer on client and server. Also feeds
    // round-start stock (initializeShopsForPlayers).
    // The tablet is no longer sold (granted by TabletShopService), so it never appears here.
    // 在提供方所有监听器（包括会清空列表的商店）之后追加，遵守最终的手雷购买权限。先追加调香师道具（仅真实调香师），
    // 再追加 M67，最后追加炸弹无人机（仅真实炸弹客）；购买按下标解析，因此三者在客户端与服务端必须结果一致。
    // 开局库存初始化同样经过此处。平板已不再出售（由 TabletShopService 发放），因此不会出现在这里。
    @ModifyReturnValue(method = "getShopEntriesForPlayer", at = @At("RETURN"))
    private static List<ShopEntry> sparkstrength$appendM67(
            List<ShopEntry> original, @Local(argsOnly = true) PlayerEntity player
    ) {
        return DroneShopService.appendToFinalEntries(player,
                M67ShopService.appendToFinalEntries(player, PerfumerShopService.appendToFinalEntries(player, original)));
    }
}
