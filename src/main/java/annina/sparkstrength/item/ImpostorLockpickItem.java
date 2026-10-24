package annina.sparkstrength.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The Impostor's pry tool (撬锁工具), given free at round start by {@code ImpostorLockpickService}. It only pries
 * junction boxes, exactly like Wathe's lockpick there ({@code JunctionBoxService#isPryTool}); doors don't know it, so
 * it opens and jams none.
 * 内鬼的撬锁工具，由 ImpostorLockpickService 开局免费发放。它只能撬接线箱，效果与 Wathe 开锁器撬箱完全相同；
 * 门不认识它，所以开不了也卡不了任何门。
 */
public final class ImpostorLockpickItem extends Item {
    public ImpostorLockpickItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.impostor_lockpick.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.impostor_lockpick.tooltip.line2")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
