package annina.sparkstrength.role.veteran;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeteranRulesTest {
    private static final Role VETERAN = role("wathe", "veteran", 0x4A7023, true, false);
    private static final Role CIVILIAN = role("wathe", "civilian", 0xFFFFFF, true, false);
    private static final Role KILLER = role("wathe", "killer", 0xCC0000, false, true);
    private static final Role NEUTRAL = role("noellesroles", "vulture", 0xB56700, false, false);

    @Test
    void veteranKnifeUsesAndShopConstantsMatchDesign() {
        assertEquals("sparkstrength_veteran_knife", VeteranRules.KNIFE_ENTRY_ID);
        assertEquals(200, VeteranRules.KNIFE_PRICE);
        assertEquals(2, VeteranRules.STAB_USES_PER_KNIFE);
        assertTrue(VeteranRules.shouldRemoveKnifeAfterUse(0));
        assertFalse(VeteranRules.shouldRemoveKnifeAfterUse(1));
        assertTrue(VeteranRules.shouldRemoveKnifeAfterUse(2));
    }

    @Test
    void veteranKnifeRewardsGoodAndNonGoodFactionsDifferently() {
        assertEquals(25, VeteranRules.killRewardForVictim(CIVILIAN));
        assertEquals(100, VeteranRules.killRewardForVictim(KILLER));
        assertEquals(100, VeteranRules.killRewardForVictim(NEUTRAL));
        assertEquals(100, VeteranRules.killRewardForVictim(null));
    }

    @Test
    void blackoutHighlightAlwaysShowsNearbyOtherPlayersWithVeteranColor() {
        GetInstinctHighlight.HighlightResult result = VeteranRules.blackoutHighlight(
                VETERAN,
                true,
                true,
                false,
                false,
                true,
                false,
                VeteranRules.BLACKOUT_HIGHLIGHT_RANGE_SQUARED
        );

        assertEquals(VETERAN.color(), result.color());
        assertFalse(result.requiresKeybind());
        assertEquals(85, result.priority());
    }

    @Test
    void blackoutHighlightRejectsInvalidViewerTargetOrRange() {
        assertNull(VeteranRules.blackoutHighlight(CIVILIAN, true, true, false, false, true, false, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(VETERAN, false, true, false, false, true, false, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(VETERAN, true, false, false, false, true, false, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(VETERAN, true, true, true, false, true, false, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(VETERAN, true, true, false, true, true, false, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(VETERAN, true, true, false, false, false, false, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(VETERAN, true, true, false, false, true, true, 1.0D));
        assertNull(VeteranRules.blackoutHighlight(
                VETERAN,
                true,
                true,
                false,
                false,
                true,
                false,
                VeteranRules.BLACKOUT_HIGHLIGHT_RANGE_SQUARED + 0.1D
        ));
    }

    private static Role role(String namespace, String path, int color, boolean innocent, boolean killer) {
        return new Role(
                Identifier.of(namespace, path),
                color,
                innocent,
                killer,
                Role.MoodType.FAKE,
                -1,
                true
        );
    }
}
