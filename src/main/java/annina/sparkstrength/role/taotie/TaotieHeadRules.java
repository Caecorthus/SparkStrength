package annina.sparkstrength.role.taotie;

import annina.sparkstrength.payloadpolicy.GeneratedPayloadPolicy;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Pure rules for the Taotie head launch (owner-approved spec 2026-10-07, revision 2: one head per living swallowed
 * player): numbers, the cooldown tier, the volley fan, steering math, the homing cone and the daze payload deny-list.
 * Inputs in, values out, no world access.
 * 饕餮"发射头颅"的纯规则（所有者 2026-10-07 批准，第 2 版：体内每名存活玩家一颗头颅）：数值、冷却档位、齐射扇形、
 * 转向数学、追踪锥与眩晕数据包拦截表。只接收输入、返回结果，不访问世界。
 */
public final class TaotieHeadRules {
    public static final Identifier TAOTIE_ID = Identifier.of("noellesroles", "taotie");
    /** SparkFactionAPI {@code canAffectPlayer} action of a head hit. / 头颅命中使用的 SparkFactionAPI 行为 id。 */
    public static final Identifier HEAD_ACTION_ID = Identifier.of("sparkstrength", "taotie_head");
    /** SparkFactionAPI forced-cooldown store id. / SparkFactionAPI 强制冷却存储 id。 */
    public static final Identifier COOLDOWN_STORE_ID = Identifier.of("sparkstrength", "taotie_head");
    public static final Identifier FIRE_PAYLOAD_ID = Identifier.of("sparkstrength", "taotie_head_fire");

    /**
     * Cooldown tier by living swallowed players at fire time, set once per volley: 1 → 60 s, 2 → 45 s, 3+ → 30 s.
     * 按发射时体内存活人数分档，每次齐射只设置一次。
     */
    public static final int COOLDOWN_ONE_TICKS = 60 * 20;
    public static final int COOLDOWN_TWO_TICKS = 45 * 20;
    public static final int COOLDOWN_THREE_PLUS_TICKS = 30 * 20;

    /** Blocks per tick, constant, no gravity. / 每 tick 方块数，恒速、无重力。 */
    public static final double SPEED = 1.0D;
    public static final double MAX_PATH_BLOCKS = 30.0D;
    /** Spawn point: eye + the head's own fan heading * this. / 生成点：眼睛 + 该头颅自身的扇形朝向 * 此值。 */
    public static final double SPAWN_FORWARD_OFFSET = 0.6D;
    /** Yaw between neighbouring heads of one volley. / 同一次齐射中相邻头颅的偏航间隔。 */
    public static final double FAN_SPACING_DEGREES = 8.0D;
    public static final float HEAD_SIZE = 0.5F;
    public static final double HEAD_HALF_SIZE = HEAD_SIZE / 2.0D;

    public static final double HOMING_RANGE = 8.0D;
    public static final double HOMING_RANGE_SQUARED = HOMING_RANGE * HOMING_RANGE;
    /** Half angle of the 90° acquisition cone. / 90° 索敌锥的半角。 */
    public static final double HOMING_HALF_ANGLE_DEGREES = 45.0D;
    public static final double MAX_TURN_DEGREES_PER_TICK = 12.0D;

    public static final int DAZE_TICKS = 60;
    public static final int BLINDNESS_TICKS = 60;
    public static final int SLOWNESS_TICKS = 60;
    /** Slowness V. / 缓慢 V。 */
    public static final int SLOWNESS_AMPLIFIER = 4;

    public static final int TRAIL_COLOR = 0x8B4513;
    public static final float TRAIL_DUST_SCALE = 1.0F;

    public static final float LAUNCH_SPIT_VOLUME = 1.0F;
    public static final float LAUNCH_SPIT_PITCH = 0.8F;
    public static final float LAUNCH_GHAST_VOLUME = 0.5F;
    public static final float LAUNCH_GHAST_PITCH = 0.6F;
    public static final float BREAK_VOLUME = 0.6F;
    public static final float BONK_VOLUME = 1.0F;
    public static final float BONK_PITCH = 1.0F;

