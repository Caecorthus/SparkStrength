package annina.sparkstrength.role.corruptcop;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorruptCopRulesTest {
    private static final int CORRUPT_COP_COLOR = 0x193264;
    private static final Role CORRUPT_COP = new Role(
            Identifier.of("noellesroles", "corrupt_cop"),
            CORRUPT_COP_COLOR,
            false,
            false,
            Role.MoodType.FAKE,
            -1,
            true
    );
    private static final Role VULTURE = new Role(
            Identifier.of("noellesroles", "vulture"),
            0xB56700,
            false,
            false,
            Role.MoodType.FAKE,
            -1,
            false
    );

    @Test
    void corruptCopInstinctHighlightsOtherLivingPlayersWithOwnColor() {
        GetInstinctHighlight.HighlightResult result = CorruptCopRules.instinctHighlight(
                CORRUPT_COP,
                true,
                false,
                false,
                true,
                false,
                false
        );

        assertEquals(CORRUPT_COP_COLOR, result.color());
        assertTrue(result.requiresKeybind());
        assertEquals(90, result.priority());
    }

    @Test
    void corruptCopInstinctPriorityPreservesHardSkips() {
        assertTrue(CorruptCopRules.INSTINCT_PRIORITY > GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT);
        assertTrue(CorruptCopRules.INSTINCT_PRIORITY < GetInstinctHighlight.HighlightResult.PRIORITY_HIGH);
    }

    @Test
    void corruptCopInstinctDoesNotHandleInvalidViewersOrTargets() {
        assertNull(CorruptCopRules.instinctHighlight(WatheRoles.CIVILIAN, true, false, false, true, false, false));
        assertNull(CorruptCopRules.instinctHighlight(CORRUPT_COP, false, false, false, true, false, false));
        assertNull(CorruptCopRules.instinctHighlight(CORRUPT_COP, true, true, false, true, false, false));
        assertNull(CorruptCopRules.instinctHighlight(CORRUPT_COP, true, false, true, true, false, false));
        assertNull(CorruptCopRules.instinctHighlight(CORRUPT_COP, true, false, false, false, false, false));
        assertNull(CorruptCopRules.instinctHighlight(CORRUPT_COP, true, false, false, true, true, false));
        assertNull(CorruptCopRules.instinctHighlight(CORRUPT_COP, true, false, false, true, false, true));
    }

    @Test
    void corruptCopNeutralMasterKeyAllowsTrainDoorAndLockedRoomDoor() {
        assertEquals(
                DoorInteraction.DoorInteractionResult.ALLOW,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );
        assertEquals(
                DoorInteraction.DoorInteractionResult.ALLOW,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.SMALL_DOOR,
                        false,
                        false,
                        false,
                        true,
                        false
                )
        );
    }

    @Test
    void corruptCopNeutralMasterKeyRespectsCooldownAndDoorStateGuards() {
        assertEquals(
                DoorInteraction.DoorInteractionResult.DENY,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        false,
                        false,
                        false,
                        false,
                        true
                )
        );
        assertEquals(
                DoorInteraction.DoorInteractionResult.PASS,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        true,
                        false,
                        false,
                        false,
                        true
                )
        );
        assertEquals(
                DoorInteraction.DoorInteractionResult.PASS,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        false,
                        true,
                        false,
                        false,
                        true
                )
        );
        assertEquals(
                DoorInteraction.DoorInteractionResult.PASS,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        false,
                        false,
                        true,
                        false,
                        true
                )
        );
    }

    @Test
    void corruptCopNeutralMasterKeyLeavesOtherRolesAndItemsAlone() {
        assertEquals(
                DoorInteraction.DoorInteractionResult.PASS,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        VULTURE,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );
        assertEquals(
                DoorInteraction.DoorInteractionResult.PASS,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        Identifier.of("noellesroles", "master_key"),
                        CORRUPT_COP,
                        DoorInteraction.DoorType.TRAIN_DOOR,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );
        assertEquals(
                DoorInteraction.DoorInteractionResult.PASS,
                CorruptCopRules.neutralMasterKeyDoorResult(
                        CorruptCopRules.NEUTRAL_MASTER_KEY_ID,
                        CORRUPT_COP,
                        DoorInteraction.DoorType.SMALL_DOOR,
                        false,
                        false,
                        false,
                        false,
                        false
                )
        );
    }
}
