package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.sabotage.EngineHalt;
import annina.sparkstrength.sabotage.EnginePhase;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.config.datapack.MapEnhancementsConfiguration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * How fast the train looks to move while the engine is wrecked (0) or on emergency power (0.5), eased over 8 s.
 * 动力损毁（0）或应急动力（0.5）期间列车看起来的速度，用 8 秒平滑过渡。
 *
 * <p>Wathe keeps its real speed, so nothing else changes. Its scenery (vanilla and Sodium) and wheels place themselves
 * at {@code (getTime() + tickDelta) × speed}; once engaged, {@code getTime()} returns
 * {@code lerp(tickDelta, virtualNow, virtualNext) − tickDelta}, so all three see one continuous time that advances
 * {@code factor} per tick. Scaling only the whole-tick time would leave the raw tickDelta sweeping the scenery back and
 * forth every tick once stopped. Engaged lazily from the time on screen, and kept until the train stops for real
 * (round over, lobby) or the world changes, so the picture never jumps.
 * Wathe 保留真实速度，其他一切不变。它的风景（原版与 Sodium）和车轮按 (getTime() + tickDelta) × speed 定位；接管之后
 * getTime() 返回 lerp(tickDelta, virtualNow, virtualNext) − tickDelta，三者看到的是同一个每 tick 前进 factor 的连续时间。
 * 只缩放整 tick 时间的话，停下后原始 tickDelta 仍会让风景每 tick 来回扫。接管时从屏幕上的当前时间衔接，一直保持到列车
 * 真正停下（对局结束、大厅）或换世界，所以画面不会跳。</p>
 */
public final class EngineTrainMotion {
    /** 8 s to slow down or speed up. / 减速或加速用时 8 秒。 */
    public static final int RAMP_TICKS = 160;
    private static final Identifier TRAIN_INSIDE = Identifier.of("wathe", "ambient.train.inside");
    private static final Identifier TRAIN_OUTSIDE = Identifier.of("wathe", "ambient.train.outside");

    private static @Nullable ClientWorld world;
    private static boolean engaged;
    private static double virtualNow;
    private static double virtualNext;
    private static double factor = 1.0;
    private static double previousFactor = 1.0;
    private static double rampFrom = 1.0;
    private static double target = 1.0;
    private static int rampTick = RAMP_TICKS;
    /** No status packet yet on this connection: the phase is unknown, so nothing slows down. / 本连接还没收到状态包：阶段未知，不做任何减速。 */
    private static boolean statusSeen;
    /** Jump straight to the phase's speed instead of easing: the state on arrival, not a live change. / 直接跳到阶段对应的速度而不做过渡：这是进入时的现状，不是实时变化。 */
    private static boolean snap = true;
    private static int lastSpeed;

    private EngineTrainMotion() {
    }

    public static void tick(MinecraftClient client) {
        if (client.world != world) {
            reset();
            snap = true;
            world = client.world;
        }
        if (world == null || !WatheClient.isTrainMoving()) {
            // Wathe restarts its own train time from 0 when the train moves again; whatever phase holds then is the
            // state on arrival. / 列车再次开动时 Wathe 会把自己的列车时间从 0 开始；那时的阶段就是现状。
            reset();
            snap = true;
            return;
        }
        if (client.isPaused()) {
            // Wathe's own train time does not tick behind the pause menu either. / 暂停菜单后面，Wathe 自己的列车时间同样不走。
            return;
        }
        double wanted = statusSeen ? targetFactor(EngineHalt.clientPhase()) : 1.0;
        if (snap && statusSeen) {
            factor = wanted;
            previousFactor = wanted;
            target = wanted;
            rampTick = RAMP_TICKS;
            snap = false;
        } else if (wanted != target) {
            rampFrom = factor;
            target = wanted;
            rampTick = 0;
        }
        rescaleForSpeed();
        previousFactor = factor;
        if (rampTick < RAMP_TICKS) {
            rampTick++;
            factor = MathHelper.lerp(smooth(rampTick / (double) RAMP_TICKS), rampFrom, target);
        }
        if (engaged) {
            virtualNow = virtualNext;
            virtualNext = virtualNow + factor;
        }
    }

    /**
     * Scenery and wheels sit at time × speed, so when the real speed changes the virtual time is rescaled to continue
     * from where they are. Also run at render time, since a speed sync can be drawn before the next tick.
     * 风景与车轮的位置是 time × speed，所以真实速度改变时按比例缩放虚拟时间，从当前位置接着走。渲染时也会执行，因为速度同步
     * 可能在下一个 tick 之前就被画出来。
     */
    private static void rescaleForSpeed() {
        if (WatheClient.getTrainComponent() == null) {
            return;
        }
        int speed = WatheClient.getTrainComponent().getSpeed();
        if (engaged && speed != lastSpeed && lastSpeed > 0 && speed > 0) {
            double scale = lastSpeed / (double) speed;
            virtualNow *= scale;
            virtualNext *= scale;
        }
        lastSpeed = speed;
    }

