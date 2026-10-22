package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay lines for the Reporter's connections and broadcasts.
 * 记者接线与广播的回放文本。
 */
public final class ReporterReplayEvents {
    public static final Identifier REPORTER_CONNECTION_STARTED = SparkStrength.id("reporter_connection_started");
    public static final Identifier REPORTER_CONNECTION_FAILED_ONE_DEAD = SparkStrength.id("reporter_connection_failed_one_dead");
    public static final Identifier REPORTER_CONNECTION_FAILED_BOTH_DEAD = SparkStrength.id("reporter_connection_failed_both_dead");
    public static final Identifier REPORTER_BROADCAST_STARTED = SparkStrength.id("reporter_broadcast_started");
    public static final Identifier REPORTER_BROADCAST_FAILED = SparkStrength.id("reporter_broadcast_failed");
    public static final Identifier REPORTER_CONNECTION_ENDED = SparkStrength.id("reporter_connection_ended");
    public static final Identifier REPORTER_BROADCAST_ENDED = SparkStrength.id("reporter_broadcast_ended");
    public static final Identifier REPORTER_CONNECTION_INTERRUPTED = SparkStrength.id("reporter_connection_interrupted");
    public static final Identifier REPORTER_BROADCAST_INTERRUPTED = SparkStrength.id("reporter_broadcast_interrupted");

    private ReporterReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_STARTED,
                (event, match, world) -> reporterConnectionWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_started",
                        false
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_FAILED_ONE_DEAD,
                (event, match, world) -> reporterConnectionWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_failed_one_dead",
                        true
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_FAILED_BOTH_DEAD,
                (event, match, world) -> reporterConnectionWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_failed_both_dead",
                        false
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_STARTED,
                (event, match, world) -> reporterBroadcastWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_started",
                        false
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_FAILED,
                (event, match, world) -> reporterBroadcastWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_failed",
                        true
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_ENDED,
                (event, match, world) -> reporterConnectionEndedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_ENDED,
                (event, match, world) -> reporterBroadcastEndedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_INTERRUPTED,
                (event, match, world) -> reporterConnectionInterruptedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_interrupted"
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_INTERRUPTED,
                (event, match, world) -> reporterBroadcastInterruptedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_interrupted"
                ));
    }

    private static Text reporterConnectionWithActorEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key,
            boolean includeDeadPlayer
    ) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        Text first = ReplayText.playerFromUuidOrName(data, match, "player_one", "player_one_name");
        Text second = ReplayText.playerFromUuidOrName(data, match, "player_two", "player_two_name");
        if (actorUuid == null || first == null || second == null) {
            return null;
        }

        Text actor = ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache);
        if (includeDeadPlayer) {
            Text dead = ReplayText.playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
            return dead == null ? null : Text.translatable(key, actor, first, second, dead);
        }
        return Text.translatable(key, actor, first, second);
    }

    private static Text reporterBroadcastWithActorEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key,
            boolean includeDeadPlayer
    ) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        Text target = ReplayText.playerFromUuidOrName(data, match, "target_player", "target_player_name");
        if (actorUuid == null || target == null) {
            return null;
        }

        Text actor = ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache);
        if (includeDeadPlayer) {
            Text dead = ReplayText.playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
            return dead == null ? null : Text.translatable(key, actor, target, dead);
        }
        return Text.translatable(key, actor, target);
    }

    private static Text reporterConnectionEndedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text first = ReplayText.playerFromUuidOrName(data, match, "player_one", "player_one_name");
        Text second = ReplayText.playerFromUuidOrName(data, match, "player_two", "player_two_name");
        if (first == null || second == null) {
            return null;
        }
        return Text.translatable(key, first, second);
    }

    private static Text reporterBroadcastEndedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text target = ReplayText.playerFromUuidOrName(data, match, "target_player", "target_player_name");
        return target == null ? null : Text.translatable(key, target);
    }

    private static Text reporterConnectionInterruptedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text first = ReplayText.playerFromUuidOrName(data, match, "player_one", "player_one_name");
        Text second = ReplayText.playerFromUuidOrName(data, match, "player_two", "player_two_name");
        Text dead = ReplayText.playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
        if (first == null || second == null || dead == null) {
            return null;
        }
        return Text.translatable(key, first, second, dead);
    }

    private static Text reporterBroadcastInterruptedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text dead = ReplayText.playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
        return dead == null ? null : Text.translatable(key, dead);
    }
}
