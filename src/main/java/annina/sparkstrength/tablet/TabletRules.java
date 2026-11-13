package annina.sparkstrength.tablet;

import dev.doctor4t.wathe.game.GameConstants;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
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
    // State-changing tablet packets per player (see acceptAction): one every 2 ticks (10 per second), bursts of 20.
    // 每名玩家改变状态的平板数据包（见 acceptAction）：每 2 tick 一个（每秒 10 个），可突发 20 个。
    public static final int ACTION_REFILL_TICKS = 2;
    public static final int ACTION_BURST = 20;
    private static final long ACTION_WINDOW_TICKS = (long) ACTION_REFILL_TICKS * ACTION_BURST;
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

    /**
     * Snapshot form of a countdown (v10): the world tick at which {@code remainingTicks} run out, 0 when none is
     * running. It stays the same while the countdown runs, so the snapshot only changes on real state changes; the
     * client derives the seconds ({@code TabletSnapshot#secondsUntil}).
     * 倒计时在快照中的形式（v10）：剩余 remainingTicks 用完时的世界 tick，没有倒计时时为 0。倒计时进行期间它保持不变，因此快照只在
     * 真实状态变化时改变；秒数由客户端计算（TabletSnapshot#secondsUntil）。
     */
    public static long deadlineTick(long now, int remainingTicks) {
        return remainingTicks > 0 ? now + remainingTicks : 0L;
    }

    public static int ticksFromSeconds(int seconds) {
        return GameConstants.getInTicks(seconds / 60, seconds % 60);
    }

    /**
     * Rate gate for state-changing tablet packets (chat, vote, confirm, removal approval, channel switch), each of which
     * rebuilds and pushes snapshots: a token bucket of {@link #ACTION_BURST} refilled one per
     * {@link #ACTION_REFILL_TICKS}, far above what anyone clicking or typing reaches, so it only ever stops a modified
     * client sending in a loop. {@code budgetTick} is the tick at which the player's budget is full again
     * ({@link Long#MIN_VALUE} when never used); returns the new value when accepted, empty when the packet must be
     * dropped. A value too far ahead (impossible with world time) restarts from a full budget.
     * 改变状态的平板数据包（聊天、投票、确认、移除赞成、切换频道）的限流闸门，每个都会重建并推送快照：容量为 ACTION_BURST、
     * 每 ACTION_REFILL_TICKS 恢复一个的令牌桶，远高于任何人点击或打字能达到的速度，因此只会拦下循环发包的改版客户端。
     * budgetTick 是该玩家预算重新满额的 tick（从未使用时为 Long.MIN_VALUE）；接受时返回新值，应丢弃时返回空。
     * 超前过多的值（世界时间下不可能出现）按满额预算重新开始。
     */
    public static OptionalLong acceptAction(long budgetTick, long currentTick) {
        long start = budgetTick < currentTick || budgetTick - currentTick > ACTION_WINDOW_TICKS ? currentTick : budgetTick;
        long next = start + ACTION_REFILL_TICKS;
        return next - currentTick > ACTION_WINDOW_TICKS ? OptionalLong.empty() : OptionalLong.of(next);
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
