package annina.sparkstrength.item;

import annina.sparkstrength.role.perfumer.AromaService;
import annina.sparkstrength.role.perfumer.PerfumerKitService;
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
 * Perfumer's Aroma Orb: instant throw that must hit a player directly, logic in {@link AromaService}.
 * 调香师的香薰：即时投掷，必须直接砸中玩家，逻辑在 {@link AromaService}。
 */
public final class AromaOrbItem extends Item {
    public AromaOrbItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!PerfumerKitService.canUse(user)) {
            return PerfumerKitService.deny(world, user, stack);
        }
        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }
        return user instanceof ServerPlayerEntity player && AromaService.throwFrom(player, stack)
                ? TypedActionResult.success(stack, false) : TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.aroma_orb.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
