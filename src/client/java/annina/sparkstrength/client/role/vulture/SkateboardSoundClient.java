package annina.sparkstrength.client.role.vulture;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Looping wheel noise (vanilla minecart rolling) that follows each moving skateboard rider, audible to every nearby
 * client. Purely local audio driven by the synced ride component and position deltas (no packets); one instance per
 * entity id at most.
 * 跟随每个移动中滑板骑手的循环轮子声（原版矿车滚动声），附近所有客户端都能听到。纯本地音频，由同步的滑行组件与位移差驱动
 * （无网络包）；每个实体 id 最多一个实例。
 */
public final class SkateboardSoundClient {
    /** Start a loop only within this distance of the listener; it stops itself beyond STOP_RANGE. / 仅在此距离内开始播放，超出 STOP_RANGE 自行停止。 */
    private static final double START_RANGE = 20.0;
    private static final double STOP_RANGE = 28.0;
    /** Horizontal blocks per tick that count as rolling. / 视为滚动的水平每刻位移。 */
    private static final double MIN_ROLL_SPEED = 0.02;
    /** A loop the sound engine refused (no free channel) is retried after this many ticks. / 被音频引擎拒绝的循环在此刻数后重试。 */
    private static final int RETRY_TICKS = 20;

    private static final Map<Integer, Playback> active = new HashMap<>();
    private static ClientWorld world;
    private static long ticks;

    private record Playback(RollingSound sound, long startedAt) {
    }

    private SkateboardSoundClient() {
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SkateboardSoundClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SkateboardSoundClient::reset));
    }

    private static void tick(MinecraftClient client) {
        if (client.world != world) {
            reset();
            world = client.world;
        }
        if (world == null || client.player == null) {
            return;
        }
        ticks++;
        SoundManager sounds = client.getSoundManager();
        Iterator<Playback> it = active.values().iterator();
        while (it.hasNext()) {
            Playback playback = it.next();
            boolean refused = ticks - playback.startedAt() > RETRY_TICKS && !sounds.isPlaying(playback.sound());
            if (playback.sound().isDone() || refused) {
                sounds.stop(playback.sound());
                it.remove();
            }
        }
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (active.containsKey(player.getId()) || horizontalSpeed(player) < MIN_ROLL_SPEED
                    || player.squaredDistanceTo(listener) > START_RANGE * START_RANGE
                    || !SkateboardClient.isRiding(player)) {
                continue;
            }
            RollingSound sound = new RollingSound(player);
            active.put(player.getId(), new Playback(sound, ticks));
            sounds.play(sound);
        }
    }

    private static void reset() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Playback playback : active.values()) {
            client.getSoundManager().stop(playback.sound());
        }
        active.clear();
        world = null;
        ticks = 0;
    }

    private static double horizontalSpeed(AbstractClientPlayerEntity player) {
        return MathHelper.hypot(player.getX() - player.prevX, player.getZ() - player.prevZ);
    }

    /**
     * Follows one rider: volume and pitch rise with horizontal speed (silent while standing on the board), fades out
     * when the ride ends or the rider leaves range, and cuts quickly when the entity is removed.
     * 跟随一名骑手：音量与音调随水平速度升高（站在板上不动时静音）；滑行结束或离开范围时淡出，实体被移除时快速切断。
     */
    private static final class RollingSound extends MovingSoundInstance {
        private static final float MAX_VOLUME = 0.35F;
        /** Horizontal blocks per tick at which the loop reaches full volume and pitch. / 达到最大音量与音调的水平每刻位移。 */
        private static final float FULL_SPEED = 0.45F;
        private static final float BASE_PITCH = 1.5F;
        private static final float PITCH_RANGE = 0.2F;
        private static final float FADE = 0.06F;
        private static final float REMOVED_FADE = 0.2F;

        private final AbstractClientPlayerEntity player;

        private RollingSound(AbstractClientPlayerEntity player) {
            super(SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, SoundInstance.createRandom());
            this.player = player;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.0F;
            this.pitch = BASE_PITCH;
            follow();
        }

        @Override
        public boolean canPlay() {
            return !player.isSilent();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            // Starts silent and fades in. / 从静音开始淡入。
            return true;
        }

        @Override
        public void tick() {
            if (player.isRemoved()) {
                volume = Math.max(0.0F, volume - REMOVED_FADE);
                if (volume <= 0.0F) {
                    setDone();
                }
                return;
            }
            follow();
            boolean rolling = SkateboardClient.isRiding(player)
                    && player.squaredDistanceTo(MinecraftClient.getInstance().gameRenderer.getCamera().getPos())
                    <= STOP_RANGE * STOP_RANGE;
            float effort = MathHelper.clamp((float) horizontalSpeed(player) / FULL_SPEED, 0.0F, 1.0F);
            float targetVolume = rolling ? MAX_VOLUME * effort : 0.0F;
            volume += MathHelper.clamp(targetVolume - volume, -FADE, FADE);
            pitch += (BASE_PITCH + PITCH_RANGE * effort - pitch) * 0.3F;
            if (!rolling && volume <= 0.001F) {
                setDone();
            }
        }

        private void follow() {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        }
    }
}
