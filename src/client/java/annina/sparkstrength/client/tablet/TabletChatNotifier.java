package annina.sparkstrength.client.tablet;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletScreen;
import annina.sparkstrength.network.tablet.TabletChatNotifyS2CPacket;
import annina.sparkstrength.tablet.TabletChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * New-message feedback for the local tablet holder: a short buzz and the held tablet's unread screen
 * ({@code models/item/tablet_unread}). Both are local only: the buzz is a UI sound nobody else hears, and the model
 * predicate answers only for the local player's own tablet, so a teammate's tablet in someone else's hand never lights
 * up and nobody learns that this player got a message.
 * 本地平板持有者的新消息反馈：短促振动与手中平板的未读屏幕。两者都只在本地：振动是别人听不到的界面音效，模型谓词只对本地玩家
 * 自己的平板生效，因此别人手里的平板不会亮起，也没人会知道这名玩家收到了消息。
 *
 * <p>No buzz when that channel's chat is already on screen (the message is simply shown), and at most one buzz per
 * {@link #BUZZ_GAP_MS} so a burst of messages does not rattle continuously.
 * 若该频道的聊天已显示在屏幕上则不振动（消息直接显示）；每 BUZZ_GAP_MS 最多振动一次，连发的消息不会持续嗡嗡作响。</p>
 */
public final class TabletChatNotifier {
    public static final Identifier UNREAD_PREDICATE = SparkStrength.id("unread");
    private static final long BUZZ_GAP_MS = 1200L;
    private static final float BUZZ_VOLUME = 0.8F;
    private static long lastBuzzMs = Long.MIN_VALUE / 2;

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
        long now = Util.getMeasuringTimeMs();
        if (now - lastBuzzMs < BUZZ_GAP_MS) {
            return;
        }
        lastBuzzMs = now;
        client.getSoundManager().play(PositionedSoundInstance.master(SparkStrengthSounds.TABLET_BUZZ, 1.0F, BUZZ_VOLUME));
    }
}
