package annina.sparkstrength.sabotage;

import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * The engine sabotage: units damaged, then wrecked (the train halts and the round timer stops running), then emergency
 * power. Server thread only.
 * 动力破坏：装置受损，随后损毁（列车停驶、回合倒计时暂停），再到应急动力。仅限服务端线程。
 */
public final class EngineSabotageService {
    private EngineSabotageService() {
    }

    /**
     * Move the engine sabotage to {@code phase} ({@code null} = over). The status packet goes out before the round
     * timer is synced on the same connection, so a client applies the new phase first and is then corrected to the
     * authoritative time.
     * 把动力破坏切到 phase（null 表示结束）。状态包在同一连接上先于回合倒计时同步发出，客户端先应用新阶段，再用权威时间校正。
     */
    public static void setPhase(ServerWorld world, @Nullable EnginePhase phase) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        EnginePhase previous = sabotage.enginePhase();
        if (previous == phase) {
            return;
        }
        sabotage.setEnginePhase(phase, world.getTime());
        // Re-syncs the round timer itself when the frozen state changes. / 冻结状态改变时，它会自己重新同步回合倒计时。
        SabotageService.syncStatusNow(world);
    }
}
