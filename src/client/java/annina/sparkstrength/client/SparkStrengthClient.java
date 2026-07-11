package annina.sparkstrength.client;

import annina.sparkstrength.client.item.CapsuleClient;
import annina.sparkstrength.client.role.corruptcop.CorruptCopClientHooks;
import annina.sparkstrength.client.role.corruptcop.CorruptCopMusicController;
import annina.sparkstrength.client.role.demonhunter.DemonHunterSniffClientHooks;
import annina.sparkstrength.client.role.detective.CriminologistClientHooks;
import annina.sparkstrength.client.role.economy.RoleEconomyClientHooks;
import annina.sparkstrength.client.role.engineer.EngineerClientHooks;
import annina.sparkstrength.client.role.morphling.MorphlingClientHooks;
import annina.sparkstrength.client.role.professor.ProfessorSerumClientHooks;
import annina.sparkstrength.client.role.veteran.VeteranClientHooks;
import annina.sparkstrength.client.screen.criminologist.CriminologistScreen;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletScreen;
import annina.sparkstrength.client.tablet.TabletClientHighlights;
import annina.sparkstrength.network.criminologist.OpenCriminologistScreenS2CPacket;
import annina.sparkstrength.network.tablet.OpenTabletScreenS2CPacket;
import annina.sparkstrength.network.tablet.SyncTabletSnapshotS2CPacket;
import annina.sparkstrength.network.veteran.SyncVeteranBlackoutS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SparkStrengthClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CapsuleClient.register();
        CorruptCopClientHooks.register();
        CriminologistClientHooks.register();
        DemonHunterSniffClientHooks.register();
        EngineerClientHooks.register();
        MorphlingClientHooks.register();
        ProfessorSerumClientHooks.register();
        RoleEconomyClientHooks.register();
        TabletClientHighlights.register();
        VeteranClientHooks.register();
        ClientTickEvents.END_CLIENT_TICK.register(CorruptCopMusicController::tick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                VeteranClientHooks.resetBlackoutState());
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
        ClientPlayNetworking.registerGlobalReceiver(SyncVeteranBlackoutS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        VeteranClientHooks.setBlackoutActive(payload.active())));
    }
}
