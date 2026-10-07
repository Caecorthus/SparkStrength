package annina.sparkstrength.mixin.bodyguard;

import annina.sparkstrength.role.bodyguard.BodyguardProtectionService;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bodyguard hooks on the one killPlayer overload every Wathe kill funnels into:
 * - HEAD: the Democracy Shield and vest. Here the force flag is known, and the gear is spent before any KillPlayer.BEFORE
 *   listener (NoellesRoles' Iron Man / whiskey) or Wathe's psycho armour.
 * - BEFORE / AFTER dispatch: NoellesRoles' Bodyguard sacrifice is decided inside its BEFORE listener, so that dispatch
 *   runs with the sacrifice suppressed and AFTER runs unsuppressed (see SacrificeSuppressionScope).
 * 保镖在 killPlayer 唯一汇总重载上的钩子：
 * - HEAD：民主盾牌与防弹衣。此处能看到 force 标记，并且装备先于任何 KillPlayer.BEFORE 监听（NoellesRoles 铁人/威士忌）
 *   与 Wathe 疯魔护甲消耗。
 * - BEFORE / AFTER 分发：NoellesRoles 在其 BEFORE 监听中判定保镖替死，所以该分发在抑制替死的状态下执行，
 *   AFTER 则不抑制（见 SacrificeSuppressionScope）。
 */
@Mixin(GameFunctions.class)
public abstract class BodyguardKillGuardMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkstrength$bodyguardGearBlocksKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (BodyguardProtectionService.tryBlockKill(victim, killer, deathReason, force)) {
            ci.cancel();
        }
    }

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
