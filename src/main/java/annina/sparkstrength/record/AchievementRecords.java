package annina.sparkstrength.record;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.coroner.CoronerRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Extra match-record data that SparkAssist reads for its local hidden achievements (achievement record contract,
 * SparkStrength part S1-S15). Event types and field names are binding. Server only: every caller runs on the logical
 * server, and Wathe's builder already drops events outside an active match. No replay formatter is registered for
 * these types, so Wathe's replay skips them. A recording failure is logged and swallowed: it never breaks gameplay.
 * SparkAssist 本地隐藏成就读取的额外对局记录数据（成就记录契约 SparkStrength 部分 S1-S15）。事件类型与字段名不可更改。
 * 仅服务端：所有调用方都在逻辑服务端，且 Wathe 的构建器会丢弃对局外的事件。这些类型不注册回放格式化器，
 * Wathe 回放会跳过它们。记录失败只写日志并吞掉异常，绝不影响玩法。
 */
public final class AchievementRecords {
    /** S1. actor = the Toxicologist. / actor 为毒理学家。 */
    public static final String TOXICOLOGIST_BLUE = "sparkstrength:toxicologist_blue";
    /** S2. actor = the Detective, target = the interrogated player; victim uuid, is_killer bool. / actor 为侦探，target 为被调查者。 */
    public static final String DETECTIVE_INTERROGATE = "sparkstrength:detective_interrogate";
    /** S3. actor = the Taotie; count int. / actor 为饕餮；count 为本次齐射的头颅数。 */
    public static final String TAOTIE_VOLLEY = "sparkstrength:taotie_volley";
    /** S4. actor = the Corrupt Cop; active bool. / actor 为黑警；active 为开关新状态。 */
    public static final String CORRUPT_COP_SHOW_OFF = "sparkstrength:corrupt_cop_show_off";
    /** S5. actor = the Coroner; role string ("" when cleared), killer bool. / actor 为验尸官；清除时 role 为空串。 */
    public static final String CORONER_DISGUISE = "sparkstrength:coroner_disguise";
    /** S7. actor = the killer whose income it was; amount int = coins it added to the purse. / actor 为收入所属杀手；amount 为计入团队钱包的金币。 */
    public static final String TEAM_CONTRIBUTION = "sparkstrength:team_contribution";
    /**
     * S8. World event at each real blackout edge: active bool; cause string (one of the BLACKOUT_CAUSE_* values);
     * actor = whoever caused that edge, when known (the buyer on "purchase", the restorer on "power_restoration").
     * 每个真实停电边沿的世界事件：active 布尔；cause 为 BLACKOUT_CAUSE_* 之一；已知时 actor 为造成该边沿的人。
     */
    public static final String BLACKOUT_STATE = "sparkstrength:blackout_state";
    /** S9. actor = the holder whose blackout blindness a lit flashlight cancelled; remaining_ticks int. / actor 为手电筒替其挡下停电失明的持有者。 */
    public static final String FLASHLIGHT_BLACKOUT = "sparkstrength:flashlight_blackout";
    /** S11. actor = the player who called the tablet emergency meeting. / actor 为召开平板紧急会议的玩家。 */
    public static final String TABLET_MEETING = "sparkstrength:tablet_meeting";
    /** S12. target = the player a meeting marked as suspect; caller uuid = that meeting's caller. / target 为会议投出的嫌疑人。 */
    public static final String TABLET_SUSPECT = "sparkstrength:tablet_suspect";
    /** S13. actor = the thrower; throw_id uuid (the axe entity's uuid). / actor 为投掷者；throw_id 为飞斧实体的 UUID。 */
    public static final String THROWING_AXE_THROW = "sparkstrength:throwing_axe_throw";
    /** S14. actor = the thrower, target = the player hit; throw_id uuid, killed bool. / actor 为投掷者，target 为被命中的玩家。 */
    public static final String THROWING_AXE_HIT = "sparkstrength:throwing_axe_hit";
    /**
     * S15. World event at each edge of an engine sabotage: state string (one of the ENGINE_STATE_* values); outage_id
     * int, the same on every event of one sabotage; phase string (one of the ENGINE_PHASE_* values), the phase at that
     * edge (for "repaired" and "aborted", the phase it ended in); actor = the initiator on "triggered", the repairer on
     * "repaired", none otherwise.
     * 动力破坏每个边沿的世界事件：state 为 ENGINE_STATE_* 之一；outage_id 为整数，同一次破坏的所有事件相同；phase 为
     * ENGINE_PHASE_* 之一，即该边沿时的阶段（repaired 与 aborted 为结束时所处的阶段）；actor 在 triggered 时为发起者，
     * 在 repaired 时为修理者，其余情况没有。
     */
    public static final String ENGINE_SABOTAGE = "sparkstrength:engine_sabotage";

