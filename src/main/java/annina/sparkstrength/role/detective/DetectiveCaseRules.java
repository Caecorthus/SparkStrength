package annina.sparkstrength.role.detective;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pure rules for the Detective magnifier and case folder.
 * 侦探放大镜与文件夹的纯规则。
 *
 * <p>Only {@code java.*} and {@code org.jetbrains.annotations.Nullable} may be imported here so the unit test can
 * compile this file with plain javac (no Minecraft, Wathe or Gradle).
 * 这里只能依赖 java.* 与 Nullable 注解，测试可以直接用 javac 编译，不需要 Minecraft/Wathe/Gradle。</p>
 */
public final class DetectiveCaseRules {
    public static final int MIN_SUSPECT_LIMIT = 1;
    public static final int MAX_SUSPECT_LIMIT = 4;
    public static final int DEFAULT_SUSPECT_LIMIT = 3;
    // 15 s before SparkTraits Fast Hands; always applied as a server-side vanilla item cooldown.
    // 15 秒（快手特质生效前）；只在服务端作为原版物品冷却设置。
    public static final int MAGNIFIER_COOLDOWN_TICKS = 300;
    public static final int MAX_CASES = 32;
    public static final int MAX_SNAPSHOTS = 256;
    public static final int NOTES_MAX_LENGTH = 256;
    public static final int NOTES_MAX_LINES = 12;
    public static final int NOTES_WIRE_MAX_CHARS = 1024;
    public static final int NAME_MAX_LENGTH = 32;
    private static final char SECTION_SIGN = '§';

    public enum InvestigationOutcome {
        NO_CASE,
        LIMIT_REACHED,
        ALREADY_RECORDED,
        NOT_PRESENT,
        RECORDED
    }

    private DetectiveCaseRules() {
    }

    public static int clampSuspectLimit(int requested) {
        return Math.max(MIN_SUSPECT_LIMIT, Math.min(MAX_SUSPECT_LIMIT, requested));
    }

    /**
     * Rounds a 3D distance half-up to whole blocks. NaN and negative distances read as 0; infinity clamps to int max.
     * 将三维距离四舍五入为整数格；NaN/负数视为 0，无穷大截断为 int 最大值。
     */
    public static int roundDistanceBlocks(double distance) {
        if (Double.isNaN(distance) || distance <= 0.0D) {
            return 0;
        }
        long rounded = Math.round(distance);
        return rounded >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rounded;
    }

    /**
     * Precedence is fixed: NO_CASE, LIMIT_REACHED, ALREADY_RECORDED, NOT_PRESENT, then RECORDED.
     * The limit is clamped to 1..4 so a corrupted setting can never make the check unreachable.
     * 判定顺序固定；上限会先夹到 1..4，损坏的设置也不会让上限检查失效。
     */
    public static InvestigationOutcome decideInvestigation(boolean hasSelectedCase, int recordedSuspects, int limit,
                                                           boolean alreadyRecordedPair, boolean targetHadPosition) {
        if (!hasSelectedCase) {
            return InvestigationOutcome.NO_CASE;
        }
        if (recordedSuspects >= clampSuspectLimit(limit)) {
            return InvestigationOutcome.LIMIT_REACHED;
        }
        if (alreadyRecordedPair) {
            return InvestigationOutcome.ALREADY_RECORDED;
        }
        if (!targetHadPosition) {
            return InvestigationOutcome.NOT_PRESENT;
        }
        return InvestigationOutcome.RECORDED;
    }

    /**
     * Duplicate-clue key is only what the detective SAW, never the hidden real identity: a named clue duplicates a
     * stored named clue with the same display uuid, and anonymous clues never duplicate. Keying on the real uuid would
     * make the free ALREADY_RECORDED answer tell a disguised player apart from the person they look like.
     * 去重只看侦探“看到的身份”，绝不看隐藏的真实身份：具名线索与显示 UUID 相同的已存具名线索重复，匿名线索从不重复。
     * 若按真实 UUID 去重，免费的“已记录”回应会把伪装者与其外观对应的本人区分开。
     */
    public static boolean isDuplicateClue(boolean storedAnonymous, @Nullable UUID storedDisplayUuid,
                                          boolean anonymous, @Nullable UUID displayUuid) {
        return !anonymous && !storedAnonymous && displayUuid != null && displayUuid.equals(storedDisplayUuid);
    }

