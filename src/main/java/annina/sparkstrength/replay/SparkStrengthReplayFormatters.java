package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * SparkStrength 自己的回放格式化器。
 */
public final class SparkStrengthReplayFormatters {
    public static final Identifier NOISEMAKER_GLOW_STARTED = SparkStrength.id("noisemaker_glow_started");
    public static final Identifier NOISEMAKER_GLOW_ENDED = SparkStrength.id("noisemaker_glow_ended");
    public static final Identifier PROFESSOR_SERUM_FED = SparkStrength.id("professor_serum_fed");
    public static final Identifier PROFESSOR_INVISIBILITY_ENDED = SparkStrength.id("professor_invisibility_ended");
    public static final Identifier PROFESSOR_DOORPASSING_ENDED = SparkStrength.id("professor_doorpassing_ended");
    public static final Identifier PROFESSOR_SEDATIVE_ENDED = SparkStrength.id("professor_sedative_ended");
    public static final Identifier PROFESSOR_TRUTH_REVEALED = SparkStrength.id("professor_truth_revealed");
    public static final Identifier CAPTURE_DEVICE_PLACED = SparkStrength.id("capture_device_placed");
    public static final Identifier CAPTURE_DEVICE_TRIGGERED = SparkStrength.id("capture_device_triggered");
    public static final Identifier CAPTURE_DEVICE_RELEASED = SparkStrength.id("capture_device_released");
    public static final Identifier CAPTURE_DEVICE_EXPIRED = SparkStrength.id("capture_device_expired");
    public static final Identifier DEMON_HUNTER_SNIFF_FOUND = SparkStrength.id("demon_hunter_sniff_found");
    public static final Identifier DEMON_HUNTER_SNIFF_NONE = SparkStrength.id("demon_hunter_sniff_none");
    public static final Identifier DEMON_HUNTER_SNIFF_REVEALED = SparkStrength.id("demon_hunter_sniff_revealed");

    private SparkStrengthReplayFormatters() {
    }

    public static void register() {
        ReplayRegistry.registerGlobalEventFormatter(NOISEMAKER_GLOW_STARTED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.noisemaker_glow_started",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });

        ReplayRegistry.registerGlobalEventFormatter(NOISEMAKER_GLOW_ENDED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.noisemaker_glow_ended",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache)
            );
        });

        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_SERUM_FED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.professor_serum_fed",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatItemName(data, world),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });

        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_INVISIBILITY_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_invisibility_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_DOORPASSING_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_doorpassing_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_SEDATIVE_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_sedative_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_TRUTH_REVEALED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_truth_revealed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_PLACED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_placed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_TRIGGERED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_triggered"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_RELEASED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_released"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_EXPIRED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_expired"
                ));
        ReplayRegistry.registerGlobalEventFormatter(DEMON_HUNTER_SNIFF_FOUND, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.demon_hunter_sniff_found",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    data.getInt("count")
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(DEMON_HUNTER_SNIFF_NONE,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.demon_hunter_sniff_none"
                ));
        ReplayRegistry.registerGlobalEventFormatter(DEMON_HUNTER_SNIFF_REVEALED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.demon_hunter_sniff_revealed",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });
    }

    private static Text onePlayerEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        if (actorUuid == null) {
            return null;
        }
        return Text.translatable(key, ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache));
    }
}
