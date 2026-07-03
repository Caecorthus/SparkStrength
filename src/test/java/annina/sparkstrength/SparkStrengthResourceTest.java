package annina.sparkstrength;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkStrengthResourceTest {
    @Test
    void fabricMetadataDeclaresMigratedSupport() throws IOException {
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));

        assertTrue(metadata.contains("\"sparkstrength:role_enhancements\""));
        assertTrue(metadata.contains("\"sparkstrength:role_enhancement_world\""));
        assertTrue(metadata.contains("\"cardinal-components-world\""));
        assertTrue(metadata.contains("\"sparkfactionapi\""));
        assertTrue(metadata.contains("\"lambdynlights:initializer\""));
        assertTrue(metadata.contains("annina.sparkstrength.client.role.FlashlightDynamicLightsInitializer"));
    }

    @Test
    void clientMixinRegistersCriminologistHud() throws IOException {
        String mixin = Files.readString(Path.of("src/client/resources/sparkstrength.client.mixins.json"));

        assertTrue(mixin.contains("\"role.CriminologistHudMixin\""));
    }

    @Test
    void flashlightModelUsesSparkStrengthOnModel() throws IOException {
        String model = Files.readString(Path.of("src/main/resources/assets/sparkstrength/models/item/flashlight.json"));

        assertTrue(model.contains("\"sparkstrength:item/flashlight_on\""));
        assertFalse(model.contains("sparkwitch"));
    }

    @Test
    void localizationContainsMigratedKeys() throws IOException {
        String english = Files.readString(Path.of("src/main/resources/assets/sparkstrength/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/sparkstrength/lang/zh_cn.json"));

        for (String key : new String[]{
                "item.sparkstrength.capsule",
                "item.sparkstrength.flashlight",
                "shop.sparkstrength.capsule",
                "hud.sparkstrength.criminologist.ready",
                "screen.sparkstrength.criminologist.title",
                "message.sparkstrength.criminologist.correct",
                "message.sparkstrength.flashlight.on"
        }) {
            assertTrue(english.contains(key), key);
            assertTrue(chinese.contains(key), key);
        }
    }
}
