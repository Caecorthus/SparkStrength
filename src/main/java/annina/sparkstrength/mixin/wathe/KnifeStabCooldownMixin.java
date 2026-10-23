package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.role.KnifeStabCooldown;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Wathe's knife receiver sets the stab cooldown once, at the end, after its creative / Loose Ends check; this swaps the
 * player-count-scaled ticks for the flat {@link KnifeStabCooldown#TICKS}. Stab checks, kill and sound are untouched.
 * Class-level {@code remap = false}; the vanilla target says {@code remap = true}.
 * Wathe 刀刺接收器在流程末尾（创造 / Loose Ends 判断之后）只设置一次刀冷却；这里把按人数缩放的冷却替换为固定的
 * {@link KnifeStabCooldown#TICKS}，刺杀验证、击杀与音效不变。类级 {@code remap = false}；原版目标显式声明 {@code remap = true}。
 */
@Mixin(value = KnifeStabPayload.Receiver.class, remap = false)
public abstract class KnifeStabCooldownMixin {
    @ModifyArg(
            method = "receive(Ldev/doctor4t/wathe/util/KnifeStabPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/ItemCooldownManager;set(Lnet/minecraft/item/Item;I)V",
                    remap = true
            ),
            index = 1
    )
    private int sparkstrength$flatKnifeCooldown(int scaledCooldown) {
        return KnifeStabCooldown.TICKS;
    }
}
