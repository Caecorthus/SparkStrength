package annina.sparkstrength.role.detective;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriminologistRulesTest {
    @Test
    void criminologistUsesRequestedCostsAndCooldowns() {
        assertEquals(150, CriminologistRules.COST);
        assertEquals(GameConstants.getInTicks(1, 0), CriminologistRules.INITIAL_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(2, 0), CriminologistRules.COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(0, 30), CriminologistRules.REVEAL_INTERVAL_TICKS);
        assertEquals(GameConstants.getInTicks(0, 5), CriminologistRules.REVEAL_TICKS);
        assertEquals(0xFF3030, CriminologistRules.HIGHLIGHT_COLOR);
    }

    @Test
    void detectiveRoleIdIsScopedToNoellesRolesDetective() {
        assertTrue(CriminologistRules.isDetective(role("detective")));
        assertFalse(CriminologistRules.isDetective(role("toxicologist")));
        assertFalse(CriminologistRules.isDetective(null));
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
