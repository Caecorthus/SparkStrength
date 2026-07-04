package annina.sparkstrength.role.attendant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashlightBlackoutServiceTest {
    @Test
    void litHeldFlashlightCancelsBlackoutBlindnessAnywhere() {
        assertTrue(FlashlightBlackoutService.shouldCancelBlackoutBlindness(true));
        assertFalse(FlashlightBlackoutService.shouldCancelBlackoutBlindness(false));
    }

    @Test
    void blindnessRemovalIsScopedToCurrentBlackoutRefresh() {
        assertTrue(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(600, 600));
        assertTrue(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(601, 600));
        assertTrue(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(598, 600));
        assertFalse(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(590, 600));
        assertFalse(FlashlightBlackoutService.matchesCurrentBlackoutBlindness(620, 600));
    }

    @Test
    void flashlightToggleClearsOnlyRecentRememberedBlackoutBlindness() {
        assertTrue(FlashlightBlackoutService.shouldClearRememberedBlackoutBlindness(
                true,
                true,
                true,
                600,
                600,
                0
        ));
        assertFalse(FlashlightBlackoutService.shouldClearRememberedBlackoutBlindness(
                false,
                true,
                true,
                600,
                600,
                0
        ));
        assertFalse(FlashlightBlackoutService.shouldClearRememberedBlackoutBlindness(
                true,
                true,
                true,
                400,
                600,
                0
        ));
        assertFalse(FlashlightBlackoutService.shouldClearRememberedBlackoutBlindness(
                true,
                true,
                true,
                600,
                600,
                FlashlightBlackoutService.RECENT_BLACKOUT_RECORD_TOLERANCE_TICKS + 1
        ));
        assertFalse(FlashlightBlackoutService.shouldForgetRememberedBlackoutBlindness(
                FlashlightBlackoutService.RECENT_BLACKOUT_RECORD_TOLERANCE_TICKS
        ));
        assertTrue(FlashlightBlackoutService.shouldForgetRememberedBlackoutBlindness(
                FlashlightBlackoutService.RECENT_BLACKOUT_RECORD_TOLERANCE_TICKS + 1
        ));
    }
}