    /**
     * Stable contract: C2S payloads dropped on the server while the sender is dazed (and, through their own guard
     * mixins, while carried by a Partygoer or held by an Engineer stun). Plain ids only, so no optional
     * mod class is ever loaded. Built from SparkWitch's {@code ControlExpertStunRules.BLOCKED_PAYLOADS}, with these
     * deliberate differences:
     * <ul>
     *   <li>removed {@code wathe:storebuy}: the shop stays usable while dazed;</li>
     *   <li>added the SparkStrength skills that list misses: {@code reporter_communication} (Reporter link/broadcast,
     *       cooldown skill), {@code drone_pilot_start}
     *       (taking control of a drone) and {@code drone_pilot_action} (drone drop/detonate),
     *       {@code taotie_head_fire} itself, the Party Animal's {@code partygoer_summon}, the Taotie swallowing a
     *       Partygoer {@code partygoer_swallow}, the sabotage actions {@code trigger_sabotage} and
     *       {@code complete_breaker_repair}, and the Detective's remote interrogation
     *       {@code interrogate_detective_suspect} (cooldown skill);</li>
     *   <li>kept allowed (movement, leaving or UI-only): {@code drone_pilot_move}, {@code drone_pilot_exit},
     *       {@code m67_cancel}, {@code cancel_sabotage_channel}, {@code cancel_breaker_repair}, {@code partygoer_menu}
     *       (the room picker), the tablet
     *       snapshot/chat/channel ids and the detective folder ids
     *       ({@code select_detective_case}, {@code update_detective_case_notes}, {@code set_detective_killer_guess},
     *       {@code set_detective_presumed_killer}; opening the magnifier roster is a vanilla item use), and, since
     *       2026-11-13, {@code timekeeper_watch_mode} (the Dying Watch mode pick only stores the mode; the watch's use
     *       is an item use the daze already refuses). The same day the SparkWitch cross sword and Swordfish stabs
     *       joined the list, classified like {@code wathe:knifestab};</li>
     *   <li>NoellesRoles: all 14 C2S ids registered by the pinned runtime jar (sha fcb0da) are already in the list,
     *       so none are added.</li>
     * </ul>
     * A new skill payload must be classified deliberately, as a complete row of the canonical table (below).
     * 稳定契约：发送者处于眩晕时（以及经各自的拦截 mixin，被派对客扛着或被工程师定身时）在服务端丢弃的 C2S 数据包。只保存纯 id，从不加载可选模组的类。以 SparkWitch 的
     * {@code ControlExpertStunRules.BLOCKED_PAYLOADS} 为基础，刻意的差异如下：
     * 移除 {@code wathe:storebuy}（眩晕时仍可使用商店）；补充该表缺少的 SparkStrength 技能包：记者通讯
     * {@code reporter_communication}（有冷却的技能）、接管无人机
     * {@code drone_pilot_start}、无人机投弹/引爆 {@code drone_pilot_action}、本技能 {@code taotie_head_fire}、派对狂召唤
     * {@code partygoer_summon}、饕餮吞派对客 {@code partygoer_swallow}、破坏系统的
     * {@code trigger_sabotage} 与 {@code complete_breaker_repair}，以及侦探远程审问
     * {@code interrogate_detective_suspect}（有冷却的技能）；
     * 继续放行（移动、离开或仅界面用途）：{@code drone_pilot_move}、{@code drone_pilot_exit}、{@code m67_cancel}、
     * {@code partygoer_menu}（选房界面）、
     * {@code cancel_sabotage_channel}、{@code cancel_breaker_repair}、
     * 平板快照/聊天/频道以及侦探文件夹相关 id（打开放大镜名单是原版物品使用），以及自 2026-11-13 起的
     * {@code timekeeper_watch_mode}（怀表模式选择只存储模式；怀表的使用是眩晕已拒绝的物品使用）。同日 SparkWitch 十字剑与
     * 剑鱼刺击加入本列表，与 {@code wathe:knifestab} 同样归类；NoellesRoles：锁定运行时 jar（sha fcb0da）注册的 14 个 C2S id
     * 均已在表中，无需补充。
     * 新增技能包必须作为规范表中完整的一行有意识地归类（见下）。
     */
    // Generated DAZE column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // The same table gives PartygoerCarry.EXTRA_BLOCKED_PAYLOADS (CARRY_EXTRA). From a SparkFactionAPI checkout:
    //   python3 <SparkFactionAPI>/tools/payload_policy.py generate --repo <SparkStrength> --mod strength
    //   python3 <SparkFactionAPI>/tools/payload_policy.py check --repo <SparkStrength> --mod strength
    // check fails on a stale generated file or on a registered C2S payload without a table row.
    // 生成的 DAZE 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。PartygoerCarry.EXTRA_BLOCKED_PAYLOADS
    // （CARRY_EXTRA 列）来自同一张表。在 SparkFactionAPI 检出中用上面两条命令重新生成（generate）与校验（check）；
    // 生成文件过期或已注册的 C2S 数据包在表中没有对应行时，check 会失败。
    public static final Set<Identifier> DAZE_BLOCKED_PAYLOADS = GeneratedPayloadPolicy.DAZE;

