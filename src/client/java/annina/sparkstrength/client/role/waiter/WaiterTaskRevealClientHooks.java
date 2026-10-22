package annina.sparkstrength.client.role.waiter;

import annina.sparkstrength.client.instinct.InstinctViewer;
import annina.sparkstrength.component.waiter.WaiterTaskRevealComponent;
import annina.sparkstrength.role.waiter.WaiterTaskRevealService;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.Noellesroles;

/** 服务员及验尸官服务员伪装的任务完成透视客户端高亮。 */
public final class WaiterTaskRevealClientHooks {
    private static boolean registered;

    private WaiterTaskRevealClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(WaiterTaskRevealClientHooks::highlight);
    }

    private static GetInstinctHighlight.HighlightResult highlight(Entity entity) {
        if (!(entity instanceof PlayerEntity target)) {
            return null;
        }
        InstinctViewer frame = InstinctViewer.current();
        if (frame == null || frame.player() == target) {
            return null;
        }

        // Viewer first (resolved once per frame; same predicate as the server's sync recipients), then the target's
        // reveal flag, then its alive check.
        // 先判观察者（每帧解析一次；与服务端同步对象使用同一判定），再判目标的透视标记，最后才做目标存活判定。
        if (!WaiterTaskRevealService.canSeeReveals(frame.playingAndAlive(),
                frame.game().isRole(frame.player(), Noellesroles.WAITER), frame.disguiseRole())
                || !WaiterTaskRevealComponent.KEY.get(target).isRevealed()
                || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            return null;
        }

        // always 表示无需按本能键，并且不检查视线，因此可以隔墙透视。
        return GetInstinctHighlight.HighlightResult.always(Noellesroles.WAITER.color(), 70);
    }
}