    /** A fresh connection: wait for the first status, then take it as the state on arrival. / 新连接：等第一个状态包，把它当作现状。 */
    public static void onJoin() {
        reset();
        statusSeen = false;
        snap = true;
    }

    /** A sabotage status arrived; the first one on a connection is the state on arrival. / 收到破坏状态；连接上的第一个状态就是现状。 */
    public static void onStatus() {
        if (!statusSeen) {
            statusSeen = true;
            snap = true;
        }
    }

    public static void reset() {
        engaged = false;
        lastSpeed = 0;
        factor = 1.0;
        previousFactor = 1.0;
        rampFrom = 1.0;
        target = 1.0;
        rampTick = RAMP_TICKS;
    }

    static double targetFactor(@Nullable EnginePhase phase) {
        return phase == null ? 1.0 : phase.motionFactor();
    }

    private static double smooth(double t) {
        double x = MathHelper.clamp(t, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    /** Current motion factor, 1 = normal speed. / 当前的运动系数，1 为正常速度。 */
    public static double factor() {
        return factor;
    }

    /** Between the last two ticks, for per-frame effects. / 最近两个 tick 之间的插值，用于逐帧效果。 */
    public static double factor(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousFactor, factor);
    }

    private static boolean slowed() {
        return factor < 1.0 || target < 1.0;
    }

    /**
     * {@code TrainWorldComponent.getTime()} on the client, for callers that add {@code getTickDelta(true)} themselves
     * (vanilla and Sodium scenery).
     * 客户端上的 TrainWorldComponent.getTime()，供自己加上 getTickDelta(true) 的调用方使用（原版与 Sodium 风景）。
     */
    public static float renderTime(float raw) {
        return renderTime(raw, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
    }

    /**
     * The same for a caller that adds {@code tickDelta} itself (the wheels get the block-entity tickDelta, which differs
     * under {@code /tick freeze}).
     * 同上，供自己加上 tickDelta 的调用方使用（车轮拿到的是方块实体的 tickDelta，在 /tick freeze 下与前者不同）。
     */
    public static float renderTime(float raw, float tickDelta) {
        if (!engaged) {
            if (!slowed()) {
                return raw;
            }
            engaged = true;
            virtualNow = raw;
            virtualNext = raw + factor;
            lastSpeed = WatheClient.getTrainComponent() == null ? 0 : WatheClient.getTrainComponent().getSpeed();
        } else {
            rescaleForSpeed();
        }
        return (float) (MathHelper.lerp(tickDelta, virtualNow, virtualNext) - tickDelta);
    }

    public static boolean engaged() {
        return engaged;
    }

    /** Wathe's camera shake, scaled with the motion; off once stopped. / Wathe 的镜头晃动随运动缩放，停稳后关闭。 */
    public static MapEnhancementsConfiguration.CameraShakeConfig shake(MapEnhancementsConfiguration.CameraShakeConfig config) {
        if (config == null || !config.enabled()) {
            return config;
        }
        float f = (float) factor(MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
        if (f >= 0.999F) {
            return config;
        }
        if (f <= 0.02F) {
            return new MapEnhancementsConfiguration.CameraShakeConfig(false, config.amplitudeIndoor(),
                    config.amplitudeOutdoor(), config.strengthIndoor(), config.strengthOutdoor());
        }
        return new MapEnhancementsConfiguration.CameraShakeConfig(true, config.amplitudeIndoor() * f,
                config.amplitudeOutdoor() * f, config.strengthIndoor(), config.strengthOutdoor());
    }

    /** The roof wind sway only applies while the train goes at least half speed. / 车顶风吹的晃动只在列车至少半速时生效。 */
    public static boolean windAllowed() {
        return factor >= 0.5;
    }

    public static boolean isTrainAmbience(Identifier sound) {
        return TRAIN_INSIDE.equals(sound) || TRAIN_OUTSIDE.equals(sound);
    }

    /** Train loop volume multiplier: half volume at half speed. / 列车循环音的音量倍率：半速时一半音量。 */
    public static float ambienceGain() {
        return (float) MathHelper.clamp(factor, 0.0, 1.0);
    }

    /** Train loop pitch: lower as it slows. / 列车循环音的音调：越慢越低。 */
    public static float ambiencePitch() {
        return (float) (0.75 + 0.25 * MathHelper.clamp(factor, 0.0, 1.0));
    }
}
