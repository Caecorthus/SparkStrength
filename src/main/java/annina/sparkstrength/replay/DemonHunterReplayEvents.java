package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay lines for the Demon Hunter's sniff.
 * 猎魔人嗅探的回放文本。
 */
public final class DemonHunterReplayEvents {
    public static final Identifier DEMON_HUNTER_SNIFF_FOUND = SparkStrength.id("demon_hunter_sniff_found");
    public static final Identifier DEMON_HUNTER_SNIFF_NONE = SparkStrength.id("demon_hunter_sniff_none");
    public static final Identifier DEMON_HUNTER_SNIFF_REVEALED = SparkStrength.id("demon_hunter_sniff_revealed");

    private DemonHunterReplayEvents() {
    }

    static void register() {
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
                (event, match, world) -> ReplayText.onePlayerEvent(
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
}
