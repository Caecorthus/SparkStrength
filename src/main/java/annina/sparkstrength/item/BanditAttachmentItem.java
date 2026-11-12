package annina.sparkstrength.item;

import annina.sparkstrength.role.bandit.BanditAttachment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A part for the Bandit's revolver. Fitted from the inventory: right-click it onto the revolver, or use the workbench
 * (right-click the revolver with an empty cursor). All logic lives in {@link BanditRevolverItem}.
 * 强盗改装左轮的配件。在背包中安装：拿起后右键左轮，或使用改装界面（空手右键左轮）。逻辑全部在 {@link BanditRevolverItem}。
 */
public final class BanditAttachmentItem extends Item {
    private final BanditAttachment attachment;

    public BanditAttachmentItem(Settings settings, BanditAttachment attachment) {
        super(settings);
        this.attachment = attachment;
    }

    public BanditAttachment attachment() {
        return attachment;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.bandit_part.slot." + attachment.slot().key())
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("item.sparkstrength.bandit_" + attachment.key() + ".description")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkstrength.bandit_part.hint").formatted(Formatting.DARK_GRAY));
    }
}
