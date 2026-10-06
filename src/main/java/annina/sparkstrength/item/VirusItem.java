package annina.sparkstrength.item;

import annina.sparkstrength.role.pathogen.VirusService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.List;

/**
 * The Pathogen's Virus (病毒): right-click a player within 3 blocks to make them a carrier. All rules live in
 * {@link VirusService}; one Virus is consumed only when it took effect.
 * 病原体的病毒：右键 3 格内的玩家使其成为带毒者。规则全部位于 {@link VirusService}；只有生效时才消耗一支。
 */
public final class VirusItem extends Item {
    public VirusItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity)) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverUser) || !(entity instanceof ServerPlayerEntity target)) {
            return ActionResult.PASS;
        }
        if (!VirusService.useVirus(serverUser, target)) {
            return ActionResult.FAIL;
        }
        stack.decrementUnlessCreative(1, serverUser);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.virus.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.virus.tooltip.line2")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
