package annina.sparkstrength.role.toxicologist;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToxicologistCapsuleRulesTest {
    @Test
    void capsuleStaysScopedToToxicologist() {
        assertEquals("sparkstrength_capsule", ToxicologistCapsuleRules.CAPSULE_ENTRY_ID);
        assertEquals(100, ToxicologistCapsuleRules.CAPSULE_PRICE);
        assertTrue(ToxicologistCapsuleRules.canBuyCapsules(role("toxicologist")));
        assertFalse(ToxicologistCapsuleRules.canBuyCapsules(role("detective")));
        assertFalse(ToxicologistCapsuleRules.canBuyCapsules(role("attendant")));
        assertFalse(ToxicologistCapsuleRules.canBuyCapsules(null));
    }

    @Test
    void poisonNameColorsMatchNormalBlueAndMixedStates() {
        assertEquals(0x1E5014, ToxicologistCapsuleRules.poisonNameColor(true, false));
        assertEquals(0x00BFFF, ToxicologistCapsuleRules.poisonNameColor(false, true));
        assertEquals(0x0F8789, ToxicologistCapsuleRules.poisonNameColor(true, true));
    }

    private static Role role(String path) {
        return new Role(
                Identifier.of("noellesroles", path),
                0xFFFFFF,
                false,
                false,
                Role.MoodType.FAKE,
                -1,
                true
        );
    }
}
