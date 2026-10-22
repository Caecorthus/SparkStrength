package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay lines for the Professor's serums.
 * 教授试剂的回放文本。
 */
public final class ProfessorReplayEvents {
    public static final Identifier PROFESSOR_SERUM_FED = SparkStrength.id("professor_serum_fed");
    public static final Identifier PROFESSOR_INVISIBILITY_ENDED = SparkStrength.id("professor_invisibility_ended");
    public static final Identifier PROFESSOR_DOORPASSING_ENDED = SparkStrength.id("professor_doorpassing_ended");
    public static final Identifier PROFESSOR_SEDATIVE_ENDED = SparkStrength.id("professor_sedative_ended");
    public static final Identifier PROFESSOR_TRUTH_REVEALED = SparkStrength.id("professor_truth_revealed");

    private ProfessorReplayEvents() {
    }

    static void register() {
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
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_invisibility_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_DOORPASSING_ENDED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_doorpassing_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_SEDATIVE_ENDED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_sedative_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_TRUTH_REVEALED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_truth_revealed"
                ));
    }
}
