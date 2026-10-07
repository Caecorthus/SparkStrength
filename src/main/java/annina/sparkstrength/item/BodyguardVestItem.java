package annina.sparkstrength.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Shop icon for the Bodyguard's vest. Buying the vest puts it on at once (BodyguardGearComponent), so this stack is
 * only ever a shop display and never lands in an inventory.
 * 保镖防弹衣的商店图标。购买后直接穿上（BodyguardGearComponent），所以该物品只作为商店展示，不会进入背包。
 */
public final class BodyguardVestItem extends Item {
    public BodyguardVestItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.bodyguard_vest.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
