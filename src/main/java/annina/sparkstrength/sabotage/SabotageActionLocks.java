package annina.sparkstrength.sabotage;

import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.role.taotie.TaotieHeadDaze;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

/**
 * Locks that keep a pry, a hack or a breaker repair from starting or finishing: Engineer stun, SparkWitch Control
 * Expert stun, Taotie daze, being swallowed. Checked by the server every tick, since a stun can land between two
 * heartbeats and the client's cancel only arrives a round trip later.
 * 阻止撬开、入侵或电闸维修开始或完成的锁定：工程师眩晕、SparkWitch 控场专家眩晕、饕餮眩晕、被吞下。
 * 由服务端每 tick 检查，因为眩晕可能落在两次心跳之间，而客户端的取消要一个往返后才到。
 */
final class SabotageActionLocks {
    private SabotageActionLocks() {
    }

    static boolean isLocked(ServerPlayerEntity player) {
        return EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                || SparkWitchCompat.isControlExpertStunned(player)
                || TaotieHeadDaze.isDazed(player)
                || SwallowedPlayerComponent.isPlayerSwallowed(player);
    }
}
