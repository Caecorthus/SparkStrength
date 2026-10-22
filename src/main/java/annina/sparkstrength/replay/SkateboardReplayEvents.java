package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.util.Identifier;

/**
 * Replay line for the Vulture's skateboard.
 * 秃鹫滑板的回放文本。
 */
public final class SkateboardReplayEvents {
    public static final Identifier SKATEBOARD_RIDE_STARTED = SparkStrength.id("skateboard_ride_started");

    private SkateboardReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(SKATEBOARD_RIDE_STARTED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.skateboard_ride_started"
                ));
    }
}
