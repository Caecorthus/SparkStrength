package annina.sparkstrength.replay;

import net.minecraft.util.Identifier;

/**
 * SparkStrength 自己的回放格式化器。
 *
 * <p>Each feature declares its event ids and formatters in its own {@code *ReplayEvents} class in this package; new
 * events go there, not here. The constants below are the same instances, kept so existing callers and {@code ==}
 * checks keep working.
 * 每个功能在本包内自己的 {@code *ReplayEvents} 类中声明事件 id 与格式化器，新事件也加在那里而不是这里。下面的常量是同一实例，
 * 保留它们以便现有调用方与 {@code ==} 比较继续有效。</p>
 */
public final class SparkStrengthReplayFormatters {
    public static final Identifier NOISEMAKER_GLOW_STARTED = NoisemakerReplayEvents.NOISEMAKER_GLOW_STARTED;
    public static final Identifier NOISEMAKER_GLOW_ENDED = NoisemakerReplayEvents.NOISEMAKER_GLOW_ENDED;
    public static final Identifier PHANTOM_BACKPACK_INVISIBILITY_STARTED = PhantomBackpackReplayEvents.PHANTOM_BACKPACK_INVISIBILITY_STARTED;
    public static final Identifier PHANTOM_BACKPACK_INVISIBILITY_ENDED = PhantomBackpackReplayEvents.PHANTOM_BACKPACK_INVISIBILITY_ENDED;
    public static final Identifier PROFESSOR_SERUM_FED = ProfessorReplayEvents.PROFESSOR_SERUM_FED;
    public static final Identifier PROFESSOR_INVISIBILITY_ENDED = ProfessorReplayEvents.PROFESSOR_INVISIBILITY_ENDED;
    public static final Identifier PROFESSOR_DOORPASSING_ENDED = ProfessorReplayEvents.PROFESSOR_DOORPASSING_ENDED;
    public static final Identifier PROFESSOR_SEDATIVE_ENDED = ProfessorReplayEvents.PROFESSOR_SEDATIVE_ENDED;
    public static final Identifier PROFESSOR_TRUTH_REVEALED = ProfessorReplayEvents.PROFESSOR_TRUTH_REVEALED;
    public static final Identifier CAPTURE_DEVICE_PLACED = CaptureDeviceReplayEvents.CAPTURE_DEVICE_PLACED;
    public static final Identifier CAPTURE_DEVICE_TRIGGERED = CaptureDeviceReplayEvents.CAPTURE_DEVICE_TRIGGERED;
    public static final Identifier CAPTURE_DEVICE_RELEASED = CaptureDeviceReplayEvents.CAPTURE_DEVICE_RELEASED;
    public static final Identifier CAPTURE_DEVICE_EXPIRED = CaptureDeviceReplayEvents.CAPTURE_DEVICE_EXPIRED;
    public static final Identifier TIMED_BOMB_TRAY_EMBEDDED = TimedBombReplayEvents.TIMED_BOMB_TRAY_EMBEDDED;
    public static final Identifier TIMED_BOMB_BED_EMBEDDED = TimedBombReplayEvents.TIMED_BOMB_BED_EMBEDDED;
    public static final Identifier TIMED_BOMB_TRAY_TRIGGERED = TimedBombReplayEvents.TIMED_BOMB_TRAY_TRIGGERED;
    public static final Identifier TIMED_BOMB_BED_TRIGGERED = TimedBombReplayEvents.TIMED_BOMB_BED_TRIGGERED;
    public static final Identifier DEMON_HUNTER_SNIFF_FOUND = DemonHunterReplayEvents.DEMON_HUNTER_SNIFF_FOUND;
    public static final Identifier DEMON_HUNTER_SNIFF_NONE = DemonHunterReplayEvents.DEMON_HUNTER_SNIFF_NONE;
    public static final Identifier DEMON_HUNTER_SNIFF_REVEALED = DemonHunterReplayEvents.DEMON_HUNTER_SNIFF_REVEALED;
    public static final Identifier MORPH_REAGENT_SAMPLED = MorphlingReplayEvents.MORPH_REAGENT_SAMPLED;
    public static final Identifier MORPH_REAGENT_MARKED = MorphlingReplayEvents.MORPH_REAGENT_MARKED;
    public static final Identifier MORPH_MARK_TRIGGERED = MorphlingReplayEvents.MORPH_MARK_TRIGGERED;
    public static final Identifier MORPH_MARK_ENDED = MorphlingReplayEvents.MORPH_MARK_ENDED;
    public static final Identifier PERFUMER_COOLING_OIL_HIT = PerfumerReplayEvents.PERFUMER_COOLING_OIL_HIT;
    public static final Identifier PERFUMER_AROMA_HIT = PerfumerReplayEvents.PERFUMER_AROMA_HIT;
    public static final Identifier PERFUMER_ZEPHYR_USED = PerfumerReplayEvents.PERFUMER_ZEPHYR_USED;
    public static final Identifier DRONE_PLACED = DroneReplayEvents.DRONE_PLACED;
    public static final Identifier DRONE_GRENADE_DROPPED = DroneReplayEvents.DRONE_GRENADE_DROPPED;
    public static final Identifier DRONE_DESTROYED = DroneReplayEvents.DRONE_DESTROYED;
    public static final Identifier BOMB_DRONE_DETONATED = DroneReplayEvents.BOMB_DRONE_DETONATED;
    public static final Identifier SKATEBOARD_RIDE_STARTED = SkateboardReplayEvents.SKATEBOARD_RIDE_STARTED;
    public static final Identifier JESTER_POSITIONS_SHUFFLED = JesterReplayEvents.JESTER_POSITIONS_SHUFFLED;
    public static final Identifier JESTER_FAKE_DEATH_KILL_BLOCKED = JesterReplayEvents.JESTER_FAKE_DEATH_KILL_BLOCKED;
    public static final Identifier REPORTER_CONNECTION_STARTED = ReporterReplayEvents.REPORTER_CONNECTION_STARTED;
    public static final Identifier REPORTER_CONNECTION_FAILED_ONE_DEAD = ReporterReplayEvents.REPORTER_CONNECTION_FAILED_ONE_DEAD;
    public static final Identifier REPORTER_CONNECTION_FAILED_BOTH_DEAD = ReporterReplayEvents.REPORTER_CONNECTION_FAILED_BOTH_DEAD;
    public static final Identifier REPORTER_BROADCAST_STARTED = ReporterReplayEvents.REPORTER_BROADCAST_STARTED;
    public static final Identifier REPORTER_BROADCAST_FAILED = ReporterReplayEvents.REPORTER_BROADCAST_FAILED;
    public static final Identifier REPORTER_CONNECTION_ENDED = ReporterReplayEvents.REPORTER_CONNECTION_ENDED;
    public static final Identifier REPORTER_BROADCAST_ENDED = ReporterReplayEvents.REPORTER_BROADCAST_ENDED;
    public static final Identifier REPORTER_CONNECTION_INTERRUPTED = ReporterReplayEvents.REPORTER_CONNECTION_INTERRUPTED;
    public static final Identifier REPORTER_BROADCAST_INTERRUPTED = ReporterReplayEvents.REPORTER_BROADCAST_INTERRUPTED;
    public static final Identifier SHADOW_JESTER_SHOWDOWN_STARTED = ShadowJesterReplayEvents.SHADOW_JESTER_SHOWDOWN_STARTED;
    public static final Identifier TIMEKEEPER_WATCH_USED = TimekeeperReplayEvents.TIMEKEEPER_WATCH_USED;
    public static final Identifier TAOTIE_HEAD_HIT = TaotieHeadReplayEvents.TAOTIE_HEAD_HIT;
    public static final Identifier SPIRIT_POSSESSION_STARTED = SpiritPossessionReplayEvents.SPIRIT_POSSESSION_STARTED;
    public static final Identifier PARTY_BALLOON_GIVEN = PartyAnimalReplayEvents.PARTY_BALLOON_GIVEN;
    public static final Identifier PARTYGOER_SUMMONED = PartyAnimalReplayEvents.PARTYGOER_SUMMONED;
    public static final Identifier PARTYGOER_GRABBED = PartyAnimalReplayEvents.PARTYGOER_GRABBED;
    public static final Identifier PARTYGOER_RAGED = PartyAnimalReplayEvents.PARTYGOER_RAGED;
    public static final Identifier PARTYGOER_KILLED = PartyAnimalReplayEvents.PARTYGOER_KILLED;
    public static final Identifier PARTYGOER_SWALLOWED = PartyAnimalReplayEvents.PARTYGOER_SWALLOWED;

    private SparkStrengthReplayFormatters() {
    }

    public static void register() {
        TimekeeperReplayEvents.register();
        ShadowJesterReplayEvents.register();
        NoisemakerReplayEvents.register();
        PhantomBackpackReplayEvents.register();
        ProfessorReplayEvents.register();
        CaptureDeviceReplayEvents.register();
        TimedBombReplayEvents.register();
        DemonHunterReplayEvents.register();
        MorphlingReplayEvents.register();
        PerfumerReplayEvents.registerThrowables();
        TaotieHeadReplayEvents.register();
        SpiritPossessionReplayEvents.register();
        PartyAnimalReplayEvents.register();
        PerfumerReplayEvents.registerZephyr();
        SkateboardReplayEvents.register();
        JesterReplayEvents.register();
        DroneReplayEvents.register();
        BlueVitriolReplayEvents.register();
        ReporterReplayEvents.register();
    }
}
