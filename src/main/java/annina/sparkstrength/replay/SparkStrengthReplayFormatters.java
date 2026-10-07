package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.toxicologist.ToxicologistBlueVitriolService;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;

/**
 * SparkStrength 自己的回放格式化器。
 */
public final class SparkStrengthReplayFormatters {
    public static final Identifier NOISEMAKER_GLOW_STARTED = SparkStrength.id("noisemaker_glow_started");
    public static final Identifier NOISEMAKER_GLOW_ENDED = SparkStrength.id("noisemaker_glow_ended");
    public static final Identifier PHANTOM_BACKPACK_INVISIBILITY_STARTED = SparkStrength.id("phantom_backpack_invisibility_started");
    public static final Identifier PHANTOM_BACKPACK_INVISIBILITY_ENDED = SparkStrength.id("phantom_backpack_invisibility_ended");
    public static final Identifier PROFESSOR_SERUM_FED = SparkStrength.id("professor_serum_fed");
    public static final Identifier PROFESSOR_INVISIBILITY_ENDED = SparkStrength.id("professor_invisibility_ended");
    public static final Identifier PROFESSOR_DOORPASSING_ENDED = SparkStrength.id("professor_doorpassing_ended");
    public static final Identifier PROFESSOR_SEDATIVE_ENDED = SparkStrength.id("professor_sedative_ended");
    public static final Identifier PROFESSOR_TRUTH_REVEALED = SparkStrength.id("professor_truth_revealed");
    public static final Identifier CAPTURE_DEVICE_PLACED = SparkStrength.id("capture_device_placed");
    public static final Identifier CAPTURE_DEVICE_TRIGGERED = SparkStrength.id("capture_device_triggered");
    public static final Identifier CAPTURE_DEVICE_RELEASED = SparkStrength.id("capture_device_released");
    public static final Identifier CAPTURE_DEVICE_EXPIRED = SparkStrength.id("capture_device_expired");
    public static final Identifier TIMED_BOMB_TRAY_EMBEDDED = SparkStrength.id("timed_bomb_tray_embedded");
    public static final Identifier TIMED_BOMB_BED_EMBEDDED = SparkStrength.id("timed_bomb_bed_embedded");
    public static final Identifier TIMED_BOMB_TRAY_TRIGGERED = SparkStrength.id("timed_bomb_tray_triggered");
    public static final Identifier TIMED_BOMB_BED_TRIGGERED = SparkStrength.id("timed_bomb_bed_triggered");
    public static final Identifier DEMON_HUNTER_SNIFF_FOUND = SparkStrength.id("demon_hunter_sniff_found");
    public static final Identifier DEMON_HUNTER_SNIFF_NONE = SparkStrength.id("demon_hunter_sniff_none");
    public static final Identifier DEMON_HUNTER_SNIFF_REVEALED = SparkStrength.id("demon_hunter_sniff_revealed");
    public static final Identifier MORPH_REAGENT_SAMPLED = SparkStrength.id("morph_reagent_sampled");
    public static final Identifier MORPH_REAGENT_MARKED = SparkStrength.id("morph_reagent_marked");
    public static final Identifier MORPH_MARK_TRIGGERED = SparkStrength.id("morph_mark_triggered");
    public static final Identifier MORPH_MARK_ENDED = SparkStrength.id("morph_mark_ended");
    public static final Identifier PERFUMER_COOLING_OIL_HIT = SparkStrength.id("perfumer_cooling_oil_hit");
    public static final Identifier PERFUMER_AROMA_HIT = SparkStrength.id("perfumer_aroma_hit");
    public static final Identifier PERFUMER_ZEPHYR_USED = SparkStrength.id("perfumer_zephyr_used");
    public static final Identifier DRONE_PLACED = SparkStrength.id("drone_placed");
    public static final Identifier DRONE_GRENADE_DROPPED = SparkStrength.id("drone_grenade_dropped");
    public static final Identifier DRONE_DESTROYED = SparkStrength.id("drone_destroyed");
    public static final Identifier BOMB_DRONE_DETONATED = SparkStrength.id("bomb_drone_detonated");
    public static final Identifier SKATEBOARD_RIDE_STARTED = SparkStrength.id("skateboard_ride_started");
    public static final Identifier JESTER_POSITIONS_SHUFFLED = SparkStrength.id("jester_positions_shuffled");
    public static final Identifier JESTER_FAKE_DEATH_KILL_BLOCKED = SparkStrength.id("jester_fake_death_kill_blocked");
    public static final Identifier REPORTER_CONNECTION_STARTED = SparkStrength.id("reporter_connection_started");
    public static final Identifier REPORTER_CONNECTION_FAILED_ONE_DEAD = SparkStrength.id("reporter_connection_failed_one_dead");
    public static final Identifier REPORTER_CONNECTION_FAILED_BOTH_DEAD = SparkStrength.id("reporter_connection_failed_both_dead");
    public static final Identifier REPORTER_BROADCAST_STARTED = SparkStrength.id("reporter_broadcast_started");
    public static final Identifier REPORTER_BROADCAST_FAILED = SparkStrength.id("reporter_broadcast_failed");
    public static final Identifier REPORTER_CONNECTION_ENDED = SparkStrength.id("reporter_connection_ended");
    public static final Identifier REPORTER_BROADCAST_ENDED = SparkStrength.id("reporter_broadcast_ended");
    public static final Identifier REPORTER_CONNECTION_INTERRUPTED = SparkStrength.id("reporter_connection_interrupted");
    public static final Identifier REPORTER_BROADCAST_INTERRUPTED = SparkStrength.id("reporter_broadcast_interrupted");
    /**
     * 新版“双影谢幕”开始时写入的全局回放事件。
     *
     * <p>该事件与 NoellesRoles 原有的 {@code shadow_showdown_start} 分开，
     * 因为两者分别代表“影子小丑对抗杀手”和“杀手全部死亡后影子小丑清场”
     * 两种不同的谢幕机制。</p>
     */
    public static final Identifier SHADOW_JESTER_SHOWDOWN_STARTED =
            SparkStrength.id("shadow_jester_showdown_started");
    public static final Identifier TIMEKEEPER_WATCH_USED = SparkStrength.id("timekeeper_watch_used");

