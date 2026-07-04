package annina.sparkstrength.client;

import annina.sparkstrength.client.role.NoellesRoleEnhancementClientHooks;
import annina.sparkstrength.client.screen.criminologist.CriminologistScreen;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletScreen;
import annina.sparkstrength.client.tablet.TabletClientHighlights;
import annina.sparkstrength.network.criminologist.OpenCriminologistScreenS2CPacket;
import annina.sparkstrength.network.tablet.OpenTabletScreenS2CPacket;
import annina.sparkstrength.network.tablet.SyncTabletSnapshotS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SparkStrengthClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NoellesRoleEnhancementClientHooks.register();
        TabletClientHighlights.register();

        ClientPlayNetworking.registerGlobalReceiver(OpenCriminologistScreenS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new CriminologistScreen(payload.victimUuid()))));
        ClientPlayNetworking.registerGlobalReceiver(OpenTabletScreenS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new TabletScreen())));
        ClientPlayNetworking.registerGlobalReceiver(SyncTabletSnapshotS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    TabletClientState.apply(payload.snapshot());
                    if (context.client().currentScreen instanceof TabletScreen tabletScreen) {
                        tabletScreen.handleSnapshotUpdate();
                    }
                }));
    }
}
