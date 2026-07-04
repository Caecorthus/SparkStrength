package annina.sparkstrength.role;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashlightLineLightRulesTest {
    @Test
    void flashlightLineLightUsesMoonlightLampTuning() {
        assertEquals(32.0, FlashlightLineLightRules.RANGE_BLOCKS);
        assertEquals(15.0, FlashlightLineLightRules.LUMINANCE);
        assertEquals(15.0, FlashlightLineLightRules.DIRECT_BLOCKER_LUMINANCE);
        assertEquals(0.5, FlashlightLineLightRules.INNER_CONE_RADIANS);
        assertEquals(0.7, FlashlightLineLightRules.OUTER_CONE_RADIANS);
    }

    @Test
    void lineLightOnlyIlluminatesForwardConeWithinRange() {
        double sourceX = 0.5;
        double sourceY = 0.5;
        double sourceZ = 0.5;

        assertTrue(FlashlightLineLightRules.lightAt(sourceX, sourceY, sourceZ, 1.0, 0.0, 0.0, 8, 0, 0) > 0.0);
        assertEquals(0.0, FlashlightLineLightRules.lightAt(sourceX, sourceY, sourceZ, 1.0, 0.0, 0.0, -2, 0, 0));
        assertEquals(0.0, FlashlightLineLightRules.lightAt(sourceX, sourceY, sourceZ, 1.0, 0.0, 0.0, 40, 0, 0));
        assertEquals(0.0, FlashlightLineLightRules.lightAt(sourceX, sourceY, sourceZ, 1.0, 0.0, 0.0, 8, 12, 0));
    }

    @Test
    void cachedHitRangeStopsLightBehindWall() {
        double sourceX = 0.5;
        double sourceY = 0.5;
        double sourceZ = 0.5;
        double effectiveRange = FlashlightLineLightRules.effectiveRangeAfterHit(8.0);

        assertTrue(FlashlightLineLightRules.lightAt(
                sourceX, sourceY, sourceZ,
                1.0, 0.0, 0.0,
                8, 0, 0,
                effectiveRange
        ) > 0.0);
        assertEquals(0.0, FlashlightLineLightRules.lightAt(
                sourceX, sourceY, sourceZ,
                1.0, 0.0, 0.0,
                10, 0, 0,
                effectiveRange
        ));
    }

    @Test
    void directObstacleHitUsesStrongFlashlightBrightnessWithoutLightingBehindWall() {
        double sourceX = 0.5;
        double sourceY = 0.5;
        double sourceZ = 0.5;
        double closeWallRange = FlashlightLineLightRules.effectiveRangeAfterHit(0.1);

        assertEquals(0.0, FlashlightLineLightRules.lightAt(
                sourceX, sourceY, sourceZ,
                1.0, 0.0, 0.0,
                1, 0, 0,
                closeWallRange
        ));
        assertEquals(FlashlightLineLightRules.DIRECT_BLOCKER_LUMINANCE, FlashlightLineLightRules.lightAt(
                sourceX, sourceY, sourceZ,
                1.0, 0.0, 0.0,
                1, 0, 0,
                closeWallRange,
                true
        ));
        assertEquals(0.0, FlashlightLineLightRules.lightAt(
                sourceX, sourceY, sourceZ,
                1.0, 0.0, 0.0,
                2, 0, 0,
                closeWallRange,
                false
        ));
    }

    @Test
    void wallPaddingClampsToValidRange() {
        assertEquals(0.0, FlashlightLineLightRules.effectiveRangeAfterHit(-2.0));
        assertEquals(8.75, FlashlightLineLightRules.effectiveRangeAfterHit(8.0));
        assertEquals(FlashlightLineLightRules.RANGE_BLOCKS,
                FlashlightLineLightRules.effectiveRangeAfterHit(FlashlightLineLightRules.RANGE_BLOCKS + 4.0));
    }
}
