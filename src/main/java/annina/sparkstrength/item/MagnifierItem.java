package annina.sparkstrength.item;

import annina.sparkstrength.role.detective.DetectiveCaseService;
import annina.sparkstrength.role.detective.DetectiveRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Detective magnifying glass: right-click a corpse to file the case; right-click anything else to open the roster and
 * remotely ask anyone how far they were from the body at the time of death. All rules live in
 * {@link DetectiveCaseService}.
 * 侦探放大镜：右键尸体记录命案；右键其他地方打开名单，远程询问任何人案发时距尸体多远。所有规则都在 DetectiveCaseService。
 */
public final class MagnifierItem extends Item {
    public MagnifierItem(Settings settings) {
        super(settings);
    }

    /**
     * Only a corpse is used here. The client's CONSUME stops the fall-through to {@code use()} and the arm swing; it
     * depends only on the local player's own role and the target's synced state. The server delegates and returns
     * PASS: an accepted server result would make PlayerEntity.interact emit GameEvent.ENTITY_INTERACT. A living player
     * returns PASS on both sides, so the client falls through to {@code use()} and opens the roster.
     * 这里只处理尸体。客户端返回 CONSUME，不会继续触发 use() 也不挥手，判断只依赖本地玩家自身身份与目标的同步状态。
     * 服务端委托服务后返回 PASS：若服务端返回已接受结果，PlayerEntity.interact 会发出 ENTITY_INTERACT 游戏事件。
     * 活人在两端都返回 PASS，客户端随后落到 use() 打开名单。
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity == user || !DetectiveCaseService.filesAsCorpse(entity)) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient()) {
            return DetectiveRules.isDetective(GameWorldComponent.KEY.get(user.getWorld()).getRole(user))
                    ? ActionResult.CONSUME
                    : ActionResult.PASS;
        }
        if (user instanceof ServerPlayerEntity detective) {
            DetectiveCaseService.useMagnifier(detective, stack, entity, hand);
        }
        return ActionResult.PASS;
    }

    /**
     * Opening is server-driven like the case folder: the server validates the holder, syncs the folder and then sends
     * the open packet. CONSUME on both sides: no arm swing.
     * 与文件夹一样由服务端驱动打开：服务端校验持有者、同步文件夹后再发送打开包。两端都返回 CONSUME：不挥手。
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            DetectiveCaseService.openInterrogation(player);
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.magnifier.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
