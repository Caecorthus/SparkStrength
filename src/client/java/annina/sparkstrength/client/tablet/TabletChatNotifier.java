package annina.sparkstrength.client.tablet;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletScreen;
import annina.sparkstrength.network.tablet.TabletChatNotifyS2CPacket;
import annina.sparkstrength.tablet.TabletChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

/**
 * New-message feedback for the local tablet holder: an unread count per network (the switcher rows, the channel chip
 * and the held-tablet HUD, {@link TabletHeldHud}) and the held tablet's unread screen ({@code models/item/tablet_unread}).
 * All of it is local: the model predicate answers only for the local player's own tablet, so a teammate's tablet in
 * someone else's hand never lights up and nobody learns that this player got a message. Silent since 2026-11-16 (the
 * owner found the buzz too loud).
 * 本地平板持有者的新消息反馈：每个网络的未读条数（切换浮层的行、频道胶囊与手持平板 HUD）以及手中平板的未读屏幕。全部只在本地：
 * 模型谓词只对本地玩家自己的平板生效，因此别人手里的平板不会亮起，也没人会知道这名玩家收到了消息。2026-11-16 起不再发声
 * （服主觉得振动声太吵）。
 *
 * <p>Nothing is marked when that channel's chat is already on screen (the message is simply shown).
 * 若该频道的聊天已显示在屏幕上则不标记（消息直接显示）。</p>
 */
public final class TabletChatNotifier {
    public static final Identifier UNREAD_PREDICATE = SparkStrength.id("unread");

    private TabletChatNotifier() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TabletChatNotifyS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> onMessage(context.client(), payload.channelWire())));
        ModelPredicateProviderRegistry.register(SparkStrengthItems.tablet(), UNREAD_PREDICATE,
                (stack, world, entity, seed) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    return entity != null && entity == client.player && TabletClientState.hasAnyUnreadChannel()
                            ? 1.0F : 0.0F;
                });
    }

    private static void onMessage(MinecraftClient client, int channelWire) {
        TabletChannel channel = TabletChannel.fromWire(channelWire);
        if (channel == null || client.player == null) {
            return;
        }
        if (client.currentScreen instanceof TabletScreen screen && screen.showsChatOf(channel)) {
            return;
        }
        TabletClientState.markChannelUnread(channel);
    }
}
