package annina.sparkstrength.item;

import annina.sparkstrength.role.partyanimal.PartyBalloonService;
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
 * The Party Animal's Party Balloon (派对气球): right-click a player within 3 blocks to slip them a balloon without their
 * knowing. All rules live in {@link PartyBalloonService}; one balloon is consumed only when it was given.
 * 派对狂的派对气球：右键 3 格内的玩家，神不知鬼不觉地把气球塞给对方。规则全部位于 {@link PartyBalloonService}；只有塞出去时
 * 才消耗一个。
 */
public final class PartyBalloonItem extends Item {
    public PartyBalloonItem(Settings settings) {
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
        if (!PartyBalloonService.giveBalloon(serverUser, target)) {
            return ActionResult.FAIL;
        }
        stack.decrementUnlessCreative(1, serverUser);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.party_balloon.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.party_balloon.tooltip.line2")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
