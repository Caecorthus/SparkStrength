package annina.sparkstrength.client.role.perfumer;

import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import org.jetbrains.annotations.Nullable;

/**
 * Single client-side decision point for the Perfumer debuffs on the LOCAL player. It only reads the owner-synced
 * {@link PerfumerScentComponent} (the server stays authoritative for who is hit and for how long) and applies only
 * while the local player is alive in a running round, so spectators and dead players are never affected.
 * 本地玩家调香师减益的唯一客户端判定入口。只读取仅同步给本人的 {@link PerfumerScentComponent}
 * （命中与时长由服务端决定），且仅在本地玩家处于进行中的对局并存活时生效，旁观者与死者不受影响。
 */
public final class PerfumerClientEffects {
    /** Degrees per look unit applied by vanilla {@code Entity#changeLookDirection}. / 原版视角换算系数（度/单位）。 */
    public static final double LOOK_DEGREES_PER_UNIT = 0.15D;
    /** Caps drift integration across lag spikes. / 卡顿时限制漂移积分的单帧时长。 */
    private static final double MAX_FRAME_SECONDS = 0.1D;

    private PerfumerClientEffects() {
    }

    /** Cooling Oil blocks every instinct path while active. / 风油精生效期间屏蔽所有本能路径。 */
    public static boolean blocksInstinct() {
        PerfumerScentComponent scent = activeScent();
        return scent != null && scent.hasCoolingOil();
    }

    /** Cheap identity check first: KeyBinding queries run for every key every tick. / 先做廉价的身份比较：每个按键每 tick 都会查询。 */
    public static boolean blocksInstinctKey(KeyBinding key) {
        return key == WatheClient.instinctKeybind && blocksInstinct();
    }

    /** World blur radius in framebuffer texels before resolution scaling; 0 when inactive. / 世界模糊半径（未按分辨率缩放），未生效为 0。 */
    public static float coolingOilBlurRadius(float tickDelta) {
        PerfumerScentComponent scent = activeScent();
        if (scent == null || !scent.hasCoolingOil()) {
            return 0.0F;
        }
        return PerfumerRules.coolingOilBlurRadius(
                coolingOilElapsed(scent, tickDelta), remaining(scent.coolingOilTicks(), tickDelta));
    }

    /** Snapshot of the HUD-layer state for this frame, or null when nothing is drawn. / 本帧覆盖层状态；无需绘制时为 null。 */
    public static @Nullable OverlayState overlayState(float tickDelta) {
        PerfumerScentComponent scent = activeScent();
        if (scent == null) {
            return null;
        }
        float coolingAlpha = 0.0F;
        float coolingElapsed = 0.0F;
        if (scent.hasCoolingOil()) {
            coolingElapsed = coolingOilElapsed(scent, tickDelta);
            coolingAlpha = PerfumerRules.coolingOilOverlayAlpha(
                    coolingElapsed, remaining(scent.coolingOilTicks(), tickDelta));
        }
        float aromaStrength = 0.0F;
        float aromaElapsed = 0.0F;
        if (scent.hasAroma()) {
            aromaElapsed = aromaElapsed(scent, tickDelta);
            aromaStrength = PerfumerRules.aromaStrength(aromaElapsed, remaining(scent.aromaTicks(), tickDelta));
        }
        if (coolingAlpha <= 0.0F && aromaStrength <= 0.0F) {
            return null;
        }
        return new OverlayState(coolingAlpha, coolingElapsed, aromaStrength, aromaElapsed);
    }

    /**
     * Aroma aim disruption for one {@code Mouse#updateMouse} frame, or null when inactive. The returned deltas are
     * in vanilla look units (degrees / 0.15): sensitivity scales the player's own input and the drift is integrated
     * over the real frame time, so the view wanders even without mouse input.
     * 一帧 {@code Mouse#updateMouse} 的香薰准心干扰；未生效时返回 null。返回值为原版视角单位（度 / 0.15）：
     * 灵敏度只缩放玩家自身输入，漂移按真实帧时长积分，因此不动鼠标视角也会晃动。
     */
    public static @Nullable AromaLook aromaLook(double cursorDeltaX, double cursorDeltaY, double frameSeconds) {
        PerfumerScentComponent scent = activeScent();
        if (scent == null || !scent.hasAroma()) {
            return null;
        }
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true);
        float elapsed = aromaElapsed(scent, tickDelta);
        float strength = PerfumerRules.aromaStrength(elapsed, remaining(scent.aromaTicks(), tickDelta));
        if (strength <= 0.0F) {
            return null;
        }
        long seed = scent.aromaSeed();
        double dt = Math.max(0.0D, Math.min(MAX_FRAME_SECONDS, frameSeconds));
        double sensitivity = PerfumerRules.aromaSensitivity(seed, elapsed, strength);
        double yawDrift = PerfumerRules.aromaYawDriftDegPerSec(seed, elapsed, strength) * dt / LOOK_DEGREES_PER_UNIT;
        double pitchDrift = PerfumerRules.aromaPitchDriftDegPerSec(seed, elapsed, strength) * dt / LOOK_DEGREES_PER_UNIT;
        return new AromaLook(cursorDeltaX * sensitivity + yawDrift, cursorDeltaY * sensitivity + pitchDrift);
    }

    private static @Nullable PerfumerScentComponent activeScent() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return null;
        }
        PerfumerScentComponent scent = PerfumerScentComponent.KEY.getNullable(player);
        if (scent == null || (!scent.hasCoolingOil() && !scent.hasAroma())) {
            return null;
        }
        // Checked last: it reads the world game component. / 最后检查：需要读取世界游戏组件。
        return GameFunctions.isPlayerPlayingAndAlive(player) ? scent : null;
    }

    private static float coolingOilElapsed(PerfumerScentComponent scent, float tickDelta) {
        // Uninterrupted-spell time, so a re-splash never restarts the fade-in or the blink pattern.
        // 使用连续效果时长，再次被溅到不会让渐入和眨眼节奏重新开始。
        return scent.coolingOilActiveTicks() + tickDelta;
    }

    private static float aromaElapsed(PerfumerScentComponent scent, float tickDelta) {
        return PerfumerRules.AROMA_DURATION_TICKS - scent.aromaTicks() + tickDelta;
    }

    private static float remaining(int ticks, float tickDelta) {
        return Math.max(0.0F, ticks - tickDelta);
    }

    /** Per-frame HUD inputs. / 每帧覆盖层输入。 */
    public record OverlayState(float coolingOilAlpha, float coolingOilElapsedTicks, float aromaStrength,
                               float aromaElapsedTicks) {
    }

    /** Adjusted look deltas passed on to {@code changeLookDirection}. / 传给 {@code changeLookDirection} 的调整后视角增量。 */
    public record AromaLook(double deltaX, double deltaY) {
    }
}
