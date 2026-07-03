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
    }
}
