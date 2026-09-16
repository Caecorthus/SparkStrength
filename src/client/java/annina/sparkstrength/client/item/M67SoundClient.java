package annina.sparkstrength.client.item;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.network.m67.M67SoundPayload;
import annina.sparkstrength.item.m67.M67Rules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Scoped gesture audio, never a gameplay authority. 手势声音独立管理，不参与玩法判定。 */
public final class M67SoundClient {
    private static final Map<UUID, Playback> remote = new HashMap<>();
    private static ClientWorld world;
    private static ClientPlayerEntity player;
    private static SoundInstance local;
    private static byte localAction;
    private static long localDeadline;
    private static long ticks;

    private record Playback(long token, SoundInstance sound, long deadline) {}

    private M67SoundClient() {}

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(M67SoundPayload.ID,
                (payload, context) -> context.client().execute(() -> handle(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void handle(M67SoundPayload payload) {
        if (!ensureContext() || payload.actorUuid().equals(player.getUuid())) {
            // Local transitions are predicted; delayed server echoes must never revive them.
            // 本地手势使用预测；延迟的服务端回声不得恢复已取消的声音。
            return;
        }
        byte action = payload.action();
        if (action != M67SoundPayload.STOP && action != M67SoundPayload.START_EQUIP
                && action != M67SoundPayload.START_PULL) {
            return;
        }
        Playback previous = remote.get(payload.actorUuid());
        if (previous != null && (payload.playbackToken() < previous.token()
                || (payload.playbackToken() == previous.token() && action != M67SoundPayload.STOP))) {
            return;
        }
        if (previous != null) {
            stop(previous.sound());
        }
        PlayerEntity actor = world.getPlayerByUuid(payload.actorUuid());
        SoundInstance sound = action == M67SoundPayload.STOP || actor == null || !actor.isAlive()
                || actor.isRemoved() ? null : create(action, actor);
        // Keep high-water tombstones even after expiration; a stopped START cannot replay.
        // 到期后仍保留令牌上界，防止已停止的 START 重播。
        remote.put(payload.actorUuid(), new Playback(payload.playbackToken(), sound, ticks + duration(action)));
        if (sound != null) {
            MinecraftClient.getInstance().getSoundManager().play(sound);
        }
    }

    public static void playLocal(byte action) {
        stopLocal();
        if (!ensureContext() || !player.isAlive() || player.isRemoved()
                || (action != M67SoundPayload.START_EQUIP && action != M67SoundPayload.START_PULL)
                || (action == M67SoundPayload.START_EQUIP && !holdingM67())
                || (action == M67SoundPayload.START_PULL && (!player.isUsingItem()
                || !player.getActiveItem().isOf(SparkStrengthItems.m67())
                || player.getStackInHand(player.getActiveHand()) != player.getActiveItem()))) {
            return;
        }
        local = create(action, player);
        localAction = action;
        localDeadline = ticks + duration(action);
        MinecraftClient.getInstance().getSoundManager().play(local);
    }

    public static boolean hasLocalEquip() {
        return local != null && localAction == M67SoundPayload.START_EQUIP;
    }

    public static void stopLocalPull() {
        if (local != null && localAction == M67SoundPayload.START_PULL) {
            stopLocal();
        }
    }

    public static void stopLocal() {
        stop(local);
        local = null;
    }

    public static void tick() {
        if (!ensureContext()) {
            return;
        }
        ticks++;
        if (local != null && (ticks >= localDeadline || !player.isAlive() || player.isRemoved()
                || !holdingM67() || (localAction == M67SoundPayload.START_PULL && !M67Client.isCharging()))) {
            stopLocal();
        }
        remote.replaceAll((uuid, playback) -> {
            PlayerEntity actor = world.getPlayerByUuid(uuid);
            if (playback.sound() != null && (ticks >= playback.deadline() || actor == null
                    || actor.isRemoved() || !actor.isAlive())) {
                stop(playback.sound());
                return new Playback(playback.token(), null, playback.deadline());
            }
            return playback;
        });
    }

    public static void reset() {
        stopLocal();
        remote.values().forEach(playback -> stop(playback.sound()));
        remote.clear();
        world = null;
        player = null;
        ticks = 0;
    }

    private static boolean ensureContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (world != client.world || player != client.player) {
            reset();
            world = client.world;
            player = client.player;
        }
        return world != null && player != null && player.getWorld() == world;
    }

    private static boolean holdingM67() {
        return player.getStackInHand(Hand.MAIN_HAND).isOf(SparkStrengthItems.m67())
                || player.getStackInHand(Hand.OFF_HAND).isOf(SparkStrengthItems.m67());
    }

    private static int duration(byte action) {
        return action == M67SoundPayload.START_EQUIP ? M67Rules.EQUIP_COOLDOWN_TICKS : M67Rules.CHARGE_TICKS;
    }

    private static SoundInstance create(byte action, PlayerEntity actor) {
        return new EntityTrackingSoundInstance(action == M67SoundPayload.START_EQUIP
                ? SparkStrengthSounds.M67_EQUIP : SparkStrengthSounds.M67_PULL,
                SoundCategory.PLAYERS, 1.0F, 1.0F, actor, actor.getRandom().nextLong());
    }

    private static void stop(SoundInstance sound) {
        if (sound != null) {
            MinecraftClient.getInstance().getSoundManager().stop(sound);
        }
    }
}
