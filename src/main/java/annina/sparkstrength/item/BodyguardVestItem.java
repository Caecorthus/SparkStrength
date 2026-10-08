package annina.sparkstrength.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Bodyguard's bulletproof vest: chest armour with no defence value. Right-click equips it like vanilla armour, it
 * shows on the body for everyone, and it only stops a kill while worn (BodyguardVestService).
 * 保镖的防弹衣：没有护甲值的胸甲。像原版护甲一样右键穿上，所有人都能看到穿在身上，只有穿着时才能挡下击杀
 * （BodyguardVestService）。
 */
public final class BodyguardVestItem extends ArmorItem {
    public BodyguardVestItem(RegistryEntry<ArmorMaterial> material, Settings settings) {
        super(material, Type.CHESTPLATE, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.bodyguard_vest.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