    private SparkStrengthReplayFormatters() {
    }

    public static void register() {
        ReplayRegistry.registerGlobalEventFormatter(TIMEKEEPER_WATCH_USED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }
            String mode = data.getString("mode");
            String key = "replay.global.sparkstrength.timekeeper_watch_used."
                    + ("ability_refresh".equals(mode) ? "ability" : "item");
            return Text.translatable(key, ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache));
        });
        // 新版双影谢幕是无特定触发者的全局事件，因此直接返回固定回放文案。
        // 文案中的 §d 和 §r 保留与 NoellesRoles 原版谢幕回放相同的紫色格式。
        ReplayRegistry.registerGlobalEventFormatter(
                SHADOW_JESTER_SHOWDOWN_STARTED,
                (event, match, world) -> Text.translatable(
                        "replay.global.sparkstrength.shadow_jester_showdown_started"
                )
        );

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
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.phantom_backpack_invisibility_ended"
                ));

        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_SERUM_FED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.professor_serum_fed",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatItemName(data, world),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });

        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_INVISIBILITY_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_invisibility_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_DOORPASSING_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_doorpassing_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_SEDATIVE_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_sedative_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PROFESSOR_TRUTH_REVEALED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.professor_truth_revealed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_PLACED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_placed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_TRIGGERED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_triggered"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_RELEASED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_released"
                ));
        ReplayRegistry.registerGlobalEventFormatter(CAPTURE_DEVICE_EXPIRED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.capture_device_expired"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_TRAY_EMBEDDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_tray_embedded"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_BED_EMBEDDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_bed_embedded"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_TRAY_TRIGGERED,
                (event, match, world) -> actorAndBomberEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_tray_triggered"
                ));
        ReplayRegistry.registerGlobalEventFormatter(TIMED_BOMB_BED_TRIGGERED,
                (event, match, world) -> actorAndBomberEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.timed_bomb_bed_triggered"
                ));
        ReplayRegistry.registerGlobalEventFormatter(DEMON_HUNTER_SNIFF_FOUND, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.demon_hunter_sniff_found",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    data.getInt("count")
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(DEMON_HUNTER_SNIFF_NONE,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.demon_hunter_sniff_none"
                ));
        ReplayRegistry.registerGlobalEventFormatter(DEMON_HUNTER_SNIFF_REVEALED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.demon_hunter_sniff_revealed",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });

        ReplayRegistry.registerGlobalEventFormatter(MORPH_REAGENT_SAMPLED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID sampleUuid = data.containsUuid("sample") ? data.getUuid("sample") : null;
            if (actorUuid == null || sampleUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.morph_reagent_sampled",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    formatPlayerNameWithFallback(sampleUuid, data.getString("sample_name"), playerInfoCache)
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(MORPH_REAGENT_MARKED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID sampleUuid = data.containsUuid("sample") ? data.getUuid("sample") : null;
            UUID targetUuid = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actorUuid == null || sampleUuid == null || targetUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.morph_reagent_marked",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    formatPlayerNameWithFallback(sampleUuid, data.getString("sample_name"), playerInfoCache),
                    ReplayGenerator.formatPlayerName(targetUuid, playerInfoCache)
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(MORPH_MARK_TRIGGERED, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID sampleUuid = data.containsUuid("sample") ? data.getUuid("sample") : null;
            if (actorUuid == null || sampleUuid == null) {
                return null;
            }

            return Text.translatable(
                    "replay.global.sparkstrength.morph_mark_triggered",
                    ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                    formatPlayerNameWithFallback(sampleUuid, data.getString("sample_name"), playerInfoCache)
            );
        });
        ReplayRegistry.registerGlobalEventFormatter(MORPH_MARK_ENDED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.morph_mark_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PERFUMER_COOLING_OIL_HIT,
                (event, match, world) -> throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.perfumer_cooling_oil_hit"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PERFUMER_AROMA_HIT,
                (event, match, world) -> throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.perfumer_aroma_hit"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PERFUMER_ZEPHYR_USED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.perfumer_zephyr_used"
                ));
        ReplayRegistry.registerGlobalEventFormatter(SKATEBOARD_RIDE_STARTED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.skateboard_ride_started"
                ));
        // Actor = the Jester whose transformation shuffled everyone. / actor 为开始转变、打乱所有人位置的小丑。
        ReplayRegistry.registerGlobalEventFormatter(JESTER_POSITIONS_SHUFFLED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.jester_positions_shuffled"
                ));
        // Actor = the Jester whose fake corpse shrugged off a kill. / actor 为假尸体挡下一次击杀的小丑。
        ReplayRegistry.registerGlobalEventFormatter(JESTER_FAKE_DEATH_KILL_BLOCKED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.jester_fake_death_kill_blocked"
                ));
        ReplayRegistry.registerGlobalEventFormatter(DRONE_PLACED, (event, match, world) -> {
            NbtCompound data = event.data();
            return onePlayerEvent(data, match, "replay.global.sparkstrength.drone_placed." + droneKindId(data));
        });
        ReplayRegistry.registerGlobalEventFormatter(DRONE_GRENADE_DROPPED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.drone_grenade_dropped"
                ));
        // Actor = who broke it (none when it crashed), target = the owner. / actor 为击毁者（坠毁时为空），target 为主人。
        ReplayRegistry.registerGlobalEventFormatter(DRONE_DESTROYED, (event, match, world) -> {
            NbtCompound data = event.data();
            return throwableHitEvent(data, match, "replay.global.sparkstrength.drone_destroyed." + droneKindId(data));
        });
        ReplayRegistry.registerGlobalEventFormatter(BOMB_DRONE_DETONATED,
                (event, match, world) -> onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.bomb_drone_detonated"
                ));
        // Wathe drops ITEM_USE events whose item has no registered formatter; the action comes from the vitriol service.
        // Wathe 会忽略没有注册格式化器的物品使用事件；action 由蓝矾服务写入。
        ReplayRegistry.registerItemUseFormatter(SparkStrengthItems.BLUE_VITRIOL_ID, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }
            Text actorText = ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache);
            String action = data.getString("action");
            if (ToxicologistBlueVitriolService.ACTION_FOOD.equals(action)) {
                return Text.translatable(
                        "replay.item_use.sparkstrength.blue_vitriol.food",
                        actorText,
                        ReplayGenerator.formatItemName(data, world)
                );
            }
            if (ToxicologistBlueVitriolService.ACTION_PLATE.equals(action)
                    || ToxicologistBlueVitriolService.ACTION_BED.equals(action)
                    || ToxicologistBlueVitriolService.ACTION_CAPSULE.equals(action)) {
                return Text.translatable("replay.item_use.sparkstrength.blue_vitriol." + action, actorText);
            }
            return null;
        });
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_STARTED,
                (event, match, world) -> reporterConnectionWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_started",
                        false
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_FAILED_ONE_DEAD,
                (event, match, world) -> reporterConnectionWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_failed_one_dead",
                        true
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_FAILED_BOTH_DEAD,
                (event, match, world) -> reporterConnectionWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_failed_both_dead",
                        false
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_STARTED,
                (event, match, world) -> reporterBroadcastWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_started",
                        false
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_FAILED,
                (event, match, world) -> reporterBroadcastWithActorEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_failed",
                        true
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_ENDED,
                (event, match, world) -> reporterConnectionEndedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_ENDED,
                (event, match, world) -> reporterBroadcastEndedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_ended"
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_CONNECTION_INTERRUPTED,
                (event, match, world) -> reporterConnectionInterruptedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_connection_interrupted"
                ));
        ReplayRegistry.registerGlobalEventFormatter(REPORTER_BROADCAST_INTERRUPTED,
                (event, match, world) -> reporterBroadcastInterruptedEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.reporter_broadcast_interrupted"
                ));
    }

    private static Text onePlayerEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
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
    private static Text throwableHitEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
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

    /** Known drone kind id only, so a malformed event can never pick an arbitrary key. / 仅接受已知无人机型号 id。 */
    private static String droneKindId(NbtCompound data) {
        return DroneKind.BOMB.id().equals(data.getString("kind")) ? DroneKind.BOMB.id() : DroneKind.GRENADE.id();
    }

    private static Text actorAndBomberEvent(NbtCompound data, dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match, String key) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        UUID bomberUuid = data.containsUuid("bomber") ? data.getUuid("bomber") : null;
        if (actorUuid == null || bomberUuid == null) {
            return null;
        }
        return Text.translatable(
                key,
                ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache),
                ReplayGenerator.formatPlayerName(bomberUuid, playerInfoCache)
        );
    }

    private static Text reporterConnectionWithActorEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key,
            boolean includeDeadPlayer
    ) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        Text first = playerFromUuidOrName(data, match, "player_one", "player_one_name");
        Text second = playerFromUuidOrName(data, match, "player_two", "player_two_name");
        if (actorUuid == null || first == null || second == null) {
            return null;
        }

        Text actor = ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache);
        if (includeDeadPlayer) {
            Text dead = playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
            return dead == null ? null : Text.translatable(key, actor, first, second, dead);
        }
        return Text.translatable(key, actor, first, second);
    }

    private static Text reporterBroadcastWithActorEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key,
            boolean includeDeadPlayer
    ) {
        var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
        UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
        Text target = playerFromUuidOrName(data, match, "target_player", "target_player_name");
        if (actorUuid == null || target == null) {
            return null;
        }

        Text actor = ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache);
        if (includeDeadPlayer) {
            Text dead = playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
            return dead == null ? null : Text.translatable(key, actor, target, dead);
        }
        return Text.translatable(key, actor, target);
    }

    private static Text reporterConnectionEndedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text first = playerFromUuidOrName(data, match, "player_one", "player_one_name");
        Text second = playerFromUuidOrName(data, match, "player_two", "player_two_name");
        if (first == null || second == null) {
            return null;
        }
        return Text.translatable(key, first, second);
    }

    private static Text reporterBroadcastEndedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text target = playerFromUuidOrName(data, match, "target_player", "target_player_name");
        return target == null ? null : Text.translatable(key, target);
    }

    private static Text reporterConnectionInterruptedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text first = playerFromUuidOrName(data, match, "player_one", "player_one_name");
        Text second = playerFromUuidOrName(data, match, "player_two", "player_two_name");
        Text dead = playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
        if (first == null || second == null || dead == null) {
            return null;
        }
        return Text.translatable(key, first, second, dead);
    }

    private static Text reporterBroadcastInterruptedEvent(
            NbtCompound data,
            dev.doctor4t.wathe.record.GameRecordManager.MatchRecord match,
            String key
    ) {
        Text dead = playerFromUuidOrName(data, match, "dead_player", "dead_player_name");
        return dead == null ? null : Text.translatable(key, dead);
    }

    private static Text playerFromUuidOrName(
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

    private static Text formatPlayerNameWithFallback(
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
