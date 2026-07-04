package annina.sparkstrength.role.toxicologist;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Capsule shop and poison-display rules for the NoellesRoles Toxicologist.
 * NoellesRoles 毒理学家的胶囊商店和毒物显示规则。
 */
public final class ToxicologistCapsuleRules {
    public static final Identifier TOXICOLOGIST_ID = Identifier.of("noellesroles", "toxicologist");
    public static final String CAPSULE_ENTRY_ID = "sparkstrength_capsule";
    public static final int CAPSULE_PRICE = 100;

    public static final int NORMAL_POISON_COLOR = 0x1E5014;
    public static final int BLUE_POISON_COLOR = 0x00BFFF;

    /**
     * SparkTraits 的蓝毒组件是可选兼容点：这里只保存组件 ID。
     * 真正读取前会先问注册表是否存在，没装 SparkTraits 时不会形成启动依赖。
     */
    public static final Identifier BLUE_POISON_COMPONENT_ID = Identifier.of("sparktraits", "conscience_poisoner");

    private ToxicologistCapsuleRules() {
    }

    public static boolean canBuyCapsules(@Nullable Role role) {
        return isToxicologist(role);
    }

    public static boolean isToxicologist(@Nullable Role role) {
        return role != null && TOXICOLOGIST_ID.equals(role.identifier());
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