    /** S8 cause: the lights went out through Wathe's blackout shop entry; actor = the buyer. / 经 Wathe 商店停电熄灯；actor 为购买者。 */
    public static final String BLACKOUT_CAUSE_PURCHASE = "purchase";
    /** S8 cause: an Engineer's power restoration ended it early; actor = the buyer. / 工程师恢复电力提前结束；actor 为购买者。 */
    public static final String BLACKOUT_CAUSE_POWER_RESTORATION = "power_restoration";
    /** S8 cause: the world tick saw the lights back with no known cause (the blackout ran out). / 世界 tick 看到来电且无已知原因（停电时间耗尽）。 */
    public static final String BLACKOUT_CAUSE_EXPIRED = "expired";
    /** S8 cause: the world tick saw the lights go out with no known cause. / 世界 tick 看到熄灯且无已知原因。 */
    public static final String BLACKOUT_CAUSE_UNKNOWN = "unknown";
    /** S8 cause: a killer triggered the lights sabotage from the tablet; actor = that killer. / 杀手在平板上触发了灯光破坏；actor 为该杀手。 */
    public static final String BLACKOUT_CAUSE_SABOTAGE = "sabotage";
    /** S8 cause: a circuit breaker repair brought the lights back; actor = the repairer. / 修好电闸来电；actor 为修理者。 */
    public static final String BLACKOUT_CAUSE_BREAKER_REPAIR = "breaker_repair";

    /**
     * S15 state: triggered from the tablet by someone who may sabotage (a killer or the Undercover); actor = that player.
     * 由可以破坏的人（杀手或卧底）在平板上触发；actor 为该玩家。
     */
    public static final String ENGINE_STATE_TRIGGERED = "triggered";
    /** S15 state: the 60 s repair window ran out; the train halts. / 60 秒抢修窗口已过，列车停驶。 */
    public static final String ENGINE_STATE_WRECKED = "wrecked";
    /** S15 state: halted for the cap; emergency power, still wrecked. / 停驶到上限：应急动力，仍是损毁。 */
    public static final String ENGINE_STATE_EMERGENCY = "emergency";
    /** S15 state: someone finished a repair panel; actor = the repairer. / 有人完成了维修面板；actor 为修理者。 */
    public static final String ENGINE_STATE_REPAIRED = "repaired";
    /**
     * S15 state: ended without a repair (round over, map copied again, last unit gone); no actor.
     * 不经维修结束（对局结束、重新复制地图、最后一台装置没了）；没有 actor。
     */
    public static final String ENGINE_STATE_ABORTED = "aborted";
    /** S15 phase: engine units damaged, the repair window running. / 动力装置受损，抢修窗口进行中。 */
    public static final String ENGINE_PHASE_CRITICAL = "critical";
    /** S15 phase: wrecked, the train halted. / 已损毁，列车停驶。 */
    public static final String ENGINE_PHASE_WRECKED = "wrecked";
    /** S15 phase: wrecked, the train on emergency power. / 已损毁，列车靠应急动力行驶。 */
    public static final String ENGINE_PHASE_EMERGENCY = "emergency";

    private AchievementRecords() {
    }

    /** S1: a real Toxicologist entered the blue state (debounced by the caller). / 真毒理学家进入蓝毒状态（调用方已去抖）。 */
    public static void toxicologistBlue(ServerPlayerEntity toxicologist) {
        safely(TOXICOLOGIST_BLUE, () -> GameRecordManager.event(TOXICOLOGIST_BLUE).actor(toxicologist).record());
    }

    /**
     * S2: a magnifier interrogation of {@code suspect} for the case of {@code victim} succeeded. {@code is_killer} is
     * true when the suspect (real identity) is the killer credited with the victim's latest recorded death.
     * 放大镜对嫌疑人的调查成功。嫌疑人（真实身份）是该受害者最近一次死亡记录中所记的凶手时 is_killer 为 true。
     */
    public static void detectiveInterrogate(ServerPlayerEntity detective, ServerPlayerEntity suspect, UUID victim) {
        safely(DETECTIVE_INTERROGATE, () -> {
            if (!GameRecordManager.hasActiveMatch()) {
                return;
            }
            GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
            UUID credited = match == null ? null : AchievementRecordRules.creditedKiller(
                    match.getEvents(),
                    victim,
                    event -> GameRecordTypes.DEATH.equals(event.type()),
                    event -> uuidOrNull(event, "target"),
                    event -> uuidOrNull(event, "actor"));
            GameRecordManager.event(DETECTIVE_INTERROGATE)
                    .actor(detective)
                    .target(suspect)
                    .putUuid("victim", victim)
                    .putBool("is_killer", AchievementRecordRules.isCreditedKiller(suspect.getUuid(), credited))
                    .record();
        });
    }