    private TaotieHeadRules() {
    }

    public static boolean isTaotie(@Nullable Role role) {
        return role != null && TAOTIE_ID.equals(role.identifier());
    }

    /**
     * Cooldown for a launch with {@code livingSwallowed} living swallowed players; 0 or 1 → 60 s (also the forced
     * cooldown nominal while the stomach is empty).
     * 体内有 {@code livingSwallowed} 名存活玩家时的发射冷却；0 或 1 → 60 秒（体内为空时也作为强制冷却标称值）。
     */
    public static int cooldownTicksFor(int livingSwallowed) {
        if (livingSwallowed >= 3) {
            return COOLDOWN_THREE_PLUS_TICKS;
        }
        return livingSwallowed == 2 ? COOLDOWN_TWO_TICKS : COOLDOWN_ONE_TICKS;
    }

    /**
     * Yaw offsets in degrees for a volley of {@code count} heads, centred on the crosshair and ascending: head
     * {@code i} gets {@code (i - (count - 1) / 2) * 8}, so 1 → [0], 3 → [-8, 0, 8], 4 → [-12, -4, 4, 12]. Empty for
     * {@code count <= 0}; a fresh array every call.
     * {@code count} 颗头颅齐射时的偏航偏移（度），以准星为中心、升序排列：第 {@code i} 颗为
     * {@code (i - (count - 1) / 2) * 8}，即 1 → [0]、3 → [-8, 0, 8]、4 → [-12, -4, 4, 12]。{@code count <= 0} 时为空；
     * 每次调用返回新数组。
     */
    public static double[] fanYawOffsets(int count) {
        if (count <= 0) {
            return new double[0];
        }
        double[] offsets = new double[count];
        double middle = (count - 1) / 2.0D;
        for (int i = 0; i < count; i++) {
            offsets[i] = (i - middle) * FAN_SPACING_DEGREES;
        }
        return offsets;
    }

    /**
     * Turns {@code look} about the world up axis by {@code yawDegrees}, in Minecraft's yaw direction (positive = the
     * way the player's yaw grows, so +90 turns south (+Z) to west (-X)). The vertical component and the length are
     * kept, so the pitch is unchanged; a straight-up/down or zero look has no horizontal part and comes back as is
     * (never NaN).
     * 绕世界竖直轴将 {@code look} 转动 {@code yawDegrees} 度，方向与 Minecraft 偏航一致（正值 = 玩家 yaw 增大的方向，
     * +90 会把南 (+Z) 转为西 (-X)）。竖直分量与长度不变，因此俯仰角不变；正上/正下或零向量没有水平分量，原样返回
     * （不会产生 NaN）。
     */
    public static Heading fanHeading(Heading look, double yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Heading(look.x() * cos - look.z() * sin, look.y(), look.z() * cos + look.x() * sin);
    }

