package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineHaltHud;
import dev.doctor4t.wathe.client.gui.TimeRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The halted tag beside Wathe's round timer. {@code renderHud} pops its matrix exactly once, inside the branch that
 * draws the timer (round running, and the role can see the time or spectator information is shown), with the origin
 * still at the timer; injecting right before that pop inherits both the visibility and the position. Wathe's own HUD
 * class, no renderer mod touches it.
 * Wathe 回合倒计时旁的停驶标签。renderHud 只弹出一次矩阵，就在绘制倒计时的分支里（对局进行中，且身份能看到时间或显示旁观者
 * 信息），此时原点仍在倒计时处；在那次弹出之前注入，可见性和位置都直接沿用。这是 Wathe 自己的 HUD 类，没有渲染模组会改它。
 */
@Mixin(value = TimeRenderer.class, remap = false)
public abstract class EngineHaltTimerMixin {
    @Inject(method = "renderHud", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V", remap = true), require = 1, allow = 1)
    private static void sparkstrength$haltedBesideTimer(TextRenderer renderer, ClientPlayerEntity player,
                                                         DrawContext context, float delta, CallbackInfo ci) {
        EngineHaltHud.renderBesideTimer(renderer, player, context);
    }
}
