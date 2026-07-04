package annina.sparkstrength.tablet;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletShopRulesTest {
    @Test
    void tabletShopStaysScopedToVigilanteAndCorruptCopRoles() {
        assertEquals("sparkstrength_tablet", TabletShopRules.TABLET_ENTRY_ID);
        assertEquals(150, TabletShopRules.TABLET_PRICE);
        assertEquals(0x1B8AE5, TabletShopRules.TABLET_HIGHLIGHT_COLOR);
        assertEquals(0xFF8C00, TabletShopRules.SUSPECT_HIGHLIGHT_COLOR);
        assertTrue(TabletShopRules.canBuyTabletRole(role("wathe", "vigilante")));
        assertTrue(TabletShopRules.canBuyTabletRole(role("noellesroles", "corrupt_cop")));
        assertFalse(TabletShopRules.canBuyTabletRole(role("noellesroles", "detective")));
        assertFalse(TabletShopRules.canBuyTabletRole(role("noellesroles", "toxicologist")));
        assertFalse(TabletShopRules.canBuyTabletRole(null));
    }

    private static Role role(String namespace, String path) {
        return new Role(
                Identifier.of(namespace, path),
                0xFFFFFF,
                false,
                false,
                Role.MoodType.FAKE,
                -1,
                true
        );
    }
}
