package annina.sparkstrength.record;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.coroner.CoronerRules;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Extra match-record data that SparkAssist reads for its local hidden achievements (achievement record contract,
 * SparkStrength part S1-S7). Event types and field names are binding. Server only: every caller runs on the logical
 * server, and Wathe's builder already drops events outside an active match. No replay formatter is registered for
 * these types, so Wathe's replay skips them. A recording failure is logged and swallowed: it never breaks gameplay.
 * SparkAssist 本地隐藏成就读取的额外对局记录数据（成就记录契约 SparkStrength 部分 S1-S7）。事件类型与字段名不可更改。
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
