package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.role.jester.JesterMomentService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Jester's fake corpse unkillable. Every Wathe kill funnels into this overload, so a cancel at its head
 * stops forced kills too and runs before any KillPlayer.BEFORE listener can spend a shield or record a blocked death.
 * 让小丑的假尸体无法被杀死。Wathe 的所有击杀都汇入这个重载，在开头取消即可挡住强制击杀，
 * 且早于任何 KillPlayer.BEFORE 监听消耗护盾或记录死亡被挡。
 */
@Mixin(GameFunctions.class)
public abstract class JesterFakeDeathImmunityMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkstrength$jesterFakeDeathImmunity(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (JesterMomentService.blocksFakeDeathKill(victim, deathReason)) {
            ci.cancel();
        }
    }
}
