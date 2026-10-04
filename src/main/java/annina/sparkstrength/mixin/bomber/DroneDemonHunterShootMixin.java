package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.demonhunter.DemonHunterShootC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NoellesRoles Demon Hunter pistol shot at a Bomber drone (server). The seam is the receiver's single target lookup,
 * which runs after its held-pistol, cooldown and bullet checks. A broken drone resolves to null, so NoellesRoles runs
 * its own miss path (bullet spent, sounds, cooldown); every other result is returned untouched. Chains with
 * SparkWitch's Seeker wrapper on the same call.
 * NoellesRoles 猎魔枪射向炸弹客无人机（服务端）。接缝是接收器唯一一次目标查找，位于其手持、冷却与子弹检查之后。
 * 被击毁的无人机解析为 null，NoellesRoles 随后走自身未命中流程（消耗子弹、音效、冷却）；其他结果原样返回。
 * 与 SparkWitch 搜寻者在同一调用上的包装可以链式共存。
 */
@Mixin(DemonHunterShootC2SPacket.Receiver.class)
public abstract class DroneDemonHunterShootMixin {
    @WrapOperation(method = "receive(Lorg/agmas/noellesroles/demonhunter/DemonHunterShootC2SPacket;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getEntityById(I)Lnet/minecraft/entity/Entity;"))
    private @Nullable Entity sparkstrength$absorbShotAtDrone(ServerWorld world, int entityId,
                                                             Operation<Entity> original,
                                                             DemonHunterShootC2SPacket payload,
                                                             ServerPlayNetworking.Context context) {
        Entity resolved = original.call(world, entityId);
        return DroneWeaponHits.onDemonHunterShot(context.player(), resolved) ? null : resolved;
    }
}
