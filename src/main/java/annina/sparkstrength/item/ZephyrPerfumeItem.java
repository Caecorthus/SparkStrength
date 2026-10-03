package annina.sparkstrength.item;

import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.role.perfumer.PerfumerKitService;
import annina.sparkstrength.role.perfumer.ZephyrService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Perfumer's Zephyr Perfume: sprayed on oneself, logic in {@link ZephyrService}.
 * 调香师的晨风香水：喷在自己身上，逻辑在 {@link ZephyrService}。
 */
public final class ZephyrPerfumeItem extends Item {
    public ZephyrPerfumeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!PerfumerKitService.canUse(user)) {
            return PerfumerKitService.deny(world, user, stack);
        }
        if (world.isClient()) {
            // Prediction only: the Zephyr flag is synced to its owner. / 仅用于预测：晨风标记会同步给本人。
            return PerfumerScentComponent.KEY.get(user).isZephyrActive()
                    ? TypedActionResult.fail(stack) : TypedActionResult.success(stack, true);
        }
        return user instanceof ServerPlayerEntity player && ZephyrService.spray(player, stack)
                ? TypedActionResult.success(stack, false) : TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.zephyr_perfume.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
