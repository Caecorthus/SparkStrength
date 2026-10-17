package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineShopDisplay;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Green price tag for a discounted shop entry. Wathe draws the tag with the one {@code drawTooltip} call of
 * {@code renderWidget}; only its Text argument changes. SparkWitch redirects the {@code Text.literal} that builds the
 * label (mana and stamp labels), so that call is left alone; the tag's width was measured from the same string, and a
 * colour does not change it.
 * 打折商品的绿色价签。Wathe 用 renderWidget 里唯一一次 drawTooltip 调用画价签；这里只改它的 Text 参数。SparkWitch 会
 * redirect 构建标签的 Text.literal（魔力与邮票标签），所以不碰那次调用；价签宽度是按同一个字符串量的，颜色不改变宽度。
 */
@Mixin(value = LimitedInventoryScreen.StoreItemWidget.class, remap = false)
public abstract class EngineShopPriceTintMixin {
    @Shadow
    @Final
    public ShopEntry entry;

    @ModifyArg(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;II)V",
            remap = true), index = 1, require = 1, allow = 1, remap = true)
    private Text sparkstrength$greenDiscountedPrice(Text label) {
        return EngineShopDisplay.tintPrice(entry, label);
    }
}
