package annina.sparkstrength.role.detective;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable role check for the NoellesRoles detective, shared by the case-folder feature and the economy rules.
 * NoellesRoles 侦探的稳定角色判断，供文件夹功能与经济规则共用。
 *
 * <p>SparkStrength hard-depends on NoellesRoles; matching the role id (instead of the NoellesRoles constant) keeps
 * rule tests free of the NoellesRoles entrypoint.
 * SparkStrength 硬依赖 NoellesRoles；这里仍按角色 ID 判断，使规则测试无需初始化 NoellesRoles 入口类。</p>
 */
public final class DetectiveRules {
    public static final Identifier DETECTIVE_ID = Identifier.of("noellesroles", "detective");

    private DetectiveRules() {
    }

    public static boolean isDetective(@Nullable Role role) {
        return role != null && DETECTIVE_ID.equals(role.identifier());
    }
}
