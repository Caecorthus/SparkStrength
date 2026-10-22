package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.util.Identifier;

/**
 * Replay lines for the Engineer's capture device.
 * 工程师捕捉装置的回放文本。
 */
public final class CaptureDeviceReplayEvents {
    public static final Identifier CAPTURE_DEVICE_PLACED = SparkStrength.id("capture_device_placed");
    public static final Identifier CAPTURE_DEVICE_TRIGGERED = SparkStrength.id("capture_device_triggered");
    public static final Identifier CAPTURE_DEVICE_RELEASED = SparkStrength.id("capture_device_released");
    public static final Identifier CAPTURE_DEVICE_EXPIRED = SparkStrength.id("capture_device_expired");

    private CaptureDeviceReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_PLACED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_placed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_TRIGGERED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_triggered"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_RELEASED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_released"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_EXPIRED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_expired"
                ));
    }
}
