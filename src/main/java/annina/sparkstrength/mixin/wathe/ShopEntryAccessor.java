package annina.sparkstrength.mixin.wathe;

import dev.doctor4t.wathe.util.ShopEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Wathe's private {@code ShopEntry.price} field: the listed gold price, before the broken-engine discount that
 * {@code price()} may apply. Tells whether the discount changed a price, for the shop's green price tag.
 * Wathe 私有的 ShopEntry.price 字段：上架的金币价，不含 price() 可能叠加的动力损毁折扣。用来判断折扣是否改变了价格，供商店的
 * 绿色价签使用。
 */
@Mixin(value = ShopEntry.class, remap = false)
public interface ShopEntryAccessor {
    @Accessor(value = "price", remap = false)
    int sparkstrength$listedPrice();
}
