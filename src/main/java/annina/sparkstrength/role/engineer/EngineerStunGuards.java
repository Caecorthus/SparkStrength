package annina.sparkstrength.role.engineer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

/**
 * Security guard: the interaction lock of the Engineer capture stun (and the Jester Moment freeze, which reuses its
 * component), copied from {@code TaotieHeadDazeGuards}. The stunned player's own client already reads every key as
 * released; these listeners refuse the vanilla packets a modified client could still send: item use, block use, entity
 * use and attacks. Server-authoritative; on the client FAIL just means no packet. The listeners sit in a dedicated
 * phase ordered before {@link Event#DEFAULT_PHASE} (and before the drone hit phase, see {@code DroneWeaponHits}), so
 * they decide before every existing listener; a player who is not stunned always gets PASS, so the relative order of
 * all other listeners is unchanged. Unlike the daze, bare-hand block use is refused too: a dazed player keeps walking
 * and opening doors, a stunned one cannot press any key.
 * 安全闸门：工程师捕捉定身（以及复用其组件的小丑时刻定身）的交互锁，照搬 TaotieHeadDazeGuards。被定身者的客户端已把所有按键读作
 * 未按下；这些监听器拒绝修改过的客户端仍可能发送的原版数据包：使用物品、使用方块、使用实体与攻击。服务端权威；在客户端 FAIL
 * 只表示不发送数据包。监听器位于排在 Event#DEFAULT_PHASE 之前（也在无人机命中阶段之前，见 DroneWeaponHits）的专用阶段，
 * 先于所有现有监听器做出决定；未被定身的玩家始终得到 PASS，其他监听器之间的相对顺序不变。与眩晕不同，空手使用方块也会被拒绝：
 * 被眩晕者仍能走动和开门，被定身者却按不了任何键。
 */
public final class EngineerStunGuards {
    public static final Identifier STUN_PHASE = SparkStrength.id("engineer_stun");
    private static boolean registered;

    private EngineerStunGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(STUN_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return isStunned(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(STUN_PHASE, (player, world, hand, hit) -> verdict(player));
        UseEntityCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(STUN_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(STUN_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.addPhaseOrdering(STUN_PHASE, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(STUN_PHASE, (player, world, hand, pos, direction) -> verdict(player));
    }

    private static boolean isStunned(PlayerEntity player) {
        return EngineerStunnedPlayerComponent.KEY.get(player).isStunned();
    }

    private static ActionResult verdict(PlayerEntity player) {
        return isStunned(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
