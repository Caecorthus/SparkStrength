package annina.sparkstrength.role.engineer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EngineerRulesTest {
    @Test
    void captureCandidateMustBeAnotherEligiblePlayerInsideTheRadius() {
        assertTrue(EngineerRules.isCaptureCandidate(
                false,
                true,
                true,
                EngineerRules.CAPTURE_RADIUS_SQUARED
        ));
        assertFalse(EngineerRules.isCaptureCandidate(
                true,
                true,
                true,
                0.0D
        ));
        assertFalse(EngineerRules.isCaptureCandidate(
                false,
                false,
                true,
                0.0D
        ));
        assertFalse(EngineerRules.isCaptureCandidate(
                false,
                true,
                false,
                0.0D
        ));
        assertFalse(EngineerRules.isCaptureCandidate(
                false,
                true,
                true,
                Math.nextUp(EngineerRules.CAPTURE_RADIUS_SQUARED)
        ));
    }

    @Test
    void captureDeviceExpiresBeforeTriggeringAtItsLifetimeBoundary() {
        assertEquals(
                EngineerRules.CaptureTickDecision.WAIT,
                EngineerRules.decideCaptureTick(EngineerRules.CAPTURE_MAX_LIFETIME_TICKS - 1, false)
        );
        assertEquals(
                EngineerRules.CaptureTickDecision.TRIGGER,
                EngineerRules.decideCaptureTick(EngineerRules.CAPTURE_MAX_LIFETIME_TICKS - 1, true)
        );
        assertEquals(
                EngineerRules.CaptureTickDecision.EXPIRE,
                EngineerRules.decideCaptureTick(EngineerRules.CAPTURE_MAX_LIFETIME_TICKS, true)
        );
    }
}
