package annina.sparkstrength.mixin.sabotage;

import annina.sparkstrength.sabotage.EngineEconomy;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The server scope of the broken-engine shop discount: every {@code ShopEntry.price()} read during this purchase
 * (Charisma's redirect, the BEFORE listeners, team funds, {@code completePurchase}, AFTER and record bookkeeping) gets
 * the buyer's discounted price, decided once when the purchase starts. Wathe returns at once for a non-server player.
 * 动力损毁商店折扣的服务端作用域：这次购买期间的每一次 ShopEntry.price() 读取（魅力的 redirect、BEFORE 监听器、团队资金、
 * completePurchase、AFTER 与记录记账）都拿到买家的折后价，折扣在购买开始时判定一次。不是服务端玩家时 Wathe 会立刻返回。
 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class EngineShopPurchaseMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @WrapMethod(method = "tryBuy(I)V", require = 1, allow = 1)
    private void sparkstrength$engineWreckDiscountScope(int index, Operation<Void> original) {
        if (player instanceof ServerPlayerEntity buyer) {
            EngineEconomy.purchase(buyer, () -> original.call(index));
        } else {
            original.call(index);
        }
    }
}
