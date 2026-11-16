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
    /** Taotie head hitting a player. / 饕餮头颅砸中玩家。 */
    public static final SoundEvent TAOTIE_HEAD_BONK = REGISTRAR.create("entity.taotie_head.bonk");
    public static final Identifier SHADOW_JESTER_ID = SparkStrength.id("ambient.shadow_jester");
    public static final SoundEvent SHADOW_JESTER = REGISTRAR.create("ambient.shadow_jester");
    /**
     * Vulture Super Curse (~30 s, streamed) and its Childish helium take. The ids double as StopSound targets when the
     * Vulture dies or the round ends, so they must stay in step with the events below.
     * 秃鹫超级骂（约 30 秒，流式）及其幼稚氦气版。死亡或回合结束时这些 ID 也用作停止音效的目标，必须与下方事件一致。
     */
    public static final Identifier VULTURE_SUPER_CURSE_ID = SparkStrength.id("vulture.super_curse");
    public static final SoundEvent VULTURE_SUPER_CURSE = REGISTRAR.create("vulture.super_curse");
    public static final Identifier VULTURE_SUPER_CURSE_HELIUM_ID = SparkStrength.id("vulture.super_curse_helium");
    public static final SoundEvent VULTURE_SUPER_CURSE_HELIUM = REGISTRAR.create("vulture.super_curse_helium");
    /**
     * Vulture death scream (10 s). The helium event has two clips; the server plays it with one seed so every client
     * picks the same one.
     * 秃鹫死亡惨叫（10 秒）。氦气版含两段音频；服务端用同一个种子播放，所有客户端选中同一段。
     */
    public static final SoundEvent VULTURE_DEATH_SCREAM = REGISTRAR.create("vulture.death_scream");
    public static final SoundEvent VULTURE_DEATH_SCREAM_HELIUM = REGISTRAR.create("vulture.death_scream_helium");

    /** Sabotage system: junction boxes, circuit breakers and the tablet ready chime. / 破坏系统：接线箱、电闸与平板就绪提示音。 */
    public static final SoundEvent JUNCTION_BOX_PRY = REGISTRAR.create("block.junction_box.pry");
    public static final SoundEvent JUNCTION_BOX_BREAK = REGISTRAR.create("block.junction_box.break");
    public static final SoundEvent JUNCTION_BOX_SPARK = REGISTRAR.create("block.junction_box.spark");
    public static final SoundEvent JUNCTION_BOX_HACK = REGISTRAR.create("block.junction_box.hack");
    public static final SoundEvent JUNCTION_BOX_HACKED = REGISTRAR.create("block.junction_box.hacked");
    public static final SoundEvent JUNCTION_BOX_REPAIR = REGISTRAR.create("block.junction_box.repair");
    public static final SoundEvent CIRCUIT_BREAKER_TRIP = REGISTRAR.create("block.circuit_breaker.trip");
    public static final SoundEvent CIRCUIT_BREAKER_SWITCH = REGISTRAR.create("block.circuit_breaker.switch");
    public static final SoundEvent CIRCUIT_BREAKER_RESTORE = REGISTRAR.create("block.circuit_breaker.restore");
    public static final SoundEvent SABOTAGE_READY = REGISTRAR.create("ui.sabotage.ready");

    /**
     * Party Animal's Partygoer, clips picked by the owner (2026-10-21): spawn, grab, killed, gave up (vanish) and rage.
     * The ids double as StopSound targets so a new clip cuts the previous one.
     * 派对狂的派对客，音频由主人指定（2026-10-21）：出生、抓到人、被打死、放弃（消失）与狂怒。ID 也用作停止音效的目标，让新音频切断上一段。
     */
    public static final Identifier PARTYGOER_SPAWN_ID = SparkStrength.id("entity.partygoer.spawn");
    public static final SoundEvent PARTYGOER_SPAWN = REGISTRAR.create("entity.partygoer.spawn");
    public static final Identifier PARTYGOER_GRAB_ID = SparkStrength.id("entity.partygoer.grab");
    public static final SoundEvent PARTYGOER_GRAB = REGISTRAR.create("entity.partygoer.grab");
    public static final Identifier PARTYGOER_DEATH_ID = SparkStrength.id("entity.partygoer.death");
    public static final SoundEvent PARTYGOER_DEATH = REGISTRAR.create("entity.partygoer.death");
    public static final Identifier PARTYGOER_VANISH_ID = SparkStrength.id("entity.partygoer.vanish");
    public static final SoundEvent PARTYGOER_VANISH = REGISTRAR.create("entity.partygoer.vanish");
    public static final Identifier PARTYGOER_RAGE_ID = SparkStrength.id("entity.partygoer.rage");
    public static final SoundEvent PARTYGOER_RAGE = REGISTRAR.create("entity.partygoer.rage");

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
