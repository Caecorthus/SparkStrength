package annina.sparkstrength.role.attendant;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendantRulesTest {
    @Test
    void attendantStartsWithExactlyOneFlashlight() {
        Role attendant = role("attendant");
        Role reporter = role("reporter");

        assertTrue(AttendantRules.startsWithFlashlight(attendant));
        assertFalse(AttendantRules.startsWithFlashlight(reporter));
        assertTrue(AttendantRules.shouldGiveStarterFlashlight(attendant, false));
        assertFalse(AttendantRules.shouldGiveStarterFlashlight(attendant, true));
        assertFalse(AttendantRules.shouldGiveStarterFlashlight(reporter, false));
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
