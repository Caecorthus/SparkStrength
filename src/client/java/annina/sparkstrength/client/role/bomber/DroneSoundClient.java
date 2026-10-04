package annina.sparkstrength.client.role.bomber;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.bomber.drone.DroneRules;
import annina.sparkstrength.role.bomber.drone.DroneState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Looping rotor noise that follows each drone while its rotors spin, audible to every nearby client. Purely local
 * audio driven by the tracked drone state (no packets); one instance per entity id at most. Range comes from
 * {@code attenuation_distance} in sounds.json (linear falloff to 20 blocks).
 * 旋翼转动时跟随无人机的循环噪音，附近所有客户端都能听到。纯本地音频，由追踪的无人机状态驱动（无网络包）；每个实体 id 最多一个实例。
 * 可闻范围由 sounds.json 的 attenuation_distance 决定（线性衰减至 20 格）。
 */
public final class DroneSoundClient {
    /** Start a loop only within this distance of the listener; it stops itself beyond STOP_RANGE. / 仅在此距离内开始播放，超出 STOP_RANGE 自行停止。 */
    private static final double START_RANGE = 24.0;
    private static final double STOP_RANGE = 32.0;
    /** A loop the sound engine refused (no free channel) is retried after this many ticks. / 被音频引擎拒绝的循环在此刻数后重试。 */
    private static final int RETRY_TICKS = 20;

    private static final Map<Integer, Playback> active = new HashMap<>();
    private static ClientWorld world;
    private static long ticks;

    private record Playback(DroneLoopSound sound, long startedAt) {
    }

    private DroneSoundClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DroneSoundClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(DroneSoundClient::reset));
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
            boolean refused = ticks - playback.startedAt() > 2 && !sounds.isPlaying(playback.sound());
            if (playback.sound().isDone() || (refused && ticks - playback.startedAt() > RETRY_TICKS)) {
                it.remove();
            }
        }
        // The listener is the camera, which is the drone itself while piloting. / 听者是相机，驾驶时即无人机本身。
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof DroneEntity drone) || drone.isRemoved() || !drone.state().rotorsOn()
                    || active.containsKey(drone.getId())
                    || drone.squaredDistanceTo(listener) > START_RANGE * START_RANGE) {
                continue;
            }
            DroneLoopSound sound = new DroneLoopSound(drone);
            active.put(drone.getId(), new Playback(sound, ticks));
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

    /**
     * Follows one drone: fades in when the rotors spin up, pitches up slightly with speed, and fades out with a falling
     * pitch (spin-down) when they stop or the drone leaves range; quick cut when the entity is removed.
     * 跟随一架无人机：旋翼启动时淡入，随速度略微升调；旋翼停转或离开范围时降调淡出（模拟减速）；实体被移除时快速切断。
     */
    private static final class DroneLoopSound extends MovingSoundInstance {
        private static final float FADE_IN = 0.08F;
        private static final float FADE_OUT = 0.05F;
        private static final float REMOVED_FADE = 0.25F;
        /** The pilot hears its own drone quieter, it would otherwise sit at the listener. / 驾驶者听自己的无人机更轻，否则声源就在耳边。 */
        private static final float PILOT_VOLUME = 0.55F;

        private final DroneEntity drone;
        private final float baseVolume;
        private final float basePitch;

        private DroneLoopSound(DroneEntity drone) {
            super(drone.kind() == DroneKind.BOMB ? SparkStrengthSounds.BOMB_DRONE_LOOP
                    : SparkStrengthSounds.GRENADE_DRONE_LOOP, SoundCategory.PLAYERS, SoundInstance.createRandom());
            this.drone = drone;
            this.baseVolume = drone.kind() == DroneKind.BOMB ? 0.9F : 0.75F;
            // Small per-drone detune so two drones never phase into one tone. / 每架略微失谐，两架无人机不会合成单一音调。
            this.basePitch = 0.97F + (Math.floorMod(drone.getId() * 31, 7)) * 0.01F;
            this.repeat = true;
            this.repeatDelay = 0;
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;
            this.volume = 0.0F;
            this.pitch = basePitch;
            follow();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            // Starts silent and fades in. / 从静音开始淡入。
            return true;
        }

        @Override
        public void tick() {
            if (drone.isRemoved()) {
                volume = Math.max(0.0F, volume - REMOVED_FADE);
                if (volume <= 0.0F) {
                    setDone();
                }
                return;
            }
            follow();
            DroneState state = drone.state();
            boolean on = state.rotorsOn();
            Vec3d listener = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            boolean inRange = drone.squaredDistanceTo(listener) <= STOP_RANGE * STOP_RANGE;
            double dx = drone.getX() - drone.prevX;
            double dy = drone.getY() - drone.prevY;
            double dz = drone.getZ() - drone.prevZ;
            float speed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float effort = MathHelper.clamp(speed / (float) DroneRules.horizontalSpeed(drone.kind()), 0.0F, 1.0F);

            float targetVolume = on && inRange ? baseVolume * (0.82F + 0.18F * effort) : 0.0F;
            if (drone.isLocallyPiloted()) {
                targetVolume *= PILOT_VOLUME;
            }
            volume += MathHelper.clamp(targetVolume - volume, -FADE_OUT, FADE_IN);
            float targetPitch = on
                    ? basePitch * (1.0F + 0.1F * effort + (state == DroneState.FLYING ? 0.03F : 0.0F))
                    : basePitch * 0.7F;
            pitch += (targetPitch - pitch) * 0.15F;
            if (targetVolume <= 0.0F && volume <= 0.001F) {
                setDone();
            }
        }

        private void follow() {
            x = drone.getX();
            y = drone.getY() + drone.getHeight() * 0.5;
            z = drone.getZ();
        }
    }
}
