package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe revolver/derringer shot at a Bomber drone (server), at the {@code recordItemUse} anchor: after Wathe's
 * spectator, gun-tag, cooldown and spent-derringer checks (the anchor SparkTraits and SparkWitch also use). A drone id
 * never becomes Wathe's player target, so Wathe finishes the shot as a miss (click/shot sounds, derringer spent,
 * cooldown, no punishment); SilencerGunShootPayloadMixin only wraps the sounds and is unaffected.
 * Wathe 左轮/德林加射向炸弹客无人机（服务端），挂在 recordItemUse 锚点：位于 Wathe 的旁观、枪械标签、冷却与德林加已用检查之后
 * （SparkTraits 与 SparkWitch 使用同一锚点）。无人机 id 永远不会成为 Wathe 的玩家目标，因此 Wathe 按未命中完成这一枪
 * （扳机/射击声、德林加用尽、冷却、无惩罚）；SilencerGunShootPayloadMixin 只包装音效，不受影响。
 *
 * <p>The same anchor kills a Partygoer the client picked ({@code PartygoerWeaponHits#onGunShot}, {@code wathe:gun});
 * one entity id is either a drone or a Partygoer, so at most one of the two acts.
 * 同一锚点也处理客户端选中的派对客（PartygoerWeaponHits#onGunShot，wathe:gun）；一个实体 id 要么是无人机要么是派对客，
 * 两者至多一个生效。</p>
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class DroneGunShootPayloadMixin {
    @Inject(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/record/GameRecordManager;recordItemUse(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/nbt/NbtCompound;)V"))
    private void sparkstrength$shootDrone(GunShootPayload payload, ServerPlayNetworking.Context context,
                                          CallbackInfo ci) {
        ServerPlayerEntity shooter = context.player();
        Entity target = shooter.getServerWorld().getEntityById(payload.target());
        DroneWeaponHits.onGunShot(shooter, target);
        PartygoerWeaponHits.onGunShot(shooter, target);
    }
}
