package annina.sparkstrength.role.attendant;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure role predicates for Attendant starter equipment and the tablet door monitor.
 * 乘务员开局装备与平板房门监控的纯规则。
 */
public final class AttendantRules {
    public static final Identifier ATTENDANT_ID = Identifier.of("noellesroles", "attendant");

    private AttendantRules() {
    }

    public static boolean startsWithFlashlight(@Nullable Role role) {
        return role != null && ATTENDANT_ID.equals(role.identifier());
    }

    public static boolean shouldGiveStarterFlashlight(@Nullable Role role, boolean alreadyHasFlashlight) {
        return startsWithFlashlight(role) && !alreadyHasFlashlight;
    }

    public static boolean isAttendant(@Nullable Role role) {
        return role != null && ATTENDANT_ID.equals(role.identifier());
    }

    /**
     * Door-monitor access. {@code realRole} must be the real round role, never a disguise; the only disguise that
     * grants it is a Coroner currently disguised as an Attendant (that disguise is cleared at death).
     * 房门监控权限。realRole 必须是真实局内身份而非伪装；唯一能获得它的伪装是当前伪装成乘务员的验尸官（该伪装在死亡时清除）。
     */
    public static boolean hasDoorLog(@Nullable Role realRole, boolean coronerAttendantDisguise) {
        return isAttendant(realRole) || coronerAttendantDisguise;
    }

    /**
     * Actor a viewer may see for a stored door-log actor: {@code null} stays "no actor", and a viewer whose names are
     * hidden ({@code DetectiveIdentityResolver.viewerSeesNoNames}) sees every actor as anonymous.
     * 观看者能看到的房门记录操作者：null 仍表示没有操作者；名字被隐藏的观看者看到的所有操作者都是匿名。
     */
    public static @Nullable DoorLog.Actor actorShownTo(@Nullable DoorLog.Actor stored, boolean viewerSeesNoNames) {
        if (stored == null) {
            return null;
        }
        return viewerSeesNoNames ? DoorLog.Actor.anonymousActor() : stored;
    }
}
