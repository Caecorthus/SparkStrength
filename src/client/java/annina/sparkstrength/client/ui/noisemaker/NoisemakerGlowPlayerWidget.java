package annina.sparkstrength.client.ui.noisemaker;

import annina.sparkstrength.client.ui.common.InventoryWidgetPaint;
import annina.sparkstrength.client.ui.common.PlayerHeadTextureHelper;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowUserComponent;
import annina.sparkstrength.network.noisemaker.NoisemakerGlowC2SPacket;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 大嗓门背包里的玩家头像按钮。
 *
 * <p>按钮本身不判断目标是否存活，点击后始终把目标 UUID 发给服务端。
 * 服务端会先扣冷却再判断目标状态，这样客户端无法用按钮反馈来试探存活信息。</p>
 */
public class NoisemakerGlowPlayerWidget extends ButtonWidget {
    private final UUID targetUuid;
    @Nullable
    private final PlayerListEntry targetPlayerEntry;

    public NoisemakerGlowPlayerWidget(
            int x,
            int y,
            UUID targetUuid,
            @Nullable PlayerListEntry targetPlayerEntry
    ) {
        super(x, y, 16, 16, Text.empty(), button ->
                ClientPlayNetworking.send(new NoisemakerGlowC2SPacket(targetUuid)), DEFAULT_NARRATION_SUPPLIER);
        this.targetUuid = targetUuid;
        this.targetPlayerEntry = targetPlayerEntry;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        ClientPlayerEntity localPlayer = MinecraftClient.getInstance().player;
        int cooldownTicks = localPlayer != null
                ? NoisemakerGlowUserComponent.KEY.get(localPlayer).getCooldownTicks()
                : 0;
        boolean onCooldown = cooldownTicks > 0;

        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        if (onCooldown) {
            context.setShaderColor(0.35f, 0.35f, 0.35f, 0.75f);
        }
        PlayerSkinDrawer.draw(context,
                PlayerHeadTextureHelper.resolveOnlineOrDefaultSkinTextures(targetUuid, targetPlayerEntry).texture(),
                this.getX(), this.getY(), 16);
        context.setShaderColor(1f, 1f, 1f, 1f);

        if (onCooldown) {
            InventoryWidgetPaint.cooldownSeconds(context, this.getX(), this.getY(), cooldownTicks);
        }

        if (this.isHovered()) {
            InventoryWidgetPaint.highlight(context, this.getX(), this.getY(), 0x90FFBF49);
            Text name = targetPlayerEntry != null
                    ? Text.literal(targetPlayerEntry.getProfile().getName())
                    : Text.translatable("ui.sparkstrength.noisemaker.unknown_player");
            context.drawTooltip(
                    MinecraftClient.getInstance().textRenderer,
                    name,
                    this.getX() - 4 - MinecraftClient.getInstance().textRenderer.getWidth(name) / 2,
                    this.getY() - 9
            );
        }
    }

    @Override
    public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
        // 头像本身就是按钮内容，不渲染文字，避免盖住玩家皮肤。
    }
}
