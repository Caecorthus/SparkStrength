package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay line for the Timekeeper's Dying Watch.
 * 计时员濒毁怀表的回放文本。
 */
public final class TimekeeperReplayEvents {
    public static final Identifier TIMEKEEPER_WATCH_USED = SparkStrength.id("timekeeper_watch_used");

    private TimekeeperReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(TIMEKEEPER_WATCH_USED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }
            String mode = data.getString("mode");
            String key = "replay.global.sparkstrength.timekeeper_watch_used."
                    + ("ability_refresh".equals(mode) ? "ability" : "item");
            return Text.translatable(key, ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache));
        });
    }
}
