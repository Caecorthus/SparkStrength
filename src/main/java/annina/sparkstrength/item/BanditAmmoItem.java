package annina.sparkstrength.item;

import annina.sparkstrength.role.bandit.BanditAmmo;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A round for the Bandit's revolver. Loaded from the inventory (right-click the stack onto the revolver, one round per
 * click), from the workbench, or with the ability key while the revolver is held.
 * 强盗改装左轮的子弹。可在背包中装填（拿起后右键左轮，每次一发）、在改装界面装填，或手持左轮时按技能键装填。
 */
public final class BanditAmmoItem extends Item {
    private final BanditAmmo ammo;

    public BanditAmmoItem(Settings settings, BanditAmmo ammo) {
        super(settings);
        this.ammo = ammo;
    }

    public BanditAmmo ammo() {
        return ammo;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength." + ammo.key() + "_round.description")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkstrength.bandit_ammo.hint").formatted(Formatting.DARK_GRAY));
    }
}
