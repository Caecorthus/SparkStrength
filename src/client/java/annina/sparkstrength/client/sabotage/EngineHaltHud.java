package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.sabotage.EngineHalt;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * "⏸ Halted" right of Wathe's round timer while the timer is frozen (engine wrecked). Drawn from inside Wathe's
 * {@code TimeRenderer.renderHud}, so it shows exactly to those who see the timer (killers, Timekeeper, Discovery
 * civilians, spectators) and shares its origin: the screen centre, 6 px from the top. Same font scale as the digits,
 * muted amber so it does not compete with them.
 * 回合倒计时冻结（动力损毁）期间，在 Wathe 回合倒计时右侧显示「⏸ 停驶」。在 Wathe 的 TimeRenderer.renderHud 内部绘制，
 * 所以只有看得到倒计时的人（杀手、计时员、Discovery 平民、旁观者）能看到，原点也和它相同：屏幕水平中央、距顶部 6 像素。
 * 字号与数字相同，用暗琥珀色，不与数字抢眼。
 */
public final class EngineHaltHud {
    /**
     * Wathe draws the seconds' ones digit at x = 16 of the timer's origin; a digit is 5 px plus 1 px of shadow.
     * Wathe 把秒的个位画在倒计时原点的 x = 16 处；一个数字宽 5 像素，另加 1 像素阴影。
     */
    private static final int TIMER_RIGHT = 22;
    private static final int GAP = 6;
    private static final int COLOR = 0xFFD8A24A;

    private EngineHaltHud() {
    }

    /** Called with the timer's matrix still pushed (origin at the timer). / 调用时倒计时的矩阵仍在栈上（原点即倒计时）。 */
    public static void renderBesideTimer(TextRenderer renderer, ClientPlayerEntity player, DrawContext context) {
        if (player == null || !EngineHalt.freezesRoundTimer(player.getWorld())) {
            return;
        }
        context.drawTextWithShadow(renderer, Text.translatable("hud.sparkstrength.engine.halted"), TIMER_RIGHT + GAP,
                0, COLOR);
    }
}
