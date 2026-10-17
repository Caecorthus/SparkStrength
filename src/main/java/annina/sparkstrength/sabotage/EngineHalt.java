package annina.sparkstrength.sabotage;

import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Side-agnostic view of the engine sabotage for code that runs on both sides (Wathe's common-ticking round timer).
 * The server reads its round state; the client reads the phase from the last sabotage status packet, so common mixins
 * never touch client classes.
 * 两端通用的动力破坏视图，供两端都会执行的代码使用（Wathe 两端都 tick 的回合倒计时）。服务端读取本局状态；客户端读取最近一次
 * 破坏状态包里的阶段，因此通用 mixin 不会碰到客户端类。
 */
public final class EngineHalt {
    private static volatile @Nullable EnginePhase clientPhase;

    private EngineHalt() {
    }

    /**
     * Server: the phase derived from the trigger tick, exact to the tick (the timer freezes at the deadline tick itself,
     * before the end-of-tick update runs). Client: the phase of the last status packet.
     * 服务端：由触发 tick 推导的阶段，精确到 tick（倒计时在截止那一 tick 就冻结，不等 tick 末尾的更新）。客户端：最近一次状态包里的阶段。
     */
    public static @Nullable EnginePhase phase(World world) {
        if (world == null) {
            return null;
        }
        if (world.isClient()) {
            return clientPhase;
        }
        return SabotageWorldComponent.KEY.get(world).enginePhase(world.getTime());
    }

    /**
     * Whether the round timer skips its natural countdown this tick: only while wrecked in an active round. Time added
     * or taken by kills and roles still applies, and reaching zero still ends the round.
     * 本 tick 回合倒计时是否跳过自然递减：仅在进行中的对局里处于损毁阶段时。击杀与职业加减的时间照常生效，减到零仍会结束对局。
     */
    public static boolean freezesRoundTimer(World world) {
        EnginePhase phase = phase(world);
        return phase != null && phase.freezesRoundTimer()
                && GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /** Client: the phase the server last reported. / 客户端：服务端最近报告的阶段。 */
    public static void setClientPhase(@Nullable EnginePhase phase) {
        clientPhase = phase;
    }

    public static @Nullable EnginePhase clientPhase() {
        return clientPhase;
    }
}
