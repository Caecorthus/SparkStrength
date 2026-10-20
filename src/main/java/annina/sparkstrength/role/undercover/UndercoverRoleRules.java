package annina.sparkstrength.role.undercover;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Role-only Undercover check with no other dependencies, for rule classes compiled by the local fixtures
 * (RoleEconomyRules). {@link UndercoverRoles} adds the player-level checks.
 * 只看身份的卧底判断，不依赖其他类，供本地测试编译的规则类（RoleEconomyRules）使用。玩家层面的判断见 UndercoverRoles。
 */
public final class UndercoverRoleRules {
    public static final Identifier UNDERCOVER_ID = Identifier.of("noellesroles", "undercover");

    private UndercoverRoleRules() {
    }

    public static boolean isUndercover(@Nullable Role role) {
        return role != null && UNDERCOVER_ID.equals(role.identifier());
    }
}
