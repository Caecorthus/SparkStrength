package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.tablet.TabletChannel;
import org.jetbrains.annotations.Nullable;

/**
 * Colour tokens of the "Nightline" tablet UI. Pure client presentation; nothing here reaches the server.
 * “Nightline” 平板界面的颜色令牌。纯客户端表现层，不会影响服务端。
 *
 * <p>All colours are non-premultiplied ARGB ints.
 * 所有颜色均为非预乘 ARGB 整数。</p>
 */
public final class TabletTheme {
    public static final int BACKDROP = 0x9A05070A;
    public static final int DEVICE = 0xFF0B0D12;
    public static final int DEVICE_RIM_TOP = 0xFF3A4150;
    public static final int CAMERA = 0xFF1A1E27;
    public static final int CAMERA_HIGHLIGHT = 0xFF2B3342;
    public static final int SCREEN_TOP = 0xFF131722;
    public static final int SCREEN_BOTTOM = 0xFF0B0D12;
    public static final int RAIL_TINT = 0x40000000;

    public static final int SURFACE = 0xFF181C25;
    public static final int SURFACE_HOVER = 0xFF20252F;
    public static final int SURFACE_RAISED = 0xFF1C212B;
    public static final int SURFACE_SUNKEN = 0xFF101319;
    public static final int LOCKED_BADGE = 0xFF262B35;
    public static final int HAIRLINE = 0x17FFFFFF;
    public static final int FAINT = 0x14FFFFFF;

    public static final int TEXT = 0xFFEEF1F6;
    public static final int TEXT_2 = 0xFF9CA4B2;
    public static final int TEXT_3 = 0xFF626A79;

    public static final int SUCCESS = 0xFF3DD68C;
    public static final int WARNING = 0xFFF5A524;
    public static final int DANGER = 0xFFF2555A;
    public static final int WARNING_INK = 0xFF1A1206;
    public static final int WHITE = 0xFFFFFFFF;
    // Stand-in disc behind "?" for anonymous senders/actors. 匿名发送者/操作者“?”头像的底色圆盘。
    public static final int ANONYMOUS_DISC = 0xFF2A303B;

    public record Accent(int base, int pale, int deep) {
    }

    public static final Accent POLICE = new Accent(0xFF4C9BFF, 0xFFB6D6FF, 0xFF2E6BD6);
    public static final Accent KILLER = new Accent(0xFFF2555A, 0xFFFFB9BB, 0xFFBF363C);
    public static final Accent WITCH = new Accent(0xFFB07CFF, 0xFFDDC9FF, 0xFF8550E0);
    public static final Accent NONE = new Accent(0xFF7A8394, 0xFFBCC3CF, 0xFF4F5766);
    /**
     * Door monitor (Attendant door log): surveillance cyan, kept apart from the channel hues and from the
     * success/warning/danger colours the log rows use. Frames a holder whose only content is the door log.
     * 房门监控（乘务员房门记录）：监控青色，与各频道色及记录行使用的成功/警告/危险色区分。用于只有房门记录的持有者的外框。
     */
    public static final Accent MONITOR = new Accent(0xFF2EC4C9, 0xFFA9ECEE, 0xFF1C8C93);

    private TabletTheme() {
    }

    public static Accent accentFor(@Nullable TabletChannel channel) {
        if (channel == null) {
            return NONE;
        }
        return switch (channel) {
            case POLICE -> POLICE;
            case KILLER -> KILLER;
            case WITCH -> WITCH;
        };
    }

    /** Replaces the alpha byte (clamped to 0..255). 替换 alpha 字节（限制在 0..255）。 */
    public static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Scales the existing alpha by {@code factor} (clamped to 0..1). 按系数缩放现有 alpha。 */
    public static int multiplyAlpha(int color, float factor) {
        if (factor >= 1f) {
            return color;
        }
        if (!(factor > 0f)) {
            return color & 0x00FFFFFF;
        }
        int a = Math.round((color >>> 24) * factor);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Per-channel ARGB lerp; {@code t} is clamped to 0..1. 按通道线性插值 ARGB，t 限制在 0..1。 */
    public static int mix(int a, int b, float t) {
        if (!(t > 0f)) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int aa = a >>> 24;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
