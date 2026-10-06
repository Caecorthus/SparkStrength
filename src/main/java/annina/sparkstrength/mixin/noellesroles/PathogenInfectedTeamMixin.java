package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.role.pathogen.PathogenVisibility;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.pathogen.InfectedPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every Pathogen counts as infected, on both sides. A T-Virus can create a second Pathogen, and NoellesRoles' win check
 * wants every other living player infected, so an uninfected teammate would block the win forever. Its infect skill and
 * nearest-target compass would also aim at teammates. A named-method hook (identical in both pinned NoellesRoles jars)
 * instead of the win lambda, whose synthetic name differs between them. The Taotie stomach spread is unchanged: a
 * Pathogen's own {@code infectedBy} stays null.
 * 所有病原体在两端都视为已感染。T病毒可能产生第二名病原体，而 NoellesRoles 的胜利判定要求其他存活玩家全部感染，
 * 未感染的同伴会让胜利永远无法达成；其感染技能与最近目标指针也会对准同伴。这里挂接具名方法（两个固定的 NoellesRoles
 * jar 中完全一致），而不是两者合成名不同的胜利 lambda。饕餮胃内传染不受影响：病原体自身的 {@code infectedBy} 仍为空。
 */
@Mixin(value = InfectedPlayerComponent.class, remap = false)
public abstract class PathogenInfectedTeamMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "isInfected", at = @At("HEAD"), cancellable = true, remap = false)
    private void sparkstrength$pathogensCountAsInfected(CallbackInfoReturnable<Boolean> cir) {
        if (PathogenVisibility.isPathogen(player)) {
            cir.setReturnValue(true);
        }
    }
}
