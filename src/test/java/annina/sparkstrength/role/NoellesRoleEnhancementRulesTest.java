package annina.sparkstrength.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoellesRoleEnhancementRulesTest {
    private static final Role DETECTIVE = role("detective");
    private static final Role TOXICOLOGIST = role("toxicologist");
    private static final Role ATTENDANT = role("attendant");
    private static final Role CORRUPT_COP = role("corrupt_cop");
    private static final Role VIGILANTE = new Role(
            Identifier.of("wathe", "vigilante"),
            0x1B8AE5,
            true,
            false,
            Role.MoodType.REAL,
            -1,
            false
    );
    private static final Role REPORTER = role("reporter");
    private static final Role WAITER = role("waiter");
    private static final Role BARTENDER = role("bartender");
    private static final Role RECALLER = role("recaller");
    private static final Role TIMEKEEPER = role("timekeeper");
    private static final Role PIG_GOD = new Role(
            Identifier.of("sparkwitch", "pig_god"),
            0xFFB6C1,
            false,
            false,
            Role.MoodType.FAKE,
            -1,
            true
    );

    @Test
    void criminologistUsesRequestedCostsAndCooldowns() {
        assertEquals(150, NoellesRoleEnhancementRules.CRIMINOLOGIST_COST);
        assertEquals(GameConstants.getInTicks(1, 0), NoellesRoleEnhancementRules.CRIMINOLOGIST_INITIAL_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(2, 0), NoellesRoleEnhancementRules.CRIMINOLOGIST_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(0, 30), NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_INTERVAL_TICKS);
        assertEquals(GameConstants.getInTicks(0, 5), NoellesRoleEnhancementRules.CRIMINOLOGIST_REVEAL_TICKS);
    }

    @Test
    void enhancedMoneyRolesStartAtZeroAndEarnTaskMoney() {
        assertEquals(0, NoellesRoleEnhancementRules.INITIAL_GOOD_ROLE_MONEY);
        assertEquals(50, NoellesRoleEnhancementRules.TASK_MONEY_REWARD);
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(DETECTIVE));
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(TOXICOLOGIST));
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(VIGILANTE));
        assertTrue(NoellesRoleEnhancementRules.isGoodMoneyRole(CORRUPT_COP));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(PIG_GOD));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(REPORTER));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(WAITER));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(BARTENDER));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(RECALLER));
        assertFalse(NoellesRoleEnhancementRules.isGoodMoneyRole(TIMEKEEPER));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(DETECTIVE));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(TOXICOLOGIST));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(VIGILANTE));
        assertTrue(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(CORRUPT_COP));
        assertFalse(NoellesRoleEnhancementRules.shouldInitializeGoodMoney(PIG_GOD));
        assertFalse(NoellesRoleEnhancementRules.earnsTaskMoney(REPORTER));
        assertFalse(NoellesRoleEnhancementRules.earnsTaskMoney(WAITER));
        assertFalse(NoellesRoleEnhancementRules.earnsTaskMoney(BARTENDER));
        assertFalse(NoellesRoleEnhancementRules.earnsTaskMoney(RECALLER));
        assertFalse(NoellesRoleEnhancementRules.earnsTaskMoney(TIMEKEEPER));
    }

    @Test
    void enhancedMoneyRolesExposeMoneyToServerHooks() {
        assertEquals(CanSeeMoney.Result.ALLOW, NoellesRoleEnhancementService.moneyVisibilityResult(DETECTIVE));
        assertEquals(CanSeeMoney.Result.ALLOW, NoellesRoleEnhancementService.moneyVisibilityResult(TOXICOLOGIST));
        assertEquals(CanSeeMoney.Result.ALLOW, NoellesRoleEnhancementService.moneyVisibilityResult(VIGILANTE));
        assertEquals(CanSeeMoney.Result.ALLOW, NoellesRoleEnhancementService.moneyVisibilityResult(CORRUPT_COP));
        assertNull(NoellesRoleEnhancementService.moneyVisibilityResult(PIG_GOD));
        assertNull(NoellesRoleEnhancementService.moneyVisibilityResult(REPORTER));
        assertNull(NoellesRoleEnhancementService.moneyVisibilityResult(null));
    }

    @Test
    void capsuleStaysScopedToToxicologist() {
        assertEquals("sparkstrength_capsule", NoellesRoleEnhancementRules.CAPSULE_ENTRY_ID);
        assertEquals(100, NoellesRoleEnhancementRules.CAPSULE_PRICE);
        assertTrue(NoellesRoleEnhancementRules.canBuyCapsules(TOXICOLOGIST));
        assertFalse(NoellesRoleEnhancementRules.canBuyCapsules(DETECTIVE));
        assertFalse(NoellesRoleEnhancementRules.canBuyCapsules(ATTENDANT));
        assertFalse(NoellesRoleEnhancementRules.canBuyCapsules(REPORTER));
    }

    @Test
    void tabletShopStaysScopedToVigilanteAndCorruptCopRoles() {
        assertEquals("sparkstrength_tablet", NoellesRoleEnhancementRules.TABLET_ENTRY_ID);
        assertEquals(150, NoellesRoleEnhancementRules.TABLET_PRICE);
        assertEquals(0x1B8AE5, NoellesRoleEnhancementRules.TABLET_HIGHLIGHT_COLOR);
        assertEquals(0xFF8C00, NoellesRoleEnhancementRules.SUSPECT_HIGHLIGHT_COLOR);
        assertTrue(NoellesRoleEnhancementRules.canBuyTabletRole(VIGILANTE));
        assertTrue(NoellesRoleEnhancementRules.canBuyTabletRole(CORRUPT_COP));
        assertFalse(NoellesRoleEnhancementRules.canBuyTabletRole(DETECTIVE));
        assertFalse(NoellesRoleEnhancementRules.canBuyTabletRole(TOXICOLOGIST));
        assertFalse(NoellesRoleEnhancementRules.canBuyTabletRole(ATTENDANT));
        assertFalse(NoellesRoleEnhancementRules.canBuyTabletRole(REPORTER));
    }

    @Test
    void attendantStartsWithExactlyOneFlashlight() {
        assertTrue(NoellesRoleEnhancementRules.startsWithFlashlight(ATTENDANT));
        assertFalse(NoellesRoleEnhancementRules.startsWithFlashlight(DETECTIVE));
        assertFalse(NoellesRoleEnhancementRules.startsWithFlashlight(TOXICOLOGIST));
        assertFalse(NoellesRoleEnhancementRules.startsWithFlashlight(REPORTER));

        assertTrue(NoellesRoleEnhancementService.shouldGiveAttendantFlashlight(ATTENDANT, false));
        assertFalse(NoellesRoleEnhancementService.shouldGiveAttendantFlashlight(ATTENDANT, true));
        assertFalse(NoellesRoleEnhancementService.shouldGiveAttendantFlashlight(REPORTER, false));
    }

    @Test
    void poisonNameColorsMatchNormalBlueAndMixedStates() {
        assertEquals(0x1E5014, NoellesRoleEnhancementRules.poisonNameColor(true, false));
        assertEquals(0x00BFFF, NoellesRoleEnhancementRules.poisonNameColor(false, true));
        assertEquals(0x0F8789, NoellesRoleEnhancementRules.poisonNameColor(true, true));
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
