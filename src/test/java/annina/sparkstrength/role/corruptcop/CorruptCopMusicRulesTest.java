package annina.sparkstrength.role.corruptcop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorruptCopMusicRulesTest {
    @Test
    void pausedTrackIsRetainedThroughTickOneHundredNinetyNine() {
        assertTrue(CorruptCopMusicRules.shouldRetainPausedTrack(199));
        assertFalse(CorruptCopMusicRules.shouldDiscardPausedTrack(199));
    }

    @Test
    void pausedTrackIsDiscardedAtTickTwoHundred() {
        assertFalse(CorruptCopMusicRules.shouldRetainPausedTrack(200));
        assertTrue(CorruptCopMusicRules.shouldDiscardPausedTrack(200));
    }

    @Test
    void remainingSecondsUseCeilingDivision() {
        assertEquals(10, CorruptCopMusicRules.remainingResumeSeconds(0));
        assertEquals(10, CorruptCopMusicRules.remainingResumeSeconds(1));
        assertEquals(9, CorruptCopMusicRules.remainingResumeSeconds(20));
        assertEquals(1, CorruptCopMusicRules.remainingResumeSeconds(199));
        assertEquals(0, CorruptCopMusicRules.remainingResumeSeconds(200));
    }
}
