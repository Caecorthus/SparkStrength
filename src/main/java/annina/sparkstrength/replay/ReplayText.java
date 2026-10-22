package annina.sparkstrength.replay;

import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

/**
 * Text helpers shared by the replay formatters in this package.
 * 本包回放格式化器共用的文本工具。
 */
final class ReplayText {
    private ReplayText() {
    }

    static Text onePlayerEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        if (actorUuid == null) {
            return null;
        }
        return Text.translatable(key, ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache));
    }

    /**
     * A thrower who logged off before impact is recorded with no actor, so fall back to the one-argument key.
     * 投掷者在命中前离线时事件没有 actor，此时回退到单参数文本。
     */
    static Text throwableHitEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
        if (targetUuid == null) {
            return null;
        }
        if (actorUuid == null) {
            return Text.translatable(key + ".unknown", ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache));
        }
        return Text.translatable(
                key,
                ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
        );
    }

    static Text playerFromUuidOrName(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String uuidKey,
            String nameKey
    ) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        String fallbackName = data.getString(nameKey);
        if (data.containsUuid(uuidKey)) {
            return formatPlayerNameWithFallback(data.getUuid(uuidKey), fallbackName, playerInfoCache);
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return Text.literal(fallbackName);
        }
        return null;
    }

    static Text formatPlayerNameWithFallback(
            UUID uuid,
            String fallbackName,
            Map<UUID, ReplayGenerator.PlayerInfo> playerInfoCache
    ) {
        if (playerInfoCache.containsKey(uuid)) {
            return ReplayGenerator.formatPlayerName(uuid, playerInfoCache);
        }
        if (fallbackName != null && !fallbackName.isBlank()) {
            return Text.literal(fallbackName);
        }
        return ReplayGenerator.formatPlayerName(uuid, playerInfoCache);
    }
}
