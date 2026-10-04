package annina.sparkstrength.item;

import annina.sparkstrength.role.vulture.VultureSkateboardService;
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
 * The Vulture's skateboard: right-click to ride it for a short burst of speed.
 * 秃鹫的滑板：右键踩上滑板，短时间大幅加速。
 */
public final class SkateboardItem extends Item {
    public SkateboardItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            // Vanilla already skips use() while the item cools down; the server decides the rest (role, round state).
            // 物品冷却中时原版不会调用 use()；其余条件（身份、对局状态）由服务器判定。
            return TypedActionResult.success(stack, true);
        }
        if (user instanceof ServerPlayerEntity player && VultureSkateboardService.tryStartRide(player)) {
            return TypedActionResult.success(stack, false);
        }
        return TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.skateboard.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
