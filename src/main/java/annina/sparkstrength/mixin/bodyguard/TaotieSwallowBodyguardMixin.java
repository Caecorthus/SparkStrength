package annina.sparkstrength.mixin.bodyguard;

import annina.sparkstrength.role.bodyguard.BodyguardEconomyService;
import annina.sparkstrength.role.bodyguard.BodyguardProtectionService;
import annina.sparkstrength.role.bodyguard.SacrificeSuppressionScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bodyguard rules on Taotie's swallow, which never goes through killPlayer:
 * - the whole swallow runs with the Bodyguard sacrifice suppressed;
 * - a raised Democracy Shield facing the Taotie stops it (8 points) right before NoellesRoles' Iron Man check, i.e.
 *   after its cooldown, distance, line-of-sight and alive checks, and starts the Taotie's normal cooldown like Iron Man;
 * - a swallowed protected target costs the Bodyguard 50 coins, hooked where NoellesRoles tells the Serial Killer.
 * 保镖在饕餮吞噬上的规则（吞噬从不经过 killPlayer）：
 * - 整个吞噬过程抑制保镖替死；
 * - 举起且朝向饕餮的民主盾牌在 NoellesRoles 铁人药水检查前挡下吞噬（8 点），即冷却、距离、视线与存活检查之后，
 *   并像铁人药水一样让饕餮进入正常冷却；
 * - 保护目标被吞时保镖扣 50 金币，挂在 NoellesRoles 通知连环杀手的位置。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public abstract class TaotieSwallowBodyguardMixin {
    @Shadow
    @Final
    private PlayerEntity player;
    @Shadow
    private int swallowCooldown;
    @Shadow
    private int calculatedSwallowCooldown;

    @Shadow
    public abstract void sync();

    @WrapMethod(method = "swallowPlayer")
    private boolean sparkstrength$swallowWithoutBodyguardSacrifice(ServerPlayerEntity target, Operation<Boolean> original) {
        return SacrificeSuppressionScope.call(true, () -> original.call(target));
    }

    @Inject(
            method = "swallowPlayer",
            at = @At(value = "INVOKE", target = "Lorg/agmas/noellesroles/professor/IronManPlayerComponent;hasBuff()Z", ordinal = 0),
            cancellable = true
    )
    private void sparkstrength$democracyShieldStopsSwallow(ServerPlayerEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayerEntity taotie && BodyguardProtectionService.tryBlockSwallow(target, taotie)) {
            swallowCooldown = calculatedSwallowCooldown;
            sync();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "notifySerialKillersTargetSwallowed", at = @At("HEAD"))
    private void sparkstrength$bodyguardTargetSwallowed(ServerPlayerEntity target, ServerWorld world, CallbackInfo ci) {
        BodyguardEconomyService.onTargetGone(target);
    }
}
