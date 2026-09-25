package annina.sparkstrength;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public final class SparkStrengthItemGroups {
    // Stable creative tab id; other mods may target it via ItemGroupEvents.
    // 稳定的创造物品栏标签页 ID，其他模组可通过 ItemGroupEvents 引用。
    public static final RegistryKey<ItemGroup> ITEMS_GROUP =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, SparkStrength.id("items"));
    private static boolean registered;

    private SparkStrengthItemGroups() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        Registry.register(
                Registries.ITEM_GROUP,
                ITEMS_GROUP,
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.sparkstrength.items"))
                        .icon(() -> new ItemStack(SparkStrengthItems.m67()))
                        .entries((displayContext, entries) -> {
                            // Lists every item in our namespace in registration order, so new items join automatically.
                            // 按注册顺序列出本模组命名空间下的全部物品，新增物品无需再手动加入。
                            for (Item item : Registries.ITEM) {
                                if (SparkStrength.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace())) {
                                    entries.add(item);
                                }
                            }
                        })
                        .build()
        );
        registered = true;
    }
}
