package annina.sparkstrength.item;

import annina.sparkstrength.tablet.TabletAccess;
import annina.sparkstrength.tablet.TabletLinkRules;
import annina.sparkstrength.tablet.TabletLinkService;
import annina.sparkstrength.tablet.TabletStateService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Opens the tablet network UI; all privileged actions are validated server-side.
 * 打开平板网络界面；所有关键操作都由服务端重新校验。
 */
public final class TabletItem extends Item {
    // Client-thread only: the local player's previous link gesture. / 仅客户端线程使用：本地玩家上一次互认操作。
    private static @Nullable UUID lastClientGestureTarget;
    private static long lastClientGestureTick;

    public TabletItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            TabletStateService.openTablet(serverPlayer);
        }
        // CONSUME on both sides (no swing): a non-eligible Shift+right-click on a player falls through here, so a swing
        // would let bystanders tell killers (silent link gesture) from everyone else.
        // 两端都返回 CONSUME（不挥手）：不合格者对玩家的 Shift+右键 会落到这里，若挥手，旁观者即可区分杀手（无声互认）与其他人。
        return TypedActionResult.consume(stack);
    }

    /**
     * Shift+right-click on a player starts or answers a KILLER-channel identity link.
     * Shift+右键玩家发起或回应杀手频道的身份互认。
     *
     * <p>The client's CONSUME means no fall-through to {@link #use} and no arm swing; the server returns PASS so no
     * ENTITY_INTERACT vibration is emitted. The client decision depends only on the actor's own identity and
     * entity-synced target visibility, and it always shows the same local "link signal sent" line, so it cannot probe
     * membership; the server re-validates everything, and server-only guards (reach, etc.) that drop the interaction
     * leave the actor with that same line.
     * 客户端返回 CONSUME 表示不会继续触发 use() 且不挥手；服务端返回 PASS，因此不会发出 ENTITY_INTERACT 振动。
     * 客户端判断只依赖发起者自身身份与实体同步的目标可见性，并总是在本地显示同一句“已发出互认信号”，因此无法探测成员身份；
     * 服务端重新校验一切，服务端独有的拦截（距离等）也不会改变这句反馈。</p>
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity target) || target == user || !user.isSneaking()) {
            return ActionResult.PASS;
        }
        // PASS falls through to use(): an unlinkable target or ineligible actor opens the tablet like clicking air. The
        // hotbar check is the actor's own state and mirrors the server gate, so an offhand-only tablet never shows "sent".
        // PASS 会继续触发 use()：不可互认的目标或不合格的发起者会像对空气右键一样打开平板。快捷栏检查只看发起者自身状态，
        // 与服务端闸门一致，仅副手持有平板时不会显示“已发出”。
        if (!TabletLinkService.isLinkableTarget(target)
                || !TabletLinkService.isEligibleActor(user)
                || !TabletAccess.hasTabletInHotbar(user)) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient()) {
            long now = user.getWorld().getTime();
            // Held use re-fires every 4 ticks; repeats must not replace the "linked" confirmation.
            // 按住使用键每 4 tick 重复触发；重复触发不能覆盖“已互认”的确认提示。
            boolean heldRepeat = TabletLinkRules.isHeldRepeat(
                    lastClientGestureTarget, lastClientGestureTick, target.getUuid(), now);
            lastClientGestureTarget = target.getUuid();
            lastClientGestureTick = now;
            if (!heldRepeat) {
                user.sendMessage(Text.translatable("message.sparkstrength.tablet.link.sent"), true);
            }
            return ActionResult.CONSUME;
        }
        if (user instanceof ServerPlayerEntity actor && target instanceof ServerPlayerEntity partner) {
            TabletLinkService.handleLinkGesture(actor, partner);
        }
        // Server PASS: the client already returned CONSUME, so no use() packet follows; an accepted server result would
        // make PlayerEntity.interact emit GameEvent.ENTITY_INTERACT (a vibration sculk hears even while sneaking) only
        // for killers.
        // 服务端返回 PASS：客户端已返回 CONSUME，不会再发送 use() 数据包；若服务端返回已接受结果，PlayerEntity.interact
        // 会仅对杀手发出 ENTITY_INTERACT 游戏事件（潜行也无法屏蔽的振动，幽匿感测体可感知）。
        return ActionResult.PASS;
    }
}
