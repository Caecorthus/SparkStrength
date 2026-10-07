package annina.sparkstrength.item;

import annina.sparkstrength.role.pathogen.PathogenReviveService;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
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
 * The Pathogen's T-Virus (T病毒): right-click a corpse to revive that player as a converted Pathogen. All rules live in
 * {@link PathogenReviveService}; it is consumed only when the revive happened.
 * 病原体的 T病毒：右键尸体使其复活为转化病原体。规则全部位于 {@link PathogenReviveService}；只有复活成功时才消耗。
 */
public final class TVirusItem extends Item {
    public TVirusItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerBodyEntity body)) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverUser) || !PathogenReviveService.useTVirus(serverUser, body)) {
            return ActionResult.FAIL;
        }
        stack.decrementUnlessCreative(1, serverUser);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.t_virus.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.t_virus.tooltip.line2")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.t_virus.tooltip.line3")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
