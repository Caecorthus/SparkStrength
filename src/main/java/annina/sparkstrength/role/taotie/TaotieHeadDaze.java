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
        // The daze blocks starting an item, but a bow-class charge already running would still fire in onStoppedUsing
        // once the use key is let go (SparkWitch's M1903A4). Cancel the use without a release, as SparkWitch's Control
        // Expert stun does; the cleared using flag syncs, so the client stops too.
        // 眩晕阻止开始使用物品，但已在蓄力的弓类武器在松开使用键时仍会于 onStoppedUsing 中开火（SparkWitch 的 M1903A4）。
        // 因此像 SparkWitch 控场专家眩晕一样取消使用而不触发松手；清除后的使用标志会同步，客户端也随之停止。
        if (player.isUsingItem()) {
            player.clearActiveItem();
        }
    }

    public static void clear(PlayerEntity player) {
        TaotieHeadDazeComponent.KEY.get(player).clear();
    }
}
