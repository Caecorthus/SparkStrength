package annina.sparkstrength.client.ui.professor;

import annina.sparkstrength.client.ui.common.InventoryWidgetPaint;
import annina.sparkstrength.client.ui.common.PlayerHeadTextureHelper;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 教授背包第一页的玩家头像按钮。
 *
 * <p>按钮只负责选择“准备投喂谁”，不会直接发包；真正投喂要在第二页选择试剂后才提交。
 * 这样服务端能收到完整的目标 UUID + 试剂类型，并在同一个入口里统一校验。</p>
 */
public class ProfessorRemoteFeedPlayerWidget extends ButtonWidget {
    private final UUID targetUuid;
    @Nullable
    private final PlayerListEntry targetPlayerEntry;

    public ProfessorRemoteFeedPlayerWidget(
            int x,
            int y,
            UUID targetUuid,
            @Nullable PlayerListEntry targetPlayerEntry,
            PressAction onPress
    ) {
        super(x, y, 16, 16, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.targetUuid = targetUuid;
        this.targetPlayerEntry = targetPlayerEntry;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), getX() - 7, getY() - 7, 30, 30);
        PlayerSkinDrawer.draw(context,
                PlayerHeadTextureHelper.resolveOnlineOrDefaultSkinTextures(targetUuid, targetPlayerEntry).texture(),
                getX(), getY(), 16);

        if (isHovered()) {
            InventoryWidgetPaint.highlight(context, getX(), getY(), 0x907FD8FF);
            Text name = targetPlayerEntry != null
                    ? Text.literal(targetPlayerEntry.getProfile().getName())
                    : Text.translatable("ui.sparkstrength.professor.unknown_player");
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            context.drawTooltip(
                    textRenderer,
                    name,
                    getX() - 4 - textRenderer.getWidth(name) / 2,
                    getY() - 9
            );
        }
    }

    @Override
    public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
        // 头像就是按钮内容，不额外绘制文字，避免盖住皮肤。
    }
}
