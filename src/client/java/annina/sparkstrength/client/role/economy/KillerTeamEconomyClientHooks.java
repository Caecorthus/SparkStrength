package annina.sparkstrength.client.role.economy;

import dev.doctor4t.wathe.client.gui.StoreRenderer;
import net.minecraft.client.MinecraftClient;
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
    // Mirrors the y anchor hard-coded in Wathe StoreRenderer.renderHud. / 对应 Wathe StoreRenderer.renderHud 中写死的 y 锚点。
    private static final int PERSONAL_ROW_Y = 6;
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
        if (!isShowing(player)) {
            if (visible) {
                reset();
            }
            return;
        }

        float pulse = 1.0f - Math.abs(offsetDelta) * 0.25f;
        int colour = MathHelper.packRgb(pulse, pulse / 3.0f, pulse / 3.0f) | 0xFF000000;
        int rowY = teamRowY(renderer.fontHeight);
        context.getMatrices().push();
        context.getMatrices().translate(context.getScaledWindowWidth() - 12, rowY, 0);
        withRowClip(context, rowY, renderer.fontHeight,
                () -> view.render(renderer, context, 0, 0, colour, delta));
        context.getMatrices().pop();
        offsetDelta = MathHelper.lerp(delta / 16.0f, offsetDelta, 0.0f);
    }

    /**
     * Wathe's personal odometer is clipped only while the team row is stacked directly beneath it.
     * 仅在团队行紧贴其下时裁剪 Wathe 个人余额的滚动数字，其它情况保持 Wathe 原样渲染。
     */
    public static void renderPersonalRow(ClientPlayerEntity player, DrawContext context, int fontHeight, Runnable draw) {
        if (isShowing(player)) {
            withRowClip(context, PERSONAL_ROW_Y, fontHeight, draw);
        } else {
            draw.run();
        }
    }

    /**
     * Public optional seam, reflected by SparkTraits' inventory card: the exclusive bottom y of the rows SparkStrength
     * stacks under Wathe's money, or 0 when none is showing. Client render thread only.
     * 公开可选接缝，供 SparkTraits 背包信息卡反射查询：返回 Wathe 金币下方 SparkStrength 叠加行的底边 y（不含），
     * 未显示时为 0。仅限客户端渲染线程。
     */
    public static int topRightHudBottom() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !isShowing(client.player)) {
            return 0;
        }
        int fontHeight = client.textRenderer.fontHeight;
        return teamRowY(fontHeight) - 1 + rollingStep(fontHeight);
    }

    private static boolean isShowing(ClientPlayerEntity player) {
        return visible && snapshotWorld == player.getWorld();
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
        return PERSONAL_ROW_Y + rollingStep(fontHeight);
    }

    /**
     * Wathe digits roll a full step above and below their anchor, so stacked rows are scissored to
     * their own step-high band instead of being spaced apart by the whole animation range.
     * Wathe 数字会滚出锚点上下各一格；紧贴的两行各自裁剪在一格高的带内，而不是靠空行避让动画。
     */
    static void withRowClip(DrawContext context, int rowY, int fontHeight, Runnable draw) {
        // One pixel of headroom keeps Wathe's 8px-ascent coin glyph; the band ends where the next row begins.
        // 上沿多留 1 像素容纳 Wathe 金币字形（ascent 8），下沿恰好止于下一行起点。
        int top = rowY - 1;
        context.enableScissor(0, top, context.getScaledWindowWidth(), top + rollingStep(fontHeight));
        try {
            draw.run();
        } finally {
            context.disableScissor();
        }
    }

    private static int rollingStep(int fontHeight) {
        return fontHeight + 2;
    }
}