    /** S3: a Taotie volley spawned {@code count} heads. / 饕餮一次齐射生成了 count 颗头颅。 */
    public static void taotieVolley(ServerPlayerEntity taotie, int count) {
        safely(TAOTIE_VOLLEY, () -> GameRecordManager.event(TAOTIE_VOLLEY).actor(taotie).putInt("count", count).record());
    }

    /** S4: the server copy of the Corrupt Cop toggle really flipped. / 黑警开关的服务端副本确实翻转了。 */
    public static void corruptCopShowOff(ServerPlayerEntity cop, boolean active) {
        safely(CORRUPT_COP_SHOW_OFF,
                () -> GameRecordManager.event(CORRUPT_COP_SHOW_OFF).actor(cop).putBool("active", active).record());
    }

    /**
     * S5: the Coroner's active disguise changed; {@code roleId} null means cleared. {@code killer} uses the disguise's
     * own role, even while a higher-priority morph temporarily hides it.
     * 验尸官的当前伪装发生变化；roleId 为 null 表示清除。killer 按伪装本身的职业判断，即使暂时被更高优先级的变形压住。
     */
    public static void coronerDisguise(ServerPlayerEntity coroner, @Nullable Identifier roleId) {
        safely(CORONER_DISGUISE, () -> GameRecordManager.event(CORONER_DISGUISE)
                .actor(coroner)
                .put("role", roleId == null ? "" : roleId.toString())
                .putBool("killer", roleId != null && CoronerRules.isKillerFaction(CoronerRules.resolveRole(roleId)))
                .record());
    }

    /**
     * S7: a killer's income moved the killer team purse from {@code purseBefore} to {@code purseAfter}; records
     * {@code amount} = the increase, and nothing when the purse did not grow. One event per contribution.
     * 杀手的收入使杀手团队钱包从 purseBefore 变为 purseAfter；记录 amount = 增加量，钱包未增加时不记录。每笔贡献一条事件。
     */
    public static void teamContribution(ServerPlayerEntity killer, int purseBefore, int purseAfter) {
        safely(TEAM_CONTRIBUTION, () -> {
            int amount = AchievementRecordRules.purseIncrease(purseBefore, purseAfter);
            if (amount > 0) {
                GameRecordManager.event(TEAM_CONTRIBUTION).actor(killer).putInt("amount", amount).record();
            }
        });
    }

    /**
     * S8: a real blackout edge (the lights went out, or came back) in {@code world}; {@code cause} is one of the
     * BLACKOUT_CAUSE_* values and {@code actor} the player behind it, null when unknown. Fed by BlackoutRecorder,
     * which keeps one edge per change.
     * 该世界的一个真实停电边沿（熄灯或来电）；cause 为 BLACKOUT_CAUSE_* 之一，actor 为造成者，未知时为 null。
     * 由 BlackoutRecorder 调用，每次变化只记一个边沿。
     */
    public static void blackoutState(ServerWorld world, boolean active, @Nullable ServerPlayerEntity actor, String cause) {
        safely(BLACKOUT_STATE, () -> GameRecordManager.event(BLACKOUT_STATE)
                .world(world)
                .actor(actor)
                .putBool("active", active)
                .put("cause", cause)
                .record());
    }

    /**
     * S9: a lit flashlight cancelled {@code holder}'s blackout blindness, with {@code remainingTicks} of the blackout
     * left. Once per blackout per player (BlackoutRecorder).
     * 开启的手电筒挡下了持有者的停电失明，此时停电还剩 remainingTicks 个 tick。每次停电每名玩家一次（BlackoutRecorder）。
     */
    public static void flashlightBlackout(ServerPlayerEntity holder, int remainingTicks) {
        safely(FLASHLIGHT_BLACKOUT, () -> GameRecordManager.event(FLASHLIGHT_BLACKOUT)
                .actor(holder)
                .putInt("remaining_ticks", remainingTicks)
                .record());
    }

    /** S11: {@code caller} started a tablet emergency meeting. / 该玩家召开了平板紧急会议。 */
    public static void tabletMeeting(ServerPlayerEntity caller) {
        safely(TABLET_MEETING, () -> GameRecordManager.event(TABLET_MEETING).actor(caller).record());
    }

    /**
     * S12: a finished meeting added {@code suspect} to the suspect list; {@code caller} is that meeting's caller (left
     * out when unknown). The suspect goes into the target slot by uuid: a meeting may pick an offline player.
     * 会议结束时把嫌疑人加入了嫌疑人列表；caller 为该次会议的召开者（未知时省略）。嫌疑人按 UUID 写入 target：会议可能投出离线玩家。
     */
    public static void tabletSuspect(ServerWorld world, UUID suspect, @Nullable UUID caller) {
        safely(TABLET_SUSPECT, () -> {
            GameRecordManager.EventBuilder event = GameRecordManager.event(TABLET_SUSPECT)
                    .world(world)
                    .putUuid("target", suspect);
            if (caller != null) {
                event.putUuid("caller", caller);
            }
            event.record();
        });
    }

