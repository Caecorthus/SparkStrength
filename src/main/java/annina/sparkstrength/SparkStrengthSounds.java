package annina.sparkstrength;

import dev.doctor4t.ratatouille.util.registrar.SoundEventRegistrar;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Registers SparkStrength sound events through Wathe's ambience registrar path.
 * 通过 Wathe 环境音使用的 registrar 链路注册 SparkStrength 声音事件。
 */
public final class SparkStrengthSounds {
    private static final SoundEventRegistrar REGISTRAR = new SoundEventRegistrar(SparkStrength.MOD_ID);

    public static final Identifier MUSIC_TAKEDISKRUSH_ID = SparkStrength.id("music.takediskrush");
    public static final SoundEvent MUSIC_TAKEDISKRUSH = REGISTRAR.create("music.takediskrush");
    public static final SoundEvent M67_EQUIP = REGISTRAR.create("item.m67.equip");
    public static final SoundEvent M67_PULL = REGISTRAR.create("item.m67.pull");
    public static final SoundEvent M67_THROW = REGISTRAR.create("item.m67.throw");
    public static final SoundEvent M67_LAND = REGISTRAR.create("item.m67.land");
    public static final SoundEvent M67_EXPLODE = REGISTRAR.create("item.m67.explode");
    /** Looping rotor noise; each drone kind sounds different. / 循环旋翼噪音，两种无人机音色不同。 */
    public static final SoundEvent GRENADE_DRONE_LOOP = REGISTRAR.create("entity.grenade_drone.loop");
    public static final SoundEvent BOMB_DRONE_LOOP = REGISTRAR.create("entity.bomb_drone.loop");
    public static final SoundEvent DRONE_PLACE = REGISTRAR.create("entity.drone.place");
    public static final SoundEvent DRONE_BREAK = REGISTRAR.create("entity.drone.break");
    public static final SoundEvent DRONE_RELEASE = REGISTRAR.create("entity.drone.release");
    public static final SoundEvent DRONE_BIND = REGISTRAR.create("item.drone.bind");
    public static final Identifier SHADOW_JESTER_ID = SparkStrength.id("ambient.shadow_jester");
    public static final SoundEvent SHADOW_JESTER = REGISTRAR.create("ambient.shadow_jester");

    private SparkStrengthSounds() {
    }

    public static void initialize() {
        REGISTRAR.registerEntries();
        if (!Registries.SOUND_EVENT.containsId(MUSIC_TAKEDISKRUSH_ID)) {
            SparkStrength.LOGGER.warn("SparkStrength sound event {} was not registered.", MUSIC_TAKEDISKRUSH_ID);
        }
        if (!Registries.SOUND_EVENT.containsId(SHADOW_JESTER_ID)) {
            SparkStrength.LOGGER.warn("SparkStrength sound event {} was not registered.", SHADOW_JESTER_ID);
        }
    }
}
