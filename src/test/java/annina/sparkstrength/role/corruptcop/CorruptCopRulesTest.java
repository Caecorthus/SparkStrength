package annina.sparkstrength.role.corruptcop;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorruptCopRulesTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void abilityToggleRejectsOtherRolesAndFlipsOnlyForCorruptCop() {
        assertFalse(CorruptCopRules.nextAbilityActive(false, false));
        assertFalse(CorruptCopRules.nextAbilityActive(false, true));
        assertTrue(CorruptCopRules.nextAbilityActive(true, false));
        assertFalse(CorruptCopRules.nextAbilityActive(true, true));
    }

    @Test
    void lateralBonusRequiresAnActiveLivingCorruptCop() {
        Vec3d input = new Vec3d(1.0D, 0.0D, 0.0D);

        assertEquals(Vec3d.ZERO, CorruptCopRules.lateralVelocityBonus(input, 0.1F, 0.0F, false, true, true));
        assertEquals(Vec3d.ZERO, CorruptCopRules.lateralVelocityBonus(input, 0.1F, 0.0F, true, false, true));
        assertEquals(Vec3d.ZERO, CorruptCopRules.lateralVelocityBonus(input, 0.1F, 0.0F, true, true, false));
    }

    @Test
    void pureLateralInputRaisesOnlyItsNormalizedShareToOnePointSevenFiveTimes() {
        float speed = 0.1F;
        Vec3d bonus = CorruptCopRules.lateralVelocityBonus(
                new Vec3d(1.0D, 0.0D, 0.0D),
                speed,
                0.0F,
                true,
                true,
                true
        );

        assertEquals((double) speed * CorruptCopRules.LATERAL_SPEED_MULTIPLIER, speed + bonus.x, EPSILON);
        assertEquals(0.0D, bonus.y, EPSILON);
        assertEquals(0.0D, bonus.z, EPSILON);

        Vec3d oppositeBonus = CorruptCopRules.lateralVelocityBonus(
                new Vec3d(-1.0D, 0.0D, 0.0D),
                speed,
                0.0F,
                true,
                true,
                true
        );
        assertEquals(-(double) speed * CorruptCopRules.LATERAL_SPEED_MULTIPLIER, -speed + oppositeBonus.x, EPSILON);
    }

    @Test
    void diagonalInputBoostsOnlyTheNormalizedLateralShare() {
        float speed = 0.1F;
        double normalizedLateralShare = 1.0D / Math.sqrt(2.0D);
        Vec3d bonus = CorruptCopRules.lateralVelocityBonus(
                new Vec3d(1.0D, 0.0D, 1.0D),
                speed,
                0.0F,
                true,
                true,
                true
        );

        assertEquals(
                normalizedLateralShare * speed * (CorruptCopRules.LATERAL_SPEED_MULTIPLIER - 1.0F),
                bonus.x,
                EPSILON
        );
        assertEquals(0.0D, bonus.y, EPSILON);
        assertEquals(0.0D, bonus.z, EPSILON);
    }

    @Test
    void forwardOnlyInputGetsNoBonus() {
        assertEquals(
                Vec3d.ZERO,
                CorruptCopRules.lateralVelocityBonus(
                        new Vec3d(0.0D, 0.0D, 1.0D),
                        0.1F,
                        0.0F,
                        true,
                        true,
                        true
                )
        );
    }
}
