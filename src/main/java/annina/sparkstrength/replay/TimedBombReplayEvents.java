package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay lines for the Bomber's tray and bed bombs.
 * 炸弹客托盘与床铺炸弹的回放文本。
 */
public final class TimedBombReplayEvents {
    public static final Identifier TIMED_BOMB_TRAY_EMBEDDED = SparkStrength.id("timed_bomb_tray_embedded");
    public static final Identifier TIMED_BOMB_BED_EMBEDDED = SparkStrength.id("timed_bomb_bed_embedded");
    public static final Identifier TIMED_BOMB_TRAY_TRIGGERED = SparkStrength.id("timed_bomb_tray_triggered");
    public static final Identifier TIMED_BOMB_BED_TRIGGERED = SparkStrength.id("timed_bomb_bed_triggered");

    private TimedBombReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_TRAY_EMBEDDED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_tray_embedded"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_BED_EMBEDDED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_bed_embedded"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_TRAY_TRIGGERED,
                (event, match, world) -> actorAndBomberEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_tray_triggered"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_BED_TRIGGERED,
                (event, match, world) -> actorAndBomberEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_bed_triggered"
                ));
    }

    private static Text actorAndBomberEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        UUID bomberUuid = data.containsUuid("bomber") ? data.getUuid("bomber") : null;
        if (actorUuid == null || bomberUuid == null) {
            return null;
        }
        return Text.translatable(
                key,
                ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                ReplayGenerator.formatPlayerName(bomberUuid, playerInfoCache)
        );
    }
}
