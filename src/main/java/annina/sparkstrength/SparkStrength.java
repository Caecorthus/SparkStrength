package annina.sparkstrength;

import annina.sparkstrength.event.SparkStrengthEvents;
import annina.sparkstrength.network.SparkStrengthPackets;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public final class SparkStrength implements ModInitializer {
    public static final String MOD_ID = "sparkstrength";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        SparkStrengthPackets.registerServer();
        SparkStrengthEvents.register();
        SparkStrengthReplayFormatters.register();
    }
}
