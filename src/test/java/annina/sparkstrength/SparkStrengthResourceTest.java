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

        assertTrue(metadata.contains("\"sparkstrength:criminologist_player\""));
        assertTrue(metadata.contains("\"sparkstrength:criminologist_world\""));
        assertTrue(metadata.contains("\"sparkstrength:tablet_world\""));
        assertTrue(metadata.contains("\"sparkstrength.mixins.json\""));
        assertTrue(metadata.contains("\"cardinal-components-world\""));
        assertFalse(metadata.contains("\"sparkfactionapi\""));
        assertFalse(metadata.contains("\"sparktraits\""));
        assertTrue(metadata.contains("\"lambdynlights:initializer\""));
        assertTrue(metadata.contains("annina.sparkstrength.client.role.attendant.FlashlightDynamicLightsInitializer"));
    }

    @Test
    void clientMixinRegistersCriminologistHud() throws IOException {
        String mixin = Files.readString(Path.of("src/client/resources/sparkstrength.client.mixins.json"));

        assertTrue(mixin.contains("\"detective.CriminologistHudMixin\""));
    }

    @Test
    void commonMixinRegistersTabletEquipmentHiding() throws IOException {
        String mixin = Files.readString(Path.of("src/main/resources/sparkstrength.mixins.json"));

        assertTrue(mixin.contains("\"noellesroles.HiddenEquipmentHelperMixin\""));
    }

    @Test
    void flashlightModelUsesSparkStrengthOnModel() throws IOException {
        String model = Files.readString(Path.of("src/main/resources/assets/sparkstrength/models/item/flashlight.json"));

        assertTrue(model.contains("\"sparkstrength:item/flashlight_on\""));
        assertFalse(model.contains("sparkwitch"));
    }

    @Test
    void tabletModelExists() throws IOException {
        String model = Files.readString(Path.of("src/main/resources/assets/sparkstrength/models/item/tablet.json"));

        assertTrue(model.contains("\"minecraft:item/filled_map\""));
    }

    @Test
    void localizationContainsMigratedKeys() throws IOException {
        String english = Files.readString(Path.of("src/main/resources/assets/sparkstrength/lang/en_us.json"));
        String chinese = Files.readString(Path.of("src/main/resources/assets/sparkstrength/lang/zh_cn.json"));

        for (String key : new String[]{
                "item.sparkstrength.capsule",
                "item.sparkstrength.flashlight",
                "item.sparkstrength.tablet",
                "shop.sparkstrength.capsule",
                "shop.sparkstrength.tablet",
                "screen.sparkstrength.tablet.title",
                "screen.sparkstrength.tablet.tab.connections",
                "screen.sparkstrength.tablet.tab.chat",
                "screen.sparkstrength.tablet.tab.meeting",
                "screen.sparkstrength.tablet.tab.suspects",
                "screen.sparkstrength.tablet.meeting.chances",
                "message.sparkstrength.tablet.meeting_chances_empty",
                "message.sparkstrength.tablet.meeting_started",
                "message.sparkstrength.tablet.suspect_removed",
                "commands.sparkstrength.emergency_meeting_chances.success",
                "commands.sparkstrength.vote_time.success",
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
