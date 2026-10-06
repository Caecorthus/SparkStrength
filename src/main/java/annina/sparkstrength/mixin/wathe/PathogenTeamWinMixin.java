package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.role.pathogen.PathogenTeamService;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Q7: Pathogens win together. NoellesRoles names one living Pathogen through the single-player
 * {@code WinResult.neutralWin}; every other Pathogen of the round, dead or alive and online, joins as a co-winner.
 * Hooked on Wathe's named factory because NoellesRoles' win lambda has a different synthetic name in each pinned jar.
 * Only a Pathogen winner is touched.
 * Q7：病原体共同获胜。NoellesRoles 通过单人 {@code WinResult.neutralWin} 指定一名存活病原体；本局其他所有在线病原体
 * （无论存活与否）作为共同胜者加入。挂接 Wathe 的具名工厂方法，因为 NoellesRoles 的胜利 lambda 在两个固定 jar 中合成名不同。
 * 只影响病原体胜者。
 */
@Mixin(CheckWinCondition.WinResult.class)
public abstract class PathogenTeamWinMixin {
    @Inject(
            method = "neutralWin(Lnet/minecraft/server/network/ServerPlayerEntity;)Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkstrength$pathogensWinTogether(
            ServerPlayerEntity winner,
            CallbackInfoReturnable<CheckWinCondition.WinResult> cir
    ) {
        List<ServerPlayerEntity> teammates = PathogenTeamService.coWinners(winner);
        if (!teammates.isEmpty()) {
            cir.setReturnValue(CheckWinCondition.WinResult.neutralWin(winner, teammates));
        }
    }
}
