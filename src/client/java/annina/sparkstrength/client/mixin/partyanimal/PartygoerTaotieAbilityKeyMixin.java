package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.client.role.partyanimal.PartygoerTaotieClient;
import annina.sparkstrength.role.partyanimal.PartygoerTaotieRules;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side prediction for the Taotie's G key on a Partygoer: {@code KeyBinding#wasPressed} RETURN (vanilla member,
 * remapped). Only when the original result is true, {@code this} is {@code NoellesrolesClient.abilityBind}, the local
 * player is a ready Taotie and the vanilla crosshair is on a Partygoer within 3.0 of the eye, the press reads as false
 * (NoellesRoles sends no {@code taotie_swallow}, so a player behind the Partygoer is never swallowed) and, when it is also
 * within the server's feet-to-feet reach, {@code partygoer_swallow} is sent. Coexists with SparkWitch's
 * {@code SeekerTaotieAbilityKeyMixin} on the same key in either order: each claims only its own entity, and whichever
 * claims first leaves the other a false result. Engineer and Control Expert stuns cancel at HEAD, so this never runs then.
 * 饕餮对派对客按 G 的客户端预测：对 {@code KeyBinding#wasPressed} 做 RETURN 注入（原版成员，需重映射）。仅当原始结果为真、
 * {@code this} 为 {@code NoellesrolesClient.abilityBind}、本地玩家是就绪的饕餮且原版准星落在距眼睛 3.0 格内的派对客上时，本次按键
 * 读作 false（NoellesRoles 不发送 {@code taotie_swallow}，派对客身后的玩家因此不会被吞）；若它也在服务端脚到脚距离内，则发送
 * {@code partygoer_swallow}。与同一按键上 SparkWitch 的 {@code SeekerTaotieAbilityKeyMixin} 以任意顺序共存：各自只认领自己的实体，
 * 先认领的一方让另一方读到 false。工程师定身与控场专家眩晕在 HEAD 处取消，此时本钩子不会执行。
 */
@Mixin(KeyBinding.class)
public abstract class PartygoerTaotieAbilityKeyMixin {
    @Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
    private void sparkstrength$claimPartygoerSwallow(CallbackInfoReturnable<Boolean> cir) {
        int claim = PartygoerTaotieClient.claimPress((KeyBinding) (Object) this, cir.getReturnValueZ());
        if (claim != PartygoerTaotieRules.NO_CLAIM) {
            cir.setReturnValue(false);
            PartygoerTaotieClient.send(claim);
        }
    }
}
