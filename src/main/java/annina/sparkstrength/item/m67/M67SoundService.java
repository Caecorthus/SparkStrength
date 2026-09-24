package annina.sparkstrength.item.m67;

import annina.sparkstrength.network.m67.M67SoundPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Exact playback tokens and original listeners survive tracking changes until STOP.
 *  精确播放标识及原始听众保留至 STOP，不受追踪范围变化影响。 */
final class M67SoundService {
    private static final Map<ServerPlayerEntity, Playback> PLAYBACKS = new IdentityHashMap<>();
    private static long nextToken;

    private M67SoundService() {
    }

    static void start(ServerPlayerEntity actor, byte action, Hand hand) {
        stop(actor);
        long token = ++nextToken;
        Set<ServerPlayerEntity> recipients = new HashSet<>(PlayerLookup.tracking(actor));
        // The actor predicts locally; never send its own delayed gesture back. / 本人本地预测，不回传延迟手势。
        recipients.remove(actor);
        recipients.removeIf(listener -> !ServerPlayNetworking.canSend(listener, M67SoundPayload.ID));
        M67SoundPayload payload = new M67SoundPayload(actor.getUuid(), token, action, hand,
                actor.getInventory().selectedSlot);
        PLAYBACKS.put(actor, new Playback(actor.getServerWorld(), payload, recipients,
                actor.getServerWorld().getTime() + (action == M67SoundPayload.START_EQUIP
                        ? M67Rules.EQUIP_COOLDOWN_TICKS : M67Rules.CHARGE_TICKS)));
        recipients.forEach(listener -> ServerPlayNetworking.send(listener, payload));
    }

    static void stop(ServerPlayerEntity actor) {
        Playback playback = PLAYBACKS.remove(actor);
        if (playback == null) {
            return;
        }
        M67SoundPayload start = playback.start();
        M67SoundPayload stop = new M67SoundPayload(start.actorUuid(), start.playbackToken(),
                M67SoundPayload.STOP, start.hand(), start.selectedSlot());
        for (ServerPlayerEntity listener : playback.recipients()) {
            if (ServerPlayNetworking.canSend(listener, M67SoundPayload.ID)) {
                ServerPlayNetworking.send(listener, stop);
            }
        }
    }

    static void tick(ServerPlayerEntity actor) {
        Playback playback = PLAYBACKS.get(actor);
        if (playback != null && (playback.world() != actor.getWorld()
                || playback.world().getTime() >= playback.expiresAt())) {
            stop(actor);
        }
    }

    static Set<ServerPlayerEntity> actors() {
        return new HashSet<>(PLAYBACKS.keySet());
    }

    static boolean belongsTo(ServerPlayerEntity actor, ServerWorld world) {
        Playback playback = PLAYBACKS.get(actor);
        return playback != null && playback.world() == world;
    }

    private record Playback(ServerWorld world, M67SoundPayload start,
                            Set<ServerPlayerEntity> recipients, long expiresAt) {
    }
}
