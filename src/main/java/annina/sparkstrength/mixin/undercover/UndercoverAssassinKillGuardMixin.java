package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverRoles;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Backstop for {@link UndercoverAssassinImmunityMixin}: an assassination kill of a stage-2 Undercover never lands.
 * Compares against NoellesRoles' own constant (bounties compare death reasons by reference).
 * UndercoverAssassinImmunityMixin 的兜底：对二阶段卧底的刺杀击杀永远不会生效。直接比较 NoellesRoles 自己的常量。
 */
@Mixin(GameFunctions.class)
public abstract class UndercoverAssassinKillGuardMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkstrength$stage2UndercoverSurvivesAssassination(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (Noellesroles.DEATH_REASON_ASSASSINATED.equals(deathReason) && UndercoverRoles.isStage2(victim)) {
            ci.cancel();
        }
    }
}
