package annina.sparkstrength.item;

import annina.sparkstrength.role.toxicologist.ToxicologistBlueVitriolService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;

import java.util.List;

/**
 * Blue Vitriol. Plate/bed use is handled by {@link ToxicologistBlueVitriolService}'s UseBlockCallback; inventory use
 * (vitriol on the cursor, right-click a poisoned food/drink or filled capsule) goes through {@link #onStackClicked}.
 * 蓝矾。对餐盘/床使用由 ToxicologistBlueVitriolService 的 UseBlockCallback 处理；背包内使用（光标拿起蓝矾右键有毒食物/饮品
 * 或已装填胶囊）走 onStackClicked。
 */
public final class BlueVitriolItem extends Item {
    public BlueVitriolItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean onStackClicked(ItemStack stack, Slot slot, ClickType clickType, PlayerEntity player) {
        return ToxicologistBlueVitriolService.onStackClicked(stack, slot, clickType, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 3; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.blue_vitriol.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
