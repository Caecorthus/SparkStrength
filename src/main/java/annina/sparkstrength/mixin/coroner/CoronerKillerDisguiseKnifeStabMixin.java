package annina.sparkstrength.mixin.coroner;

import annina.sparkstrength.role.veteran.InnocentKnifeKillRules;
import annina.sparkstrength.role.veteran.VeteranKnifeService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Coroner killer-body knife "小脑" on Wathe's ordinary knife receiver.
 * 在 Wathe 普通刀包接收器上结算验尸官杀手尸体借刀“小脑”。
 *
 * <p>Wathe's ordinary stab has no innocent-kill penalty (the knife is a killer weapon), but a Coroner wearing a
 * killer-faction corpse disguise receives a temporary Wathe knife while staying a Wathe civilian. Only the stab's
 * {@code killPlayer} call is wrapped: alignment is captured before it (SparkTraits clears the victim's traits in
 * KillPlayer.AFTER) and the penalty is applied right after it returns, the same timing as the Veteran knife.
 * Veteran/Scavenger/Silencer-body stabs are cancelled at HEAD by VeteranKnifeService/SilencerKnifeService and never
 * reach this call, so nothing is punished twice; every other attacker passes through unchanged.
 * Wathe 普通刀刺本身没有误杀惩罚（匕首是杀手武器），但验尸官顶着杀手阵营尸体会拿到临时 Wathe 匕首，身份仍是 Wathe 好人。
 * 这里只包裹刺杀的 killPlayer 调用：击杀前记录阵营（SparkTraits 会在 KillPlayer.AFTER 清空死者天赋），
 * 击杀返回后立即结算，与老兵匕首时序一致。老兵/清道夫/静语者尸体身份已在 HEAD 被接管、不会走到这里，
 * 因此不会重复惩罚；其他攻击者原样放行。</p>
 *
 * <p>The full {@code receive} descriptor skips the synthetic {@code receive(CustomPayload, Context)} bridge.
 * 使用完整描述符，避开合成桥接方法 receive(CustomPayload, Context)。</p>
 */
@Mixin(KnifeStabPayload.Receiver.class)
public abstract class CoronerKillerDisguiseKnifeStabMixin {
    @WrapOperation(
            method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"
            )
    )
    private void sparkstrength$coronerKillerDisguiseKnifeKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            Operation<Void> original
    ) {
        InnocentKnifeKillRules.PreKill preKill =
                VeteranKnifeService.captureCoronerKillerDisguiseKnifeKill(killer, victim, deathReason);
        original.call(victim, spawnBody, killer, deathReason);
        VeteranKnifeService.punishCoronerKillerDisguiseKnifeKill(killer, victim, preKill);
    }
}