    /**
     * Only a recorded clue spends the magnifier; every rejection (and corpse filing) is free.
     * 只有成功记录线索才会进入放大镜冷却；所有拒绝结果（以及记录尸体）都不消耗冷却。
     */
    public static boolean startsCooldown(InvestigationOutcome outcome) {
        return outcome == InvestigationOutcome.RECORDED;
    }

    /**
     * Server-authoritative notes cleanup. Steps: null to empty; CRLF and CR to LF; drop a section sign together with
     * the code point after it; drop ISO control characters except LF (and unpaired surrogates); keep at most
     * {@link #NOTES_MAX_LINES} lines; cap at {@link #NOTES_MAX_LENGTH} chars without splitting a surrogate pair;
     * strip trailing whitespace. The result is a fixed point: sanitizing it again returns the same string.
     * 服务端权威的笔记清洗：换行统一为 \n；去除 § 及其后一个字符；去除除 \n 外的控制字符与孤立代理项；
     * 最多保留 12 行；截断到 256 字符且不拆开代理对；去除末尾空白。清洗结果再次清洗保持不变。
     */
    public static String sanitizeNotes(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(Math.min(text.length(), NOTES_MAX_LENGTH + 1));
        int lines = 1;
        for (int index = 0; index < text.length(); ) {
            if (cleaned.length() > NOTES_MAX_LENGTH) {
                // The cap below only looks at the first NOTES_MAX_LENGTH chars, so the rest cannot matter.
                break;
            }
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == SECTION_SIGN) {
                if (index < text.length()) {
                    index += Character.charCount(text.codePointAt(index));
                }
                continue;
            }
            if (codePoint == '\n') {
                if (lines >= NOTES_MAX_LINES) {
                    break;
                }
                lines++;
                cleaned.append('\n');
                continue;
            }
            if (Character.isISOControl(codePoint) || isUnpairedSurrogate(codePoint)) {
                continue;
            }
            cleaned.appendCodePoint(codePoint);
        }
        return truncate(cleaned.toString(), NOTES_MAX_LENGTH).stripTrailing();
    }

    /**
     * Caps {@code text} at {@code maxChars} UTF-16 units without leaving a dangling high surrogate; null reads as empty.
     * Used for synced names and notes so {@code writeString(value, max)} can never throw.
     * 截断到指定 UTF-16 长度且不留下半个代理对；null 视为空串。同步名字与笔记都经过这里，保证 writeString 不会抛异常。
     */
    public static String truncate(@Nullable String text, int maxChars) {
        if (text == null || maxChars <= 0) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        int end = maxChars;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * Picks the uuid a suspect is DISPLAYED as. Precedence mirrors the client appearance order: NoellesRoles morph,
     * then SparkStrength morph reagent, then Coroner body disguise (checked last on purpose), else the real uuid.
     * The first ACTIVE source wins even if it names the player themself (no fall-through). Returns null only when
     * {@code realUuid} is null and no source applies.
     * 选择嫌疑人“显示成谁”：Noelles 变形 > SparkStrength 试剂 > 验尸官伪装（刻意最后判断）> 真实身份；
     * 第一个生效的来源即决定结果，即使它指向本人也不再向下回退。
     */
    public static @Nullable UUID pickDisplayedUuid(UUID realUuid,
                                                   boolean noellesMorphActive, @Nullable UUID noellesMorphDisguise,
                                                   boolean reagentActive, @Nullable UUID reagentSample,
                                                   @Nullable UUID coronerDisguise) {
        // An active source decides the appearance even when it is the player's own uuid: the client stops at the first
        // active source, so falling through would name a disguise nobody can see.
        // 生效的来源即使指向本人也决定外观：客户端在第一个生效来源处停止，继续向下回退会得出无人可见的伪装。
        if (noellesMorphActive) {
            // NoellesRoles syncs a null disguise as the player themself. NoellesRoles 同步时把空 disguise 当作本人。
            return noellesMorphDisguise != null ? noellesMorphDisguise : realUuid;
        }
        if (reagentActive && reagentSample != null) {
            return reagentSample;
        }
        if (coronerDisguise != null) {
            return coronerDisguise;
        }
        return realUuid;
    }

    private static boolean isUnpairedSurrogate(int codePoint) {
        return codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE;
    }
}
