package annina.sparkstrength.item;

import annina.sparkstrength.role.undercover.UndercoverRoles;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Undercover's CQCM ballistic mask: a helmet with no defence value, drawn on the face by the vanilla armour layer.
 * Right-click puts it on; the first time it goes on, stage 2 begins (UndercoverService). Only the real Undercover can
 * wear it; it can be taken off again (empty hand, Shift + right-click) without leaving stage 2.
 * 卧底的 CQCM 防弹面具：没有护甲值的头盔，由原版盔甲层画在脸上。右键戴上；第一次戴上即进入第二阶段（UndercoverService）。
 * 只有真卧底能戴；可以再摘下（空手 Shift + 右键），摘下也不会退出第二阶段。
 */
public final class CqcmMaskItem extends ArmorItem {
    public CqcmMaskItem(RegistryEntry<ArmorMaterial> material, Settings settings) {
        super(material, Type.HELMET, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!UndercoverRoles.isUndercover(user)) {
            if (!world.isClient) {
                user.sendMessage(Text.translatable("message.sparkstrength.undercover.mask_not_yours"), true);
            }
            return TypedActionResult.fail(user.getStackInHand(hand));
        }
        return super.use(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 3; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.cqcm_mask.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
