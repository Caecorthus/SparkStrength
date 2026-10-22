package annina.sparkstrength.sabotage;

import annina.sparkstrength.role.AbilityLocks;
import annina.sparkstrength.role.AbilityLocks.Lock;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/**
 * Locks that keep a pry, a hack or a breaker repair from starting or finishing: Engineer stun, SparkWitch Control
 * Expert stun, Taotie daze, being swallowed. Checked by the server every tick, since a stun can land between two
 * heartbeats and the client's cancel only arrives a round trip later.
 * 阻止撬开、入侵或电闸维修开始或完成的锁定：工程师眩晕、SparkWitch 控场专家眩晕、饕餮眩晕、被吞下。
 * 由服务端每 tick 检查，因为眩晕可能落在两次心跳之间，而客户端的取消要一个往返后才到。
 */
public final class SabotageActionLocks {
    private static final Set<Lock> LOCKS = AbilityLocks.of(Lock.ENGINEER_STUN, Lock.CONTROL_EXPERT_STUN,
            Lock.TAOTIE_DAZE, Lock.SWALLOWED);

    private SabotageActionLocks() {
    }

    public static boolean isLocked(ServerPlayerEntity player) {
        return AbilityLocks.isLocked(player, LOCKS);
    }
}
