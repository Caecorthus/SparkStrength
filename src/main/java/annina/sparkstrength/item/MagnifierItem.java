package annina.sparkstrength.item;

import annina.sparkstrength.role.detective.DetectiveCaseService;
import annina.sparkstrength.role.detective.DetectiveRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
 * Detective magnifying glass: right-click a corpse to file the case, right-click a player to measure how far they
 * were from the body at the time of death. All rules live in {@link DetectiveCaseService}.
 * 侦探放大镜：右键尸体记录命案，右键玩家得知其案发时距尸体多远。所有规则都在 DetectiveCaseService。
 */
public final class MagnifierItem extends Item {
    public MagnifierItem(Settings settings) {
        super(settings);
    }

    /**
     * The client's CONSUME stops the fall-through to {@code use()} and the arm swing; it depends only on the local
     * player's own role. The server delegates and returns PASS: an accepted server result would make
     * PlayerEntity.interact emit GameEvent.ENTITY_INTERACT. The service never sets a client-side cooldown.
     * 客户端返回 CONSUME，不会继续触发 use() 也不挥手，判断只依赖本地玩家自身身份。服务端委托服务后返回 PASS：
     * 若服务端返回已接受结果，PlayerEntity.interact 会发出 ENTITY_INTERACT 游戏事件。冷却只由服务端设置。
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity == user || !(entity instanceof PlayerBodyEntity || entity instanceof PlayerEntity)) {
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

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.magnifier.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
