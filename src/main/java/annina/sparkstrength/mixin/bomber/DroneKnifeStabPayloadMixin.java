package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe knife stab aimed at a Bomber drone (server). Priority 900 runs this HEAD callback before every default and
 * higher-priority HEAD handler on the receiver (SparkStrength Veteran/Silencer/Coroner takeovers, NoellesRoles Shadow
 * Jester, SparkTraits Close Quarters); the attacker-side restrictions among them (NoellesRoles Shadow Jester duel knife
 * / betrayal trophy, SparkTraits weapon-action gate) are re-checked in {@code DroneWeaponHits}. Wathe itself returns at
 * once for a non-player id, so cancelling a drone stab skips nothing it would have done; player targets pass through
 * untouched.
 * Wathe 刀刺中炸弹客无人机（服务端）。优先级 900 使此 HEAD 回调先于接收器上所有默认及更高优先级的 HEAD 处理
 * （SparkStrength 老兵/静语者/验尸官接管、NoellesRoles 影子小丑、SparkTraits 近身）；其中针对攻击者的限制（NoellesRoles
 * 影子小丑决斗刀 / 背叛战利品、SparkTraits 武器动作门槛）在 DroneWeaponHits 中复查。Wathe 遇到非玩家 id 会立即返回，
 * 因此取消对无人机的刺击不会跳过其任何逻辑；玩家目标原样放行。
 *
 * <p>A Partygoer id takes the same early path ({@code PartygoerWeaponHits#onKnifeStab}, {@code wathe:knife}): every
 * knife (charged release, Veteran / Coroner instant stab, Silencer quiet knife) sends this payload, and cancelling here
 * means no Veteran stab use is consumed.
 * 派对客 id 走同一条提前路径（PartygoerWeaponHits#onKnifeStab，wathe:knife）：所有刀（蓄力释放、老兵 / 验尸官瞬刺、静语者静音刀）
 * 都发送此数据包，在此取消意味着不消耗老兵刺杀次数。</p>
 *
 * <p>The full {@code receive} descriptor skips the synthetic {@code receive(CustomPayload, Context)} bridge.
 * 使用完整描述符，避开合成桥接方法。</p>
 */
@Mixin(value = KnifeStabPayload.Receiver.class, priority = 900)
public abstract class DroneKnifeStabPayloadMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void sparkstrength$stabDrone(KnifeStabPayload payload, ServerPlayNetworking.Context context,
                                         CallbackInfo ci) {
        ServerPlayerEntity attacker = context.player();
        Entity target = attacker.getServerWorld().getEntityById(payload.target());
        if (target instanceof DroneEntity drone) {
            DroneWeaponHits.onKnifeStab(attacker, drone);
            ci.cancel();
        } else if (target instanceof PartygoerEntity partygoer) {
            PartygoerWeaponHits.onKnifeStab(attacker, partygoer);
            ci.cancel();
        }
    }
}
