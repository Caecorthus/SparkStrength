package annina.sparkstrength.role.economy;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleEconomyRulesTest {
    private static final Role DETECTIVE = role("noellesroles", "detective");
    private static final Role TOXICOLOGIST = role("noellesroles", "toxicologist");
    private static final Role VIGILANTE = role("wathe", "vigilante");
    private static final Role VETERAN = role("wathe", "veteran");
    private static final Role CORRUPT_COP = role("noellesroles", "corrupt_cop");
    private static final Role REPORTER = role("noellesroles", "reporter");
    private static final Role PIG_GOD = role("sparkwitch", "pig_god");

    @Test
    void enhancedMoneyRolesStartAtZeroAndEarnTaskMoney() {
        assertEquals(0, RoleEconomyRules.INITIAL_GOOD_ROLE_MONEY);
        assertEquals(50, RoleEconomyRules.TASK_MONEY_REWARD);
        assertTrue(RoleEconomyRules.isGoodMoneyRole(DETECTIVE));
        assertTrue(RoleEconomyRules.isGoodMoneyRole(TOXICOLOGIST));
        assertTrue(RoleEconomyRules.isGoodMoneyRole(VIGILANTE));
        assertTrue(RoleEconomyRules.isGoodMoneyRole(VETERAN));
        assertTrue(RoleEconomyRules.isGoodMoneyRole(CORRUPT_COP));
        assertFalse(RoleEconomyRules.isGoodMoneyRole(PIG_GOD));
        assertFalse(RoleEconomyRules.isGoodMoneyRole(REPORTER));
        assertTrue(RoleEconomyRules.shouldInitializeGoodMoney(DETECTIVE));
        assertFalse(RoleEconomyRules.earnsTaskMoney(REPORTER));
    }

    @Test
    void enhancedMoneyRolesExposeMoneyToServerHooks() {
        assertEquals(CanSeeMoney.Result.ALLOW, RoleEconomyService.moneyVisibilityResult(DETECTIVE));
        assertEquals(CanSeeMoney.Result.ALLOW, RoleEconomyService.moneyVisibilityResult(TOXICOLOGIST));
        assertEquals(CanSeeMoney.Result.ALLOW, RoleEconomyService.moneyVisibilityResult(VIGILANTE));
        assertEquals(CanSeeMoney.Result.ALLOW, RoleEconomyService.moneyVisibilityResult(VETERAN));
        assertEquals(CanSeeMoney.Result.ALLOW, RoleEconomyService.moneyVisibilityResult(CORRUPT_COP));
        assertNull(RoleEconomyService.moneyVisibilityResult(PIG_GOD));
        assertNull(RoleEconomyService.moneyVisibilityResult(REPORTER));
        assertNull(RoleEconomyService.moneyVisibilityResult(null));
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