    /** S13: {@code thrower} threw a Bandit throwing axe; {@code throwId} is the spawned axe's uuid. / 投掷者扔出一把飞斧。 */
    public static void throwingAxeThrow(ServerPlayerEntity thrower, UUID throwId) {
        safely(THROWING_AXE_THROW, () -> GameRecordManager.event(THROWING_AXE_THROW)
                .actor(thrower)
                .putUuid("throw_id", throwId)
                .record());
    }

    /**
     * S14: the axe {@code throwId} hit {@code victim} and its kill call returned. {@code killed} is true when the victim
     * was alive before and is dead now (a shield, vest or fake death leaves it false). {@code thrower} is null when
     * the thrower is offline; the S13 event with the same throw_id still names them.
     * 飞斧 throwId 命中受害者且其击杀调用已返回。受害者此前存活、现在死亡时 killed 为 true（护盾、防弹衣或假死时为 false）。
     * 投掷者离线时 thrower 为 null；相同 throw_id 的 S13 事件仍记有投掷者。
     */
    public static void throwingAxeHit(@Nullable ServerPlayerEntity thrower, ServerPlayerEntity victim, UUID throwId,
                                      boolean deadBefore) {
        safely(THROWING_AXE_HIT, () -> GameRecordManager.event(THROWING_AXE_HIT)
                .world(victim.getServerWorld())
                .actor(thrower)
                .target(victim)
                .putUuid("throw_id", throwId)
                .putBool("killed", AchievementRecordRules.killedByHit(deadBefore, isMarkedDead(victim)))
                .record());
    }

    /**
     * S15: an engine sabotage edge in {@code world}; {@code state} is one of the ENGINE_STATE_* values and
     * {@code phase} one of the ENGINE_PHASE_* values. Fed by EngineRecorder, once per edge.
     * 该世界中动力破坏的一个边沿；state 为 ENGINE_STATE_* 之一，phase 为 ENGINE_PHASE_* 之一。由 EngineRecorder 调用，
     * 每个边沿一次。
     */
    public static void engineSabotage(ServerWorld world, String state, @Nullable ServerPlayerEntity actor, int outageId,
                                      String phase) {
        safely(ENGINE_SABOTAGE, () -> GameRecordManager.event(ENGINE_SABOTAGE)
                .world(world)
                .actor(actor)
                .put("state", state)
                .putInt("outage_id", outageId)
                .put("phase", phase)
                .record());
    }

    /** S14 helper: whether Wathe has marked the player dead this round; false on failure. / Wathe 本局是否已标记该玩家死亡。 */
    public static boolean isMarkedDead(ServerPlayerEntity player) {
        try {
            return GameWorldComponent.KEY.get(player.getWorld()).isPlayerDead(player.getUuid());
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not read the dead state for {}", THROWING_AXE_HIT, error);
            return false;
        }
    }

    /**
     * S10: extra data for the existing {@code sparkstrength:capture_device_triggered} global event: the device's owner
     * uuid. Null without an owner, which records the event as before.
     * S10：现有 capture_device_triggered 全局事件的附加数据：装置主人的 UUID。没有主人时返回 null，事件照旧记录。
     */
    public static @Nullable NbtCompound captureDeviceTriggeredExtra(@Nullable UUID owner) {
        if (owner == null) {
            return null;
        }
        NbtCompound extra = new NbtCompound();
        extra.putUuid("owner", owner);
        return extra;
    }

    /**
     * S6: extra data for the existing {@code sparkstrength:skateboard_ride_started} global event (vanilla Speed at ride
     * start). Null on failure, which records the event without the extra fields, as before.
     * S6：现有 skateboard_ride_started 全局事件的附加数据（上板时的原版速度效果）。失败时返回 null，事件照旧记录、只是不带附加字段。
     */
    public static @Nullable NbtCompound skateboardRideExtra(ServerPlayerEntity rider) {
        try {
            StatusEffectInstance speed = rider.getStatusEffect(StatusEffects.SPEED);
            NbtCompound extra = new NbtCompound();
            extra.putBoolean("has_speed", speed != null);
            extra.putInt("speed_amplifier",
                    AchievementRecordRules.speedAmplifier(speed != null, speed == null ? 0 : speed.getAmplifier()));
            return extra;
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not build skateboard ride record data", error);
            return null;
        }
    }

    private static @Nullable UUID uuidOrNull(GameRecordEvent event, String key) {
        NbtCompound data = event.data();
        return data != null && data.containsUuid(key) ? data.getUuid(key) : null;
    }

    private static void safely(String type, Runnable writer) {
        try {
            writer.run();
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record {}", type, error);
        }
    }
}
