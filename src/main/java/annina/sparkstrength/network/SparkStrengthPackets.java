package annina.sparkstrength.network;

import annina.sparkstrength.network.noisemaker.NoisemakerGlowC2SPacket;
import annina.sparkstrength.noisemaker.NoisemakerGlowService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * SparkStrength 网络包注册。
 */
public final class SparkStrengthPackets {
    private SparkStrengthPackets() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playC2S().register(NoisemakerGlowC2SPacket.ID, NoisemakerGlowC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NoisemakerGlowC2SPacket.ID, (payload, context) ->
                NoisemakerGlowService.tryUseBackpackGlow(context.player(), payload.targetPlayer())
        );
    }
}
