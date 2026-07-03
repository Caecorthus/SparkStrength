package annina.sparkstrength.client;

import annina.sparkstrength.client.role.NoellesRoleEnhancementClientHooks;
import annina.sparkstrength.client.screen.criminologist.CriminologistScreen;
import annina.sparkstrength.network.criminologist.OpenCriminologistScreenS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SparkStrengthClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NoellesRoleEnhancementClientHooks.register();

        ClientPlayNetworking.registerGlobalReceiver(OpenCriminologistScreenS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new CriminologistScreen(payload.victimUuid()))));
    }
}
