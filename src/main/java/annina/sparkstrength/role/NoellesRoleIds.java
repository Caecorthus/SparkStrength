package annina.sparkstrength.role;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Weak role-id bridge for NoellesRoles roles that SparkStrength enhances.
 * 通过角色 ID 弱连接 NoellesRoles，避免在编译期直接依赖它的类。
 */
public final class NoellesRoleIds {
    public static final String NAMESPACE = "noellesroles";
    public static final Identifier DETECTIVE = Identifier.of(NAMESPACE, "detective");
    public static final Identifier TOXICOLOGIST = Identifier.of(NAMESPACE, "toxicologist");
    public static final Identifier ATTENDANT = Identifier.of(NAMESPACE, "attendant");

    private NoellesRoleIds() {
    }

    public static boolean isDetective(@Nullable Role role) {
        return hasId(role, DETECTIVE);
    }

    public static boolean isToxicologist(@Nullable Role role) {
        return hasId(role, TOXICOLOGIST);
    }

    public static boolean isAttendant(@Nullable Role role) {
        return hasId(role, ATTENDANT);
    }

    public static boolean isEnhancedMoneyRole(@Nullable Role role) {
        return isDetective(role) || isToxicologist(role);
    }

    public static boolean hasId(@Nullable Role role, Identifier id) {
        return role != null && id.equals(role.identifier());
    }
}
