package annina.sparkstrength.item;

import annina.sparkstrength.role.detective.DetectiveCaseService;
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
 * Detective case folder. Opening is server-driven: the server validates the holder, syncs the folder and then sends
 * the open packet, so the client never opens the screen on its own.
 * 侦探文件夹。打开由服务端驱动：服务端校验持有者、同步文件夹后再发送打开包，客户端不会自行打开界面。
 */
public final class CaseFolderItem extends Item {
    public CaseFolderItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            DetectiveCaseService.openFolder(player);
        }
        // CONSUME on both sides: no arm swing. 两端都返回 CONSUME：不挥手。
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.case_folder.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
