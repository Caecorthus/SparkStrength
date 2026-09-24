package annina.sparkstrength.client.role.economy;

import dev.doctor4t.wathe.client.gui.StoreRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the server-authorized team-wallet snapshot and its independent HUD animation.
 * 保存服务端授权的团队钱包快照，并维护独立的 HUD 动画状态。
 */
public final class KillerTeamEconomyClientHooks {
    private static StoreRenderer.MoneyNumberRenderer view = createRenderer();
    private static float offsetDelta;
    private static boolean visible;
    private static int balance;
    @Nullable
    private static ClientWorld snapshotWorld;

    private KillerTeamEconomyClientHooks() {
    }

    /**
     * Binds each authorized snapshot to the world active when it is handled on the client thread.
     * 每个授权快照绑定到客户端主线程处理时的世界，避免跨世界沿用旧余额。
     */
    public static void applySnapshot(@Nullable ClientWorld world, boolean canView, int serverBalance) {
        if (!canView || world == null) {
            reset();
            return;
        }

        if (snapshotWorld != world) {
            resetVisualState();
        }
        snapshotWorld = world;
        visible = true;
        setBalance(serverBalance);
    }

    public static void tick(@Nullable ClientWorld currentWorld) {
        if (!visible) {
            return;
        }
        if (snapshotWorld != currentWorld) {
            reset();
            return;
        }
        view.update();
    }

    public static void render(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            float delta
    ) {
        if (!visible) {
            return;
        }
        if (snapshotWorld != player.getWorld()) {
            reset();
            return;
        }

        float pulse = 1.0f - Math.abs(offsetDelta) * 0.25f;
        int colour = MathHelper.packRgb(pulse, pulse / 3.0f, pulse / 3.0f) | 0xFF000000;
        context.getMatrices().push();
        context.getMatrices().translate(
                context.getScaledWindowWidth() - 12,
                teamRowY(renderer.fontHeight),
                0
        );
        view.render(renderer, context, 0, 0, colour, delta);
        context.getMatrices().pop();
        offsetDelta = MathHelper.lerp(delta / 16.0f, offsetDelta, 0.0f);
    }

    /**
     * Revocation, round cleanup, disconnect, and world replacement all discard the cached snapshot.
     * 权限撤销、回合清理、断线和世界切换都会丢弃缓存快照。
     */
    public static void reset() {
        visible = false;
        balance = 0;
        snapshotWorld = null;
        resetVisualState();
    }

    private static void setBalance(int newBalance) {
        if (balance != newBalance || view.getTarget() != newBalance) {
            offsetDelta = newBalance >= balance ? 0.6f : -0.6f;
            balance = newBalance;
            view.setTarget(newBalance);
        }
    }

    private static void resetVisualState() {
        view = createRenderer();
        offsetDelta = 0.0f;
        balance = 0;
    }

    private static StoreRenderer.MoneyNumberRenderer createRenderer() {
        StoreRenderer.MoneyNumberRenderer renderer = new StoreRenderer.MoneyNumberRenderer();
        renderer.setTarget(0.0f);
        return renderer;
    }

    static int teamRowY(int fontHeight) {
        // Each Wathe digit can roll one step above and nearly two font heights below its anchor.
        // Wathe 数字会滚到锚点上方一格、下方近两行，因此按完整动画范围留出间距。
        int rollingStep = fontHeight + 2;
        int animationGap = 2;
        return 6 + 2 * rollingStep + fontHeight + animationGap;
    }
}
