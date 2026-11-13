package annina.sparkstrength.util;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Small stack helpers shared by SparkStrength's role shops. Each shop keeps its own entries, ids, order, stock, role
 * checks and purchase callbacks; only the display stack and the hotbar top-up live here.
 * SparkStrength 各职业商店共用的小型物品堆辅助方法。各商店保留自己的条目、id、顺序、库存、身份判定与购买回调；
 * 这里只放展示物品堆与快捷栏补充逻辑。
 */
public final class ShopStacks {
    private ShopStacks() {
    }

    /**
     * Shop display: {@code stack} named {@code nameKey}, with one gray, non-italic lore line {@code nameKey +
     * ".description"}. Builder has no name/description API; display components must not enter the purchased stack, so
     * always pass a stack used for display only.
     * 商店展示物品堆：名称为 {@code nameKey}，并带一行灰色、非斜体的描述 {@code nameKey + ".description"}。Builder 无名称/描述
     * 接口；展示组件不应进入实际购买的物品堆，因此只能传入专用于展示的物品堆。
     */
    public static ItemStack display(ItemStack stack, String nameKey) {
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable(nameKey));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(nameKey + ".description")
                        .styled(style -> style.withColor(0x808080).withItalic(false)))));
        return stack;
    }

    /**
     * Server-side purchase: true charges the price, false charges nothing. Tops up an existing hotbar stack first,
     * because Wathe's default only fills an empty hotbar slot. No cap beyond the item's own max count.
     * 服务端购买：返回 true 扣费，false 不扣费。优先补充快捷栏中已有的堆叠，因为 Wathe 默认只放入空的快捷栏槽位。
     * 除物品自身的最大堆叠数外不设上限。
     */
    public static boolean giveStackable(PlayerEntity player, Item item) {
        return giveStackable(player, item, Integer.MAX_VALUE);
    }

    /**
     * Like {@link #giveStackable(PlayerEntity, Item)}, but a hotbar stack is only topped up below
     * {@code min(maxStack, its max count)}.
     * 同 {@link #giveStackable(PlayerEntity, Item)}，但只把快捷栏堆叠补到 {@code min(maxStack, 物品最大堆叠数)} 以下。
     */
    public static boolean giveStackable(PlayerEntity player, Item item, int maxStack) {
        ItemStack fresh = item.getDefaultStack();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack held = inventory.getStack(slot);
            if (ItemStack.areItemsAndComponentsEqual(held, fresh)
                    && held.getCount() < Math.min(maxStack, held.getMaxCount())) {
                held.increment(1);
                return true;
            }
        }
        return ShopEntry.insertStackInFreeSlot(player, fresh);
    }
}
