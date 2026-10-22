package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay lines for the Morphling's reagent and marks.
 * 变形者变形试剂与标记的回放文本。
 */
public final class MorphlingReplayEvents {
    public static final Identifier MORPH_REAGENT_SAMPLED = SparkStrength.id("morph_reagent_sampled");
    public static final Identifier MORPH_REAGENT_MARKED = SparkStrength.id("morph_reagent_marked");
    public static final Identifier MORPH_MARK_TRIGGERED = SparkStrength.id("morph_mark_triggered");
    public static final Identifier MORPH_MARK_ENDED = SparkStrength.id("morph_mark_ended");

    private MorphlingReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(MORPH_REAGENT_SAMPLED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID sampleUuid = data.containsUuid("sample") ? data.getUuid("sample") : null;
            if (actorUuid == null || sampleUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.morph_reagent_sampled",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayText.formatPlayerNameWithFallback(sampleUuid, data.getString("sample_name"), playerInfoCache)
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(MORPH_REAGENT_MARKED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID sampleUuid = data.containsUuid("sample") ? data.getUuid("sample") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || sampleUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.morph_reagent_marked",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayText.formatPlayerNameWithFallback(sampleUuid, data.getString("sample_name"), playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(MORPH_MARK_TRIGGERED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID sampleUuid = data.containsUuid("sample") ? data.getUuid("sample") : null;
            if (actorUuid == null || sampleUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.morph_mark_triggered",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayText.formatPlayerNameWithFallback(sampleUuid, data.getString("sample_name"), playerInfoCache)
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(MORPH_MARK_ENDED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.morph_mark_ended"
                ));
    }
}
