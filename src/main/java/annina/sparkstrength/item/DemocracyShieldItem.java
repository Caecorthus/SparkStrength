package annina.sparkstrength.item;

import annina.sparkstrength.role.bodyguard.BodyguardRules;
import annina.sparkstrength.role.bodyguard.BodyguardShieldService;
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

/**
 * The Bodyguard's Democracy Shield: hold right-click to raise it for up to 10 s. Wathe kills skip vanilla damage, so
 * the block itself is decided by BodyguardProtectionService; this item only starts and ends the raise.
 * 保镖的民主盾牌：按住右键最多举 10 秒。Wathe 的击杀绕过原版伤害，所以格挡由 BodyguardProtectionService 判定；
 * 本物品只负责开始与结束举盾。
 */
public final class DemocracyShieldItem extends Item {
    public DemocracyShieldItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return BodyguardRules.MAX_RAISE_TICKS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // Vanilla already skips use() while the item cools down; the client only predicts, the server decides.
        // 物品冷却中时原版不会调用 use()；客户端只做预测，由服务端决定。
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.fail(stack);
        }
        boolean allowed = user instanceof ServerPlayerEntity player
                ? BodyguardShieldService.tryStartRaise(player)
                : BodyguardShieldService.canRaise(user);
        if (!allowed) {
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof ServerPlayerEntity player) {
            BodyguardShieldService.onLowered(player, getMaxUseTime(stack, user) - remainingUseTicks);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity player) {
            BodyguardShieldService.onLowered(player, BodyguardRules.MAX_RAISE_TICKS);
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 3; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.democracy_shield.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
