package annina.sparkstrength.client.mixin.undercover;

import annina.sparkstrength.client.role.undercover.UndercoverClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * No outline of any kind on a stage-2 Undercover the viewer can't plainly see (owner 2026-10-20). The instinct colour
 * is already decided by UndercoverInstinctArbiterMixin; this also covers vanilla glowing, which never asks the instinct
 * (Noisemaker backpack and killer glow, Conscience bomb). Only ever turns an outline off, so it sits outermost.
 * 观察者看不见的二阶段卧底不显示任何描边（负责人 2026-10-20）。本能颜色已由 UndercoverInstinctArbiterMixin 裁决；这里还覆盖
 * 不经过本能的原版发光（噪音制造者背包与击杀者发光、良心炸弹）。只会关闭描边，所以放在最外层。
 */
@Mixin(value = MinecraftClient.class, priority = 2500)
public abstract class UndercoverOutlineVetoMixin {
    @WrapMethod(method = "hasOutline")
    private boolean sparkstrength$noOutlineThroughWalls(Entity entity, Operation<Boolean> original) {
        return !UndercoverClient.hidesOutline(entity) && original.call(entity);
    }
}
