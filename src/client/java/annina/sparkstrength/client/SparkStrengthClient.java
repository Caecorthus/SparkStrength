package annina.sparkstrength.client;

import annina.sparkstrength.client.item.CapsuleClient;
import annina.sparkstrength.client.item.M67Client;
import annina.sparkstrength.client.role.coroner.CoronerClientHooks;
import annina.sparkstrength.client.role.corruptcop.CorruptCopClientHooks;
import annina.sparkstrength.client.role.corruptcop.CorruptCopMusicController;
import annina.sparkstrength.client.role.demonhunter.DemonHunterSniffClientHooks;
import annina.sparkstrength.client.role.economy.RoleEconomyClientHooks;
import annina.sparkstrength.client.role.economy.KillerTeamEconomyClientHooks;
import annina.sparkstrength.client.role.engineer.EngineerClientHooks;
import annina.sparkstrength.client.role.morphling.MorphlingClientHooks;
import annina.sparkstrength.client.role.professor.ProfessorSerumClientHooks;
import annina.sparkstrength.client.role.veteran.VeteranClientHooks;
import annina.sparkstrength.client.screen.detective.DetectiveFolderScreen;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletScreen;
import annina.sparkstrength.client.tablet.TabletClientHighlights;
import annina.sparkstrength.network.detective.OpenDetectiveFolderS2CPacket;
import annina.sparkstrength.network.economy.SyncKillerTeamEconomyS2CPacket;
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
        M67Client.initialize();
        CoronerClientHooks.register();
        CorruptCopClientHooks.register();
        DemonHunterSniffClientHooks.register();
        EngineerClientHooks.register();
        MorphlingClientHooks.register();
        ProfessorSerumClientHooks.register();
        RoleEconomyClientHooks.register();
        TabletClientHighlights.register();
        VeteranClientHooks.register();
        ClientTickEvents.END_CLIENT_TICK.register(CorruptCopMusicController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                KillerTeamEconomyClientHooks.tick(client.world));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VeteranClientHooks.resetBlackoutState();
            KillerTeamEconomyClientHooks.reset();
            // A stale snapshot would otherwise drive tablet outlines on the next server.
            // 否则过期快照会在下一个服务器上继续驱动平板描边。
            TabletClientState.reset();
        });
        // The server syncs the case component before this packet, so the screen reads fresh data on open.
        // 服务端在发送此包前已同步命案组件，界面打开时即可读取最新数据。
        ClientPlayNetworking.registerGlobalReceiver(OpenDetectiveFolderS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new DetectiveFolderScreen())));
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
        ClientPlayNetworking.registerGlobalReceiver(SyncKillerTeamEconomyS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        KillerTeamEconomyClientHooks.applySnapshot(
                                context.client().world,
                                payload.visible(),
                                payload.balance()
                        )));
    }
}
