package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import annina.sparkstrength.entity.M67GrenadeEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Run after provider outline additions; outside the warning cube M67 has no outline.
// 在上游描边逻辑之后执行；警告立方体外的 M67 不显示描边。
@Mixin(value = MinecraftClient.class, priority = 900)
public abstract class M67OutlineMixin {
    @ModifyReturnValue(method = "hasOutline(Lnet/minecraft/entity/Entity;)Z", at = @At("RETURN"))
    private boolean sparkstrength$m67Outline(boolean original, @Local(argsOnly = true) Entity entity) {
        return entity instanceof M67GrenadeEntity ? M67Client.outlineColor(entity) != -1 : original;
    }
}
