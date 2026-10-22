package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay line for the Taotie's head.
 * 饕餮头颅的回放文本。
 */
public final class TaotieHeadReplayEvents {
    /** Taotie head hit: actor = the shooter (recorded even when offline), target = the victim. / 饕餮头颅命中：actor 为发射者（离线也记录），target 为受害者。 */
    public static final Identifier TAOTIE_HEAD_HIT = SparkStrength.id("taotie_head_hit");

    private TaotieHeadReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(TAOTIE_HEAD_HIT, (event, match, world) -> {
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            return Text.translatable(
                    "replay.global.sparkstrength.taotie_head_hit",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });
    }
}