    public static boolean isDazeBlockedPayload(@Nullable Identifier payloadId) {
        return payloadId != null && DAZE_BLOCKED_PAYLOADS.contains(payloadId);
    }

    /** Inclusive path limit. / 含端点的飞行距离上限。 */
    public static boolean pathExhausted(double travelledBlocks) {
        return travelledBlocks >= MAX_PATH_BLOCKS;
    }

    /**
     * True when {@code toTarget} lies within {@code halfAngleDegrees} of {@code heading}. A zero-length offset (target
     * centre at the head) counts as inside; a zero heading never does.
     * {@code toTarget} 与 {@code heading} 的夹角不超过 {@code halfAngleDegrees} 时为 true。偏移长度为 0（目标中心与头颅重合）
     * 视为在锥内；朝向为零向量时一律不在锥内。
     */
    public static boolean withinCone(Heading heading, Heading toTarget, double halfAngleDegrees) {
        double headingLength = heading.length();
        if (headingLength < 1.0E-9D) {
            return false;
        }
        double targetLength = toTarget.length();
        if (targetLength < 1.0E-9D) {
            return true;
        }
        double cos = heading.dot(toTarget) / (headingLength * targetLength);
        return cos >= Math.cos(Math.toRadians(halfAngleDegrees)) - 1.0E-12D;
    }

    /**
     * Rotates {@code heading} toward {@code desired} by at most {@code maxDegrees} (great-circle step) and returns the
     * result as a unit vector; within the limit it returns {@code desired} normalized. An exactly opposite desire turns
     * about an arbitrary perpendicular axis. A zero {@code desired} keeps the heading.
     * 将 {@code heading} 向 {@code desired} 最多转动 {@code maxDegrees}（大圆步进），返回单位向量；夹角在限制内时直接返回
     * 归一化的 {@code desired}。正好反向时绕任意垂直轴转动；{@code desired} 为零向量时保持原朝向。
     */
    public static Heading steer(Heading heading, Heading desired, double maxDegrees) {
        Heading from = heading.normalized();
        if (from == null) {
            Heading to = desired.normalized();
            return to != null ? to : new Heading(0.0D, 0.0D, 1.0D);
        }
        Heading to = desired.normalized();
        if (to == null) {
            return from;
        }
        double cos = Math.max(-1.0D, Math.min(1.0D, from.dot(to)));
        double angle = Math.acos(cos);
        double maxRadians = Math.toRadians(Math.max(0.0D, maxDegrees));
        if (angle <= maxRadians) {
            return to;
        }
        double sin = Math.sin(angle);
        if (sin < 1.0E-6D) {
            // Antiparallel: no unique plane, rotate toward any perpendicular. / 反向：没有唯一平面，朝任一垂直方向转动。
            Heading axis = Math.abs(from.y()) < 0.9D ? new Heading(0.0D, 1.0D, 0.0D) : new Heading(1.0D, 0.0D, 0.0D);
            Heading perpendicular = from.cross(axis).normalized();
            return from.scale(Math.cos(maxRadians)).add(perpendicular.scale(Math.sin(maxRadians))).normalized();
        }
        double fromWeight = Math.sin(angle - maxRadians) / sin;
        double toWeight = Math.sin(maxRadians) / sin;
        Heading result = from.scale(fromWeight).add(to.scale(toWeight)).normalized();
        return result != null ? result : to;
    }

    /** Minimal 3D vector so these rules need no Minecraft math types. / 最小三维向量，使本规则类无需 Minecraft 数学类型。 */
    public record Heading(double x, double y, double z) {
        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public double dot(Heading other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public Heading add(Heading other) {
            return new Heading(x + other.x, y + other.y, z + other.z);
        }

        public Heading scale(double factor) {
            return new Heading(x * factor, y * factor, z * factor);
        }

        public Heading cross(Heading other) {
            return new Heading(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }

        /** Unit vector, or null for a (near) zero vector. / 单位向量；（近似）零向量时返回 null。 */
        public @Nullable Heading normalized() {
            double length = length();
            return length < 1.0E-9D ? null : scale(1.0D / length);
        }
    }
}
