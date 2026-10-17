package annina.sparkstrength.mixin.sabotage;

import annina.sparkstrength.sabotage.EngineEconomy;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.doctor4t.wathe.util.ShopEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The broken-engine shop discount on the listed gold price, so it applies before anything built on {@code price()}.
 * SparkTraits' Charisma (the server redirect in tryBuy, and the wrapper entry it builds on both sides) reads the wrapped
 * entry through it, which gives "20% off first, then Charisma" on both sides; every price tag, info card and purchase
 * check reads the same number. A wrapper's own stored price is left alone, or it would be discounted twice. Only active
 * inside a discounted purchase on the server and on the client thread for an eligible local player (see
 * {@link EngineEconomy}); stamps, mana and ability fees are not shop gold prices.
 * 动力损毁的商店折扣作用在上架金币价上，所以先于所有基于 price() 的计算。SparkTraits 的魅力（服务端 tryBuy 里的 redirect，
 * 以及它在两端构建的包装条目）都通过它读取被包装的条目，两端都是「先 8 折、再魅力」；每个价签、信息卡和购买检查读到的都是
 * 同一个数。包装条目自己保存的价格不再处理，否则会打两次折。只在服务端打折的购买之内、以及客户端线程上本地玩家有资格时生效
 * （见 EngineEconomy）；邮票、魔力和技能费用都不是商店金币价。
 */
@Mixin(value = ShopEntry.class, remap = false)
public abstract class EngineShopPriceMixin {
    @ModifyReturnValue(method = "price()I", at = @At("RETURN"), require = 1, allow = 1)
    private int sparkstrength$engineWreckDiscount(int price) {
        return EngineEconomy.price((ShopEntry) (Object) this, price);
    }
}
