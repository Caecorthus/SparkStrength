package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.role.jester.JesterMomentService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the start of a NoellesRoles Jester Moment. Pinned NoellesRoles 1.7.6: the server tick calls the private
 * {@code startJesterPsychoMode} when stasis ends, and only a successful Wathe {@code startPsycho} flips
 * {@code inPsychoMode}; so the moment began exactly when the flag went from false at HEAD to true at RETURN.
 * Selectors use only NoellesRoles names, so {@code remap = false} holds everywhere.
 * 接入 NoellesRoles 小丑时刻的开始。锁定 NoellesRoles 1.7.6：禁锢结束时服务端 tick 调用私有的
 * {@code startJesterPsychoMode}，只有 Wathe {@code startPsycho} 成功才会置位 {@code inPsychoMode}；
 * 因此该标记在 HEAD 为 false、RETURN 为 true 时，时刻恰好开始。选择器只含 NoellesRoles 名称，{@code remap = false} 处处成立。
 */
@Mixin(value = JesterPlayerComponent.class, remap = false)
public abstract class JesterMomentStartMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Unique
    private boolean sparkstrength$wasInMoment;

    @Inject(method = "startJesterPsychoMode", at = @At("HEAD"))
    private void sparkstrength$recordMomentBefore(CallbackInfo ci) {
        this.sparkstrength$wasInMoment = ((JesterPlayerComponent) (Object) this).inPsychoMode;
    }

    @Inject(method = "startJesterPsychoMode", at = @At("RETURN"))
    private void sparkstrength$teleportShooter(CallbackInfo ci) {
        JesterPlayerComponent jester = (JesterPlayerComponent) (Object) this;
        if (!this.sparkstrength$wasInMoment && jester.inPsychoMode && this.player instanceof ServerPlayerEntity serverJester) {
            JesterMomentService.onMomentStarted(serverJester, jester.targetKiller);
        }
    }
}
