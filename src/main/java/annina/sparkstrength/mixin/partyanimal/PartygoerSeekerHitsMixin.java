package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional SparkWitch seam for the Time Stealer clock ({@code SeekerDeviceHits.onClockFired}, called with the aimed
 * player before the curse is committed). The curse kills, so a Partygoer strictly nearer than that player takes it and
 * dies at once ({@code sparkwitch:time_stolen}); the null target then makes SparkWitch start the clock cooldown and
 * steal nothing. Kept apart from {@code DroneSparkWitchHitsMixin} (drones never stop the clock). {@code @Pseudo},
 * name-only selector, {@code @Local} arguments and {@code require = 0}: a silent no-op without SparkWitch.
 * SparkWitch only calls this seam when a player was aimed, so a clock fired at a Partygoer with nobody behind it is out
 * of reach from here.
 * 窃时者时钟的可选 SparkWitch 接缝（SeekerDeviceHits.onClockFired，在提交诅咒前携带被瞄准的玩家调用）。诅咒会致死，因此严格比该玩家
 * 更近的派对客承受诅咒并立即死亡（sparkwitch:time_stolen）；随后的 null 目标让 SparkWitch 开始时钟冷却且不窃取任何人。与
 * DroneSparkWitchHitsMixin 分开（无人机从不挡下时钟）。@Pseudo、仅方法名选择器、@Local 参数与 require = 0：未安装 SparkWitch 时
 * 静默失效。SparkWitch 只在瞄准到玩家时才调用该接缝，因此身后没有玩家时对派对客使用时钟无法从这里处理。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits", remap = false)
public abstract class PartygoerSeekerHitsMixin {
    /** {@code onClockFired(ServerPlayerEntity, T extends PlayerEntity, double)}. / 窃时者时钟。 */
    @Inject(method = "onClockFired", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$clockKillsPartygoer(CallbackInfoReturnable<PlayerEntity> cir,
                                                          @Local(argsOnly = true, index = 0) ServerPlayerEntity user,
                                                          @Local(argsOnly = true, index = 1) PlayerEntity target,
                                                          @Local(argsOnly = true) double range) {
        if (PartygoerWeaponHits.onSparkWitchClock(user, target, range)) {
            cir.setReturnValue(null);
        }
    }
}
