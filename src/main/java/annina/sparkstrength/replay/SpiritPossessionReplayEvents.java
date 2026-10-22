package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay line for the Spiritualist's Wraith possession.
 * 灵界行者附身冤魂的回放文本。
 */
public final class SpiritPossessionReplayEvents {
    /** Spiritualist Wraith possession: actor = the Spiritualist, target = the Wraith. / 灵界行者附身冤魂：actor 为灵界行者，target 为冤魂。 */
    public static final Identifier SPIRIT_POSSESSION_STARTED = SparkStrength.id("spirit_possession_started");

    private SpiritPossessionReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(SPIRIT_POSSESSION_STARTED, (event, match, world) -> {
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            return Text.translatable(
                    "replay.global.sparkstrength.spirit_possession_started",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });
    }
}
