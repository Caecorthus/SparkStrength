package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverRoleRules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.doctor4t.wathe.api.Role;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The reworked Undercover (owner 2026-10-19) sprints forever like the killers it poses as, and gets the killers' fake
 * tasks so "task +50" has tasks to pay for: FAKE mood generates tasks and fires TaskComplete while the mood stays full,
 * so it can never break down. Common code, so client and server agree (the stamina bar hides itself).
 * 重做后的卧底（负责人 2026-10-19）像它假扮的杀手一样体力无限，并拿到杀手同款的假任务，让「做任务 +50」有任务可做：
 * FAKE 心情会生成任务并触发 TaskComplete，心情始终是满的，不会精神崩溃。这是双端共用代码，客户端与服务端结果一致（体力条会自动隐藏）。
 */
@Mixin(value = Role.class, remap = false)
public abstract class UndercoverRoleMixin {
    @ModifyReturnValue(method = "getMoodType", at = @At("RETURN"))
    private Role.MoodType sparkstrength$undercoverFakeTasks(Role.MoodType original) {
        return UndercoverRoleRules.isUndercover((Role) (Object) this) ? Role.MoodType.FAKE : original;
    }

    @ModifyReturnValue(method = "getMaxSprintTime", at = @At("RETURN"))
    private int sparkstrength$undercoverInfiniteSprint(int original) {
        return UndercoverRoleRules.isUndercover((Role) (Object) this) ? -1 : original;
    }
}
