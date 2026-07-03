package annina.sparkstrength.client.mixin.noisemaker;

import annina.sparkstrength.client.ui.noisemaker.NoisemakerGlowPlayerWidget;
import annina.sparkstrength.client.ui.common.PlayerPageLayout;
import annina.sparkstrength.client.ui.common.PlayerPageSwitchWidget;
import annina.sparkstrength.client.ui.common.PlayerSelectionPageState;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 给 Wathe 的受限背包界面追加大嗓门“点亮玩家”头像栏。
 *
 * <p>这里不修改 NoellesRolesspark 本体，而是在 SparkStrength 里单独混入
 * {@link LimitedInventoryScreen}。所有字段和方法都使用 {@code sparkstrength$noisemaker}
 * 前缀，避免和其它扩展 mod 也混同一个背包界面时发生名字碰撞。</p>
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class NoisemakerInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Shadow @Final public ClientPlayerEntity player;

    @Unique private final List<NoisemakerGlowPlayerWidget> sparkstrength$noisemakerPlayerWidgets = new ArrayList<>();
    @Unique private PlayerPageSwitchWidget sparkstrength$noisemakerPreviousPageWidget;
    @Unique private PlayerPageSwitchWidget sparkstrength$noisemakerNextPageWidget;
    @Unique private int sparkstrength$noisemakerCurrentPage;

    public NoisemakerInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void sparkstrength$noisemakerAddGlowButtons(CallbackInfo ci) {
        if (player == null || player.getWorld() == null) {
            return;
        }

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.getWorld());
        if (!gameWorld.isRole(player, Noellesroles.NOISEMAKER)) {
            return;
        }

        sparkstrength$noisemakerAddPlayerSelectionUI();
    }

    @Unique
    private void sparkstrength$noisemakerAddPlayerSelectionUI() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.networkHandler == null) {
            return;
        }

        // 使用 Tab/网络列表里的 UUID，而不是只读取世界实体。
        // 这样玩家死亡变旁观后头像仍然存在，点击死亡玩家也会被服务端扣冷却。
        List<UUID> playerUuids = new ArrayList<>(client.player.networkHandler.getPlayerUuids());
        int y = PlayerPageLayout.getPlayerRowY(this.height);
        sparkstrength$noisemakerPlayerWidgets.clear();
        sparkstrength$noisemakerCurrentPage = PlayerSelectionPageState.getNoisemakerPage();

        for (UUID targetUuid : playerUuids) {
            PlayerListEntry playerListEntry = client.player.networkHandler.getPlayerListEntry(targetUuid);
            NoisemakerGlowPlayerWidget child = new NoisemakerGlowPlayerWidget(
                    (LimitedInventoryScreen) (Object) this,
                    0,
                    y,
                    targetUuid,
                    playerListEntry
            );
            sparkstrength$noisemakerPlayerWidgets.add(child);
            addDrawableChild(child);
        }

        sparkstrength$noisemakerPreviousPageWidget = addDrawableChild(new PlayerPageSwitchWidget(
                0,
                y,
                Items.PURPLE_DYE.getDefaultStack(),
                Text.translatable("ui.sparkstrength.pagination.previous"),
                button -> {
                    sparkstrength$noisemakerCurrentPage--;
                    sparkstrength$noisemakerRefreshPage();
                }
        ));
        sparkstrength$noisemakerNextPageWidget = addDrawableChild(new PlayerPageSwitchWidget(
                0,
                y,
                Items.LIME_DYE.getDefaultStack(),
                Text.translatable("ui.sparkstrength.pagination.next"),
                button -> {
                    sparkstrength$noisemakerCurrentPage++;
                    sparkstrength$noisemakerRefreshPage();
                }
        ));

        sparkstrength$noisemakerRefreshPage();
    }

    @Unique
    private void sparkstrength$noisemakerRefreshPage() {
        int totalPages = PlayerPageLayout.getTotalPageCount(sparkstrength$noisemakerPlayerWidgets.size());
        if (sparkstrength$noisemakerCurrentPage < 0) {
            sparkstrength$noisemakerCurrentPage = 0;
        }
        if (sparkstrength$noisemakerCurrentPage >= totalPages) {
            sparkstrength$noisemakerCurrentPage = totalPages - 1;
        }
        PlayerSelectionPageState.setNoisemakerPage(sparkstrength$noisemakerCurrentPage);

        int startIndex = sparkstrength$noisemakerCurrentPage * PlayerPageLayout.PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PlayerPageLayout.PLAYERS_PER_PAGE, sparkstrength$noisemakerPlayerWidgets.size());
        int visibleCount = endIndex - startIndex;
        int y = PlayerPageLayout.getPlayerRowY(this.height);
        boolean showPrevious = sparkstrength$noisemakerCurrentPage > 0;
        boolean showNext = sparkstrength$noisemakerCurrentPage < totalPages - 1;
        int groupStartX = PlayerPageLayout.getCenteredGroupStartX(this.width, visibleCount, showPrevious, showNext);
        int playerStartX = groupStartX + (showPrevious ? PlayerPageLayout.SLOT_APART : 0);

        for (int i = 0; i < sparkstrength$noisemakerPlayerWidgets.size(); i++) {
            NoisemakerGlowPlayerWidget widget = sparkstrength$noisemakerPlayerWidgets.get(i);
            boolean visible = i >= startIndex && i < endIndex;
            widget.visible = visible;
            widget.active = visible;
            if (visible) {
                int visibleIndex = i - startIndex;
                widget.setX(playerStartX + visibleIndex * PlayerPageLayout.SLOT_APART);
                widget.setY(y);
            }
        }

        if (sparkstrength$noisemakerPreviousPageWidget != null) {
            sparkstrength$noisemakerPreviousPageWidget.visible = showPrevious;
            sparkstrength$noisemakerPreviousPageWidget.active = showPrevious;
            sparkstrength$noisemakerPreviousPageWidget.setX(groupStartX);
            sparkstrength$noisemakerPreviousPageWidget.setY(y);
        }
        if (sparkstrength$noisemakerNextPageWidget != null) {
            sparkstrength$noisemakerNextPageWidget.visible = showNext;
            sparkstrength$noisemakerNextPageWidget.active = showNext;
            sparkstrength$noisemakerNextPageWidget.setX(playerStartX + visibleCount * PlayerPageLayout.SLOT_APART);
            sparkstrength$noisemakerNextPageWidget.setY(y);
        }
    }
}
