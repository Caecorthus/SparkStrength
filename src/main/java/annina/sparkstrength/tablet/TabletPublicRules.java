package annina.sparkstrength.tablet;

import org.jetbrains.annotations.Nullable;

import java.util.random.RandomGenerator;

/**
 * Pure reception rules of the public proximity channel ({@link TabletChannel#PUBLIC}), measured from the sender's
 * position to each receiver's at send time.
 * 公共近距离频道的纯接收规则，以发送瞬间发送者到每名接收者的距离计算。
 *
 * <ul>
 *   <li>Up to {@link #CLEAR_RANGE} blocks: the sender's name and avatar, the message intact. /
 *   {@link #CLEAR_RANGE} 格以内：显示发送者名字与头像，消息完整。</li>
 *   <li>Up to {@link #MAX_RANGE} blocks: the sender is hidden ("???", no UUID on the wire) and more of the message turns
 *   to static the farther it travels. / {@link #MAX_RANGE} 格以内：隐藏发送者（显示 ???，线上不带 UUID），距离越远消息中被
 *   噪声替换的字越多。</li>
 *   <li>Beyond that the message is not received at all. / 超出范围则完全收不到。</li>
 * </ul>
 *
 * <p>Static is decided on the server per receiver, so the hidden characters never reach that client; the client only
 * draws each {@link #STATIC_CHAR} as flickering noise.
 * 噪声由服务端按接收者决定，被替换的字绝不会发到该客户端；客户端只把每个 STATIC_CHAR 画成闪烁的噪点。</p>
 */
public final class TabletPublicRules {
    public static final double CLEAR_RANGE = 12.0;
    public static final double MAX_RANGE = 24.0;
    /** Below this distance a garbled message still shows two signal bars. / 小于此距离时，乱码消息仍显示两格信号。 */
    public static final double WEAK_RANGE = 18.0;
    /** Share of characters replaced at {@link #MAX_RANGE}. / 在最大距离处被替换的字符比例。 */
    public static final double MAX_STATIC_SHARE = 0.85;
    /** Curve of the static share between the clear and max ranges (>1 keeps the start readable). / 噪声比例曲线指数。 */
    public static final double STATIC_CURVE = 1.5;
    /** Placeholder for a character lost to static (U+2592 MEDIUM SHADE). / 被噪声吞掉的字符占位符。 */
    public static final char STATIC_CHAR = '▒';

    public static final int SIGNAL_NONE = 0;
    public static final int SIGNAL_WEAK = 1;
    public static final int SIGNAL_FAIR = 2;
    public static final int SIGNAL_FULL = 3;

    private TabletPublicRules() {
    }

    public static boolean receives(double distance) {
        return distance >= 0 && distance <= MAX_RANGE;
    }

    /** Whether the receiver sees who sent it. / 接收者能否看到发送者。 */
    public static boolean revealsSender(double distance) {
        return distance >= 0 && distance <= CLEAR_RANGE;
    }

    /** Signal bars shown next to the message; {@link #SIGNAL_NONE} when it is not received. / 消息旁显示的信号格数。 */
    public static int signal(double distance) {
        if (!receives(distance)) {
            return SIGNAL_NONE;
        }
        if (distance <= CLEAR_RANGE) {
            return SIGNAL_FULL;
        }
        return distance <= WEAK_RANGE ? SIGNAL_FAIR : SIGNAL_WEAK;
    }

    /** Probability that one character is lost to static. / 单个字符被噪声替换的概率。 */
    public static double staticShare(double distance) {
        if (distance <= CLEAR_RANGE) {
            return 0.0;
        }
        double t = Math.min(1.0, (distance - CLEAR_RANGE) / (MAX_RANGE - CLEAR_RANGE));
        return MAX_STATIC_SHARE * Math.pow(t, STATIC_CURVE);
    }

    /**
     * The message as this receiver gets it: whitespace survives, every other code point independently turns into one
     * {@link #STATIC_CHAR} with {@link #staticShare} probability. Unchanged inside the clear range.
     * 该接收者收到的消息：空白保留，其余每个码点各自以 staticShare 的概率变成一个 STATIC_CHAR；清晰范围内不变。
     */
    public static String garble(@Nullable String message, double distance, RandomGenerator random) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        double share = staticShare(distance);
        if (share <= 0.0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length());
        for (int index = 0; index < message.length(); ) {
            int codePoint = message.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || random.nextDouble() >= share) {
                out.appendCodePoint(codePoint);
            } else {
                out.append(STATIC_CHAR);
            }
        }
        return out.toString();
    }
}
