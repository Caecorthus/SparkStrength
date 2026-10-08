package annina.sparkstrength.mixin.bodyguard;

import annina.sparkstrength.role.bodyguard.SacrificeSuppressionScope;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.bodyguard.BodyguardPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * The Bodyguard no longer dies for its target (owner rule). NoellesRoles' only server callers of isCurrentTarget are
 * its two sacrifice checks (knife in KillPlayer.BEFORE, Taotie swallow), both run inside SacrificeSuppressionScope, so
 * the method answers false only there. Its lambda names differ between NoellesRoles builds, so the call sites are not
 * targeted directly; the client highlight and SparkTraits' Impostor reward (in AFTER) keep the real answer.
 * 保镖不再为目标赴死（所有者规则）。NoellesRoles 在服务端调用 isCurrentTarget 的只有两处替死判定（KillPlayer.BEFORE 中的刀杀、
 * 饕餮吞噬），二者都在 SacrificeSuppressionScope 内执行，所以只在那里返回 false。其 lambda 名在不同 NoellesRoles 构建中不同，
 * 因此不直接定位调用点；客户端高亮与 SparkTraits 在 AFTER 中的内鬼奖励仍得到真实结果。
 */
@Mixin(value = BodyguardPlayerComponent.class, remap = false)
public abstract class BodyguardSacrificeMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "isCurrentTarget", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$noSacrifice(UUID playerUuid, CallbackInfoReturnable<Boolean> cir) {
        if (SacrificeSuppressionScope.isSuppressed() && !player.getWorld().isClient()) {
            cir.setReturnValue(false);
        }
    }
}
