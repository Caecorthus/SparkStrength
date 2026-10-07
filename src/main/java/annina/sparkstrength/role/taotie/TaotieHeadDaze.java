package annina.sparkstrength.role.taotie;

import annina.sparkstrength.component.taotie.TaotieHeadDazeComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Facade over the daze lock. {@link #isDazed} is side-neutral: on the server it is authoritative, on the client it reads
 * the owner-only synced component (so it is only meaningful for the local player).
 * 眩晕锁的门面。{@link #isDazed} 两端通用：服务端为权威结果；客户端读取只同步给本人的组件（因此只对本地玩家有意义）。
 */
public final class TaotieHeadDaze {
    private TaotieHeadDaze() {
    }

    public static boolean isDazed(PlayerEntity player) {
        return player != null && TaotieHeadDazeComponent.KEY.get(player).getDazeTicks() > 0;
    }

    public static void apply(ServerPlayerEntity player, int ticks) {
        TaotieHeadDazeComponent.KEY.get(player).apply(ticks);
    }

    public static void clear(PlayerEntity player) {
        TaotieHeadDazeComponent.KEY.get(player).clear();
    }
}
