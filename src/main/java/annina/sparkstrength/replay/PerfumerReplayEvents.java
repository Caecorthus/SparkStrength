package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.util.Identifier;

/**
 * Replay lines for the Perfumer's cooling oil, aroma orb and Zephyr Perfume.
 * 调香师风油精、香薰与晨风香水的回放文本。
 */
public final class PerfumerReplayEvents {
    public static final Identifier PERFUMER_COOLING_OIL_HIT = SparkStrength.id("perfumer_cooling_oil_hit");
    public static final Identifier PERFUMER_AROMA_HIT = SparkStrength.id("perfumer_aroma_hit");
    public static final Identifier PERFUMER_ZEPHYR_USED = SparkStrength.id("perfumer_zephyr_used");

    private PerfumerReplayEvents() {
    }

    static void registerThrowables() {
        ReplayRegistry.registerGlobalEventFormatter(PERFUMER_COOLING_OIL_HIT,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.perfumer_cooling_oil_hit"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PERFUMER_AROMA_HIT,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.perfumer_aroma_hit"
                ));
    }

    static void registerZephyr() {
        ReplayRegistry.registerGlobalEventFormatter(PERFUMER_ZEPHYR_USED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.perfumer_zephyr_used"
                ));
    }
}
