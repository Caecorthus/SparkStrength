package annina.sparkstrength.client.ui.common;

/**
 * ARGB helpers and the WCAG "readable role colour" search shared by the detective folder and the assassin guess
 * screen; each screen passes its own minimum contrast. Alpha is masked to 8 bits (not clamped) and mixes are opaque
 * RGB, unlike TabletTheme's clamping/alpha-interpolating helpers, so the two sets are not interchangeable.
 * 侦探文件夹与刺客猜测界面共用的 ARGB 工具和 WCAG“可读身份颜色”搜索；最低对比度由各界面自行传入。
 * 透明度按 8 位掩码截取（不做钳制），混色结果为不透明 RGB，与 TabletTheme 的钳制/插值透明度版本不同，二者不可互换。
 */
public final class UiColors {
    private static final int CONTRAST_STEPS = 20;

    private UiColors() {
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int mix(int from, int to, float amount) {
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Role colour made readable on {@code background}: kept as-is at WCAG contrast >= {@code minContrast}, otherwise
     * mixed toward white (toward black on a light background) in small steps, keeping the hue, until it reaches it.
     * 让身份颜色在给定底色上可读：WCAG 对比度不低于 minContrast 时原样返回，否则逐步向白色混合（浅色底则向黑色），保持色相直到达标。
     */
    public static int readableOn(int rgb, int background, double minContrast) {
        int base = opaque(rgb);
        if (contrast(base, background) >= minContrast) {
            return base;
        }
        int target = luminance(background) < 0.18D ? 0xFFFFFFFF : 0xFF000000;
        for (int step = 1; step < CONTRAST_STEPS; step++) {
            int candidate = mix(base, target, step / (float) CONTRAST_STEPS);
            if (contrast(candidate, background) >= minContrast) {
                return candidate;
            }
        }
        return target;
    }

    /** WCAG contrast ratio of two opaque colours (1..21). / 两种不透明颜色的 WCAG 对比度（1 到 21）。 */
    public static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05D) / (Math.min(a, b) + 0.05D);
    }

    private static double luminance(int color) {
        return 0.2126D * linear(color >> 16) + 0.7152D * linear(color >> 8) + 0.0722D * linear(color);
    }

    private static double linear(int channel) {
        double value = (channel & 0xFF) / 255.0D;
        return value <= 0.03928D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }
}
