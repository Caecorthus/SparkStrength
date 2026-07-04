package annina.sparkstrength.tablet;

import dev.doctor4t.wathe.game.GameConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletRulesTest {
    @Test
    void constantsMatchDesign() {
        assertEquals(0, TabletRules.HOTBAR_START_SLOT);
        assertEquals(8, TabletRules.HOTBAR_END_SLOT);
        assertEquals(120, TabletRules.CHAT_MESSAGE_MAX_LENGTH);
        assertEquals(1, TabletRules.DEFAULT_EMERGENCY_MEETING_CHANCES);
        assertEquals(GameConstants.getInTicks(1, 40), TabletRules.DEFAULT_MEETING_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(1, 40), TabletRules.MEETING_DURATION_TICKS);
        assertEquals(GameConstants.getInTicks(1, 0), TabletRules.MEETING_COOLDOWN_TICKS);
        assertEquals(GameConstants.getInTicks(0, 45), TabletRules.SUSPECT_REVEAL_INTERVAL_TICKS);
        assertEquals(GameConstants.getInTicks(0, 5), TabletRules.SUSPECT_REVEAL_TICKS);
    }

    @Test
    void twoThirdsThresholdRoundsUp() {
        assertFalse(TabletRules.meetsTwoThirds(0, 1));
        assertTrue(TabletRules.meetsTwoThirds(1, 1));
        assertFalse(TabletRules.meetsTwoThirds(1, 2));
        assertTrue(TabletRules.meetsTwoThirds(2, 2));
        assertFalse(TabletRules.meetsTwoThirds(1, 3));
        assertTrue(TabletRules.meetsTwoThirds(2, 3));
        assertEquals(0, TabletRules.requiredTwoThirds(0));
        assertEquals(1, TabletRules.requiredTwoThirds(1));
        assertEquals(2, TabletRules.requiredTwoThirds(2));
        assertEquals(2, TabletRules.requiredTwoThirds(3));
        assertEquals(3, TabletRules.requiredTwoThirds(4));
    }

    @Test
    void uniqueHighestVoteRejectsEmptyAndTies() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(TabletRules.uniqueHighestVote(Map.of()).isEmpty());
        assertTrue(TabletRules.uniqueHighestVote(Map.of(a, 0)).isEmpty());
        assertTrue(TabletRules.uniqueHighestVote(Map.of(a, 2, b, 2)).isEmpty());
        assertEquals(a, TabletRules.uniqueHighestVote(Map.of(a, 3, b, 1)).orElseThrow());
    }

    @Test
    void secondsRoundUp() {
        assertEquals(0, TabletRules.secondsCeil(0));
        assertEquals(1, TabletRules.secondsCeil(1));
        assertEquals(1, TabletRules.secondsCeil(20));
        assertEquals(2, TabletRules.secondsCeil(21));
        assertEquals(GameConstants.getInTicks(0, 95), TabletRules.ticksFromSeconds(95));
    }
}
