package annina.sparkstrength.tablet;

import dev.doctor4t.wathe.game.GameConstants;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure constants and calculations for the tablet network.
 * 平板网络使用的纯常量和计算逻辑。
 */
public final class TabletRules {
    public static final int HOTBAR_START_SLOT = 0;
    public static final int HOTBAR_END_SLOT = 8;
    public static final int CHAT_MESSAGE_MAX_LENGTH = 120;
    public static final int DEFAULT_EMERGENCY_MEETING_CHANCES = 1;
    public static final int DEFAULT_MEETING_DURATION_TICKS = GameConstants.getInTicks(1, 40);
    public static final int MEETING_DURATION_TICKS = DEFAULT_MEETING_DURATION_TICKS;
    public static final int MEETING_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int SUSPECT_REVEAL_INTERVAL_TICKS = GameConstants.getInTicks(0, 45);
    public static final int SUSPECT_REVEAL_TICKS = GameConstants.getInTicks(0, 5);
    public static final int SYNC_INTERVAL_TICKS = 20;
    public static final int SNAPSHOT_REQUEST_INTERVAL_TICKS = 10;
    private static final char SECTION_SIGN = '\u00A7';
    private static final String FORMATTING_CODES = "0123456789abcdefklmnor";

    private TabletRules() {
    }

    public static boolean meetsTwoThirds(int approvals, int electorate) {
        return electorate > 0 && approvals > 0 && approvals * 3 >= electorate * 2;
    }

    public static int requiredTwoThirds(int electorate) {
        if (electorate <= 0) {
            return 0;
        }
        return (int) Math.ceil(electorate * 2 / 3.0);
    }

    public static int secondsCeil(int ticks) {
        return (int) Math.ceil(Math.max(0, ticks) / 20.0);
    }

    public static int ticksFromSeconds(int seconds) {
        return GameConstants.getInTicks(seconds / 60, seconds % 60);
    }

    /**
     * Server-side chat cleanup: drops section-sign formatting codes and control characters, trims, then caps length.
     * Crafted packets are the only way to deliver these characters, so nothing a normal client types is lost.
     * 服务端聊天清洗：去除 § 格式代码与控制字符，去除首尾空白后截断；只有伪造数据包才会携带这些字符。
     */
    public static String sanitizeChatMessage(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); ) {
            int codePoint = raw.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == SECTION_SIGN) {
                if (index < raw.length() && FORMATTING_CODES.indexOf(Character.toLowerCase(raw.charAt(index))) >= 0) {
                    index++;
                }
                continue;
            }
            if (!Character.isISOControl(codePoint)) {
                cleaned.appendCodePoint(codePoint);
            }
        }
        String message = cleaned.toString().trim();
        if (message.length() > CHAT_MESSAGE_MAX_LENGTH) {
            int end = CHAT_MESSAGE_MAX_LENGTH;
            if (Character.isHighSurrogate(message.charAt(end - 1))) {
                end--;
            }
            message = message.substring(0, end).trim();
        }
        return message;
    }

    public static Optional<UUID> uniqueHighestVote(Map<UUID, Integer> voteCounts) {
        UUID winner = null;
        int best = 0;
        boolean tied = false;
        for (Map.Entry<UUID, Integer> entry : voteCounts.entrySet()) {
            int votes = entry.getValue();
            if (votes <= 0) {
                continue;
            }
            if (votes > best) {
                winner = entry.getKey();
                best = votes;
                tied = false;
            } else if (votes == best) {
                tied = true;
            }
        }
        return winner == null || tied ? Optional.empty() : Optional.of(winner);
    }
}
