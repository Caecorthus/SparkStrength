package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Replay lines for the Phantom's backpack invisibility.
 * 幽灵背包隐身的回放文本。
 */
public final class PhantomBackpackReplayEvents {
    public static final Identifier PHANTOM_BACKPACK_INVISIBILITY_STARTED = SparkStrength.id("phantom_backpack_invisibility_started");
    public static final Identifier PHANTOM_BACKPACK_INVISIBILITY_ENDED = SparkStrength.id("phantom_backpack_invisibility_ended");

    private PhantomBackpackReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(PHANTOM_BACKPACK_INVISIBILITY_STARTED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.phantom_backpack_invisibility_started",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });

        ReplayRegistry.registerGlobalEventFormatter(PHANTOM_BACKPACK_INVISIBILITY_ENDED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.phantom_backpack_invisibility_ended"
                ));
    }
}
