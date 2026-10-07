package annina.sparkstrength.client;

import annina.sparkstrength.client.item.CapsuleClient;
import annina.sparkstrength.client.item.FlashlightModelClient;
import annina.sparkstrength.client.item.M67Client;
import annina.sparkstrength.client.item.PerfumerItemsClient;
import annina.sparkstrength.client.role.attendant.flashlight.FlashlightLights;
import annina.sparkstrength.client.role.attendant.flashlight.FlashlightRenderer;
import annina.sparkstrength.client.role.bodyguard.BodyguardClient;
import annina.sparkstrength.client.role.bomber.BomberDroneClient;
import annina.sparkstrength.client.role.coroner.CoronerClientHooks;
import annina.sparkstrength.client.role.corruptcop.CorruptCopClientHooks;
import annina.sparkstrength.client.role.corruptcop.CorruptCopMusicController;
import annina.sparkstrength.client.role.demonhunter.DemonHunterSniffClientHooks;
import annina.sparkstrength.client.role.economy.RoleEconomyClientHooks;
import annina.sparkstrength.client.role.economy.KillerTeamEconomyClientHooks;
import annina.sparkstrength.client.role.engineer.EngineerClientHooks;
import annina.sparkstrength.client.role.jester.JesterMomentClientHooks;
import annina.sparkstrength.client.role.morphling.MorphlingClientHooks;
import annina.sparkstrength.client.role.pathogen.PathogenClientHooks;
import annina.sparkstrength.client.role.perfumer.PerfumerClientHooks;
import annina.sparkstrength.client.role.professor.ProfessorSerumClientHooks;
import annina.sparkstrength.client.role.taotie.TaotieHeadClientHooks;
import annina.sparkstrength.client.role.veteran.VeteranClientHooks;
import annina.sparkstrength.client.role.vulture.SkateboardClient;
import annina.sparkstrength.client.role.waiter.WaiterTaskRevealClientHooks;
import annina.sparkstrength.client.role.timekeeper.TimekeeperWatchClientHooks;
import annina.sparkstrength.client.role.shadowjester.ShadowJesterShowdownMusicController;
import annina.sparkstrength.client.screen.detective.DetectiveFolderScreen;
import annina.sparkstrength.client.screen.tablet.TabletClientState;
import annina.sparkstrength.client.screen.tablet.TabletScreen;
import annina.sparkstrength.client.tablet.TabletClientHighlights;
import annina.sparkstrength.network.detective.OpenDetectiveFolderS2CPacket;
import annina.sparkstrength.network.economy.SyncKillerTeamEconomyS2CPacket;
import annina.sparkstrength.network.tablet.OpenTabletScreenS2CPacket;
import annina.sparkstrength.network.tablet.SyncTabletSnapshotS2CPacket;
import annina.sparkstrength.network.shadowjester.SyncShadowJesterShowdownMusicS2CPacket;
import annina.sparkstrength.network.veteran.SyncVeteranBlackoutS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.timekeeper.TimekeeperWatchComponent;
import annina.sparkstrength.item.TimekeeperWatchItem;
import annina.sparkstrength.role.timekeeper.TimekeeperConstants;
import annina.sparkstrength.role.timekeeper.TimekeeperWatchMode;

public final class SparkStrengthClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BodyguardClient.register();
        CapsuleClient.register();
        FlashlightModelClient.register();
        M67Client.initialize();
        PerfumerItemsClient.register();
        BomberDroneClient.initialize();
        CoronerClientHooks.register();
        CorruptCopClientHooks.register();
        DemonHunterSniffClientHooks.register();
        EngineerClientHooks.register();
        FlashlightLights.register();
        FlashlightRenderer.register();
        JesterMomentClientHooks.register();
        MorphlingClientHooks.register();
        PathogenClientHooks.register();
        PerfumerClientHooks.register();
        ProfessorSerumClientHooks.register();
        RoleEconomyClientHooks.register();
        SkateboardClient.register();
        TaotieHeadClientHooks.register();
        TabletClientHighlights.register();
        VeteranClientHooks.register();
        WaiterTaskRevealClientHooks.register();
        ClientTickEvents.END_CLIENT_TICK.register(CorruptCopMusicController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                KillerTeamEconomyClientHooks.tick(client.world));
        ClientTickEvents.END_CLIENT_TICK.register(ShadowJesterShowdownMusicController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(TimekeeperWatchClientHooks::tick);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!stack.isOf(SparkStrengthItems.dyingWatch())) {
                return;
            }

            TimekeeperWatchMode mode = TimekeeperWatchItem.getMode(stack);
            int cost = mode == TimekeeperWatchMode.ITEM_REFRESH
                    ? TimekeeperConstants.ITEM_REFRESH_COST_COINS
                    : TimekeeperConstants.ABILITY_REFRESH_COST_COINS;
            int cooldownTicks = 0;
            if (MinecraftClient.getInstance().player != null) {
                var watch = TimekeeperWatchComponent.KEY.get(MinecraftClient.getInstance().player);
                cooldownTicks = mode == TimekeeperWatchMode.ITEM_REFRESH
                        ? watch.getItemRefreshCooldownTicks()
                        : watch.getAbilityRefreshCooldownTicks();
            }
            Text cooldown = cooldownTicks > 0
                    ? Text.translatable("item.sparkstrength.dying_watch.cooldown", (cooldownTicks + 19) / 20)
                    : Text.translatable("item.sparkstrength.dying_watch.cooldown.ready");
            var style = net.minecraft.text.Style.EMPTY.withColor(0xADD8E6).withItalic(false);
            int insertAt = Math.min(1, lines.size());
            lines.add(insertAt, Text.translatable("item.sparkstrength.dying_watch.cost", cost).setStyle(style));
            lines.add(insertAt + 1, cooldown.copy().setStyle(style));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VeteranClientHooks.resetBlackoutState();
            KillerTeamEconomyClientHooks.reset();
            ShadowJesterShowdownMusicController.reset();
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
        ClientPlayNetworking.registerGlobalReceiver(
                SyncShadowJesterShowdownMusicS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        ShadowJesterShowdownMusicController.setServerRequestedActive(payload.active()))
        );
    }
}
