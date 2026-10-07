package annina.sparkstrength.role.taotie;

import annina.sparkstrength.SparkStrength;
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
 * Interaction lock of the daze, copied from SparkWitch's Control Expert stun guards. Server-authoritative; it also runs
 * on the client, where FAIL means no packet is sent. The listeners sit in a dedicated phase ordered before
 * {@link Event#DEFAULT_PHASE}, so they decide before every existing listener (including ones that kill inside the
 * callback); undazed players always get PASS, so the relative order of all other listeners is unchanged. Bare-hand
 * block use stays allowed so a dazed player can still open doors and press buttons.
 * 眩晕的交互锁，照搬 SparkWitch 控场专家眩晕的写法。服务端权威；客户端同样运行，FAIL 时不会发送数据包。监听器位于排在
 * {@link Event#DEFAULT_PHASE} 之前的专用阶段，先于所有现有监听器（包括在回调内直接击杀的）做出决定；未被眩晕的玩家
 * 始终得到 PASS，其他监听器之间的相对顺序不变。空手使用方块仍然放行，被眩晕者仍可开门、按按钮。
 */
public final class TaotieHeadDazeGuards {
    public static final Identifier DAZE_PHASE = SparkStrength.id("taotie_head_daze");
    private static boolean registered;

    private TaotieHeadDazeGuards() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseItemCallback.EVENT.addPhaseOrdering(DAZE_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(DAZE_PHASE, (player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            return TaotieHeadDaze.isDazed(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
        });
        UseBlockCallback.EVENT.addPhaseOrdering(DAZE_PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(DAZE_PHASE, (player, world, hand, hit) ->
                !player.getStackInHand(hand).isEmpty() ? verdict(player) : ActionResult.PASS);
        UseEntityCallback.EVENT.addPhaseOrdering(DAZE_PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(DAZE_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackEntityCallback.EVENT.addPhaseOrdering(DAZE_PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(DAZE_PHASE, (player, world, hand, entity, hit) -> verdict(player));
        AttackBlockCallback.EVENT.addPhaseOrdering(DAZE_PHASE, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(DAZE_PHASE, (player, world, hand, pos, direction) -> verdict(player));
    }

    private static ActionResult verdict(PlayerEntity player) {
        return TaotieHeadDaze.isDazed(player) ? ActionResult.FAIL : ActionResult.PASS;
    }
}
