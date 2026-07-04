package annina.sparkstrength.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/**
 * Pure numbers and predicates for SparkStrength's NoellesRoles enhancements.
 * SparkStrength 给 NoellesRoles 角色追加机制时使用的纯规则和数值。
 */
public final class NoellesRoleEnhancementRules {
    public static final int INITIAL_GOOD_ROLE_MONEY = 0;
    public static final int TASK_MONEY_REWARD = 50;

    public static final int CRIMINOLOGIST_COST = 150;
    public static final int CRIMINOLOGIST_INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int CRIMINOLOGIST_COOLDOWN_TICKS = GameConstants.getInTicks(2, 0);
    public static final int CRIMINOLOGIST_REVEAL_INTERVAL_TICKS = GameConstants.getInTicks(0, 30);
    public static final int CRIMINOLOGIST_REVEAL_TICKS = GameConstants.getInTicks(0, 5);
    public static final int CRIMINOLOGIST_HIGHLIGHT_COLOR = 0xFF3030;

    public static final String CAPSULE_ENTRY_ID = "sparkstrength_capsule";
    public static final int CAPSULE_PRICE = 100;
    public static final String TABLET_ENTRY_ID = "sparkstrength_tablet";
    public static final int TABLET_PRICE = 150;
    public static final int TABLET_HIGHLIGHT_COLOR = 0x1B8AE5;
    public static final int SUSPECT_HIGHLIGHT_COLOR = 0xFF8C00;
    public static final int NORMAL_POISON_COLOR = 0x1E5014;
    public static final int BLUE_POISON_COLOR = 0x00BFFF;
    public static final Identifier BLUE_POISON_COMPONENT_ID = Identifier.of("sparktraits", "conscience_poisoner");

    private NoellesRoleEnhancementRules() {
    }

    public static boolean isGoodMoneyRole(Role role) {
        return NoellesRoleIds.isEnhancedMoneyRole(role);
    }

    public static boolean shouldInitializeGoodMoney(Role role) {
        return isGoodMoneyRole(role);
    }

    public static boolean earnsTaskMoney(Role role) {
        return isGoodMoneyRole(role);
    }

    public static boolean canBuyCapsules(Role role) {
        return NoellesRoleIds.isToxicologist(role);
    }

    public static boolean canBuyTabletRole(Role role) {
        return NoellesRoleIds.isTabletEconomyRole(role);
    }

    public static boolean startsWithFlashlight(Role role) {
        return NoellesRoleIds.isAttendant(role);
    }

    public static int poisonNameColor(boolean normalPoisoned, boolean bluePoisoned) {
        if (normalPoisoned && bluePoisoned) {
            return mixColors(NORMAL_POISON_COLOR, BLUE_POISON_COLOR);
        }
        return bluePoisoned ? BLUE_POISON_COLOR : NORMAL_POISON_COLOR;
    }

    private static int mixColors(int left, int right) {
        int red = (((left >> 16) & 0xFF) + ((right >> 16) & 0xFF)) / 2;
        int green = (((left >> 8) & 0xFF) + ((right >> 8) & 0xFF)) / 2;
        int blue = ((left & 0xFF) + (right & 0xFF)) / 2;
        return (red << 16) | (green << 8) | blue;
    }
}
