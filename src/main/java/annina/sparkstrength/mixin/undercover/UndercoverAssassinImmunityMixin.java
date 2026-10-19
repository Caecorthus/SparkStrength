package annina.sparkstrength.mixin.undercover;

import annina.sparkstrength.role.undercover.UndercoverRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;
import org.agmas.noellesroles.packet.AssassinGuessRoleC2SPacket;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A stage-2 Undercover cannot be assassinated (owner 2026-10-19): any guess at it is turned away before NoellesRoles
 * resolves it, so the Assassin keeps the guess and never misfires. Same seam and preconditions as SparkTraits' Last
 * Stand block; build.gradle pins the selector against the libs jar so lambda renumbering fails the build, and
 * {@code UndercoverAssassinKillGuardMixin} is the backstop if it ever slips through.
 * 二阶段卧底不能被刺杀（负责人 2026-10-19）：任何针对它的猜测都在 NoellesRoles 结算前被拦下，刺客不扣次数也不会反噬。
 * 注入点与前置条件同 SparkTraits 的背水一战；build.gradle 会对照 libs jar 锁定选择器，lambda 编号变化会让构建失败；
 * 万一漏过，由 UndercoverAssassinKillGuardMixin 兜底。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class UndercoverAssassinImmunityMixin {
    @Inject(
            method = "lambda$registerPackets$6(Lorg/agmas/noellesroles/packet/AssassinGuessRoleC2SPacket;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void sparkstrength$stage2UndercoverCannotBeAssassinated(
            AssassinGuessRoleC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci
    ) {
        ServerPlayerEntity assassin = context.player();
        ServerWorld world = assassin.getServerWorld();
        if (!GameWorldComponent.KEY.get(world).isRole(assassin, Noellesroles.ASSASSIN)
                || !GameFunctions.isPlayerPlayingAndAlive(assassin)
                || SwallowedPlayerComponent.isPlayerSwallowed(assassin)
                || !AssassinPlayerComponent.KEY.get(assassin).canGuess()) {
            return;
        }
        if (payload.targetPlayer() == null || payload.targetPlayer().equals(assassin.getUuid())) {
            return;
        }
        if (!(world.getPlayerByUuid(payload.targetPlayer()) instanceof ServerPlayerEntity target)
                || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            return;
        }
        if (UndercoverRoles.isStage2(target)) {
            assassin.sendMessage(Text.translatable("message.sparkstrength.undercover.cannot_assassinate"), true);
            ci.cancel();
        }
    }
}
