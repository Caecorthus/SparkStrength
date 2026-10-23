package annina.sparkstrength.mixin.bodyguard;

import annina.sparkstrength.role.bodyguard.SacrificeSuppressionScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bodyguard hooks on the BEFORE / AFTER dispatch of the one killPlayer overload every Wathe kill funnels into:
 * NoellesRoles' Bodyguard sacrifice is decided inside its BEFORE listener, so that dispatch runs with the sacrifice
 * suppressed and AFTER runs unsuppressed (see SacrificeSuppressionScope). The gear check at HEAD lives in
 * {@link BodyguardGearKillGuardMixin}, at a later priority so immunity guards run first.
 * 保镖在 killPlayer 唯一汇总重载的 BEFORE / AFTER 分发上的钩子：NoellesRoles 在其 BEFORE 监听中判定保镖替死，所以该分发在
 * 抑制替死的状态下执行，AFTER 则不抑制（见 SacrificeSuppressionScope）。HEAD 的装备判定在 BodyguardGearKillGuardMixin，
 * 优先级更晚，让免死判断先执行。
 */
@Mixin(GameFunctions.class)
public abstract class BodyguardKillGuardMixin {
    @WrapOperation(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/KillPlayer$Before;beforeKillPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)Ldev/doctor4t/wathe/api/event/KillPlayer$KillResult;")
    )
    private static KillPlayer.KillResult sparkstrength$suppressBodyguardSacrifice(
            KillPlayer.Before listeners,
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            Operation<KillPlayer.KillResult> original
    ) {
        return SacrificeSuppressionScope.call(true, () -> original.call(listeners, victim, killer, deathReason));
    }

    @WrapOperation(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/KillPlayer$After;afterKillPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V")
    )
    private static void sparkstrength$afterKillUnsuppressed(
            KillPlayer.After listeners,
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            Operation<Void> original
    ) {
        SacrificeSuppressionScope.run(false, () -> original.call(listeners, victim, killer, deathReason));
    }
}
