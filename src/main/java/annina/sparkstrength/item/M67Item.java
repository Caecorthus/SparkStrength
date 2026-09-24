package annina.sparkstrength.item;

import annina.sparkstrength.item.m67.M67UseService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

public final class M67Item extends Item {
    public M67Item(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            if (user.getItemCooldownManager().isCoolingDown(this)) {
                return TypedActionResult.fail(stack);
            }
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
        return user instanceof ServerPlayerEntity player && M67UseService.begin(player, hand, stack)
                ? TypedActionResult.consume(stack) : TypedActionResult.fail(stack);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        // Charge readiness is not item completion: a full hold never auto-throws. / 蓄满不等于使用完成，持续按住不会自动投掷。
        return Integer.MAX_VALUE;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player) {
            M67UseService.tick(player);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player) {
            M67UseService.release(player, stack);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 3; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.m67.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
