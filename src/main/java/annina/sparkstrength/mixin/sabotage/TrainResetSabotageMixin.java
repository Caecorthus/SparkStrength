package annina.sparkstrength.mixin.sabotage;

import annina.sparkstrength.sabotage.SabotageService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The round's train copy really finished: {@code tryResetTrain} returns false on success ("no retry needed"). Only
 * then are junction boxes and breakers scanned, so a pending or retried copy never looks like a map without them.
 * 本局的车厢复制真正完成：tryResetTrain 成功时返回 false（无需重试）。只有这时才扫描接线箱与电闸，
 * 因此排队中或重试中的复制不会被误认为是没有这些方块的地图。
 */
@Mixin(value = GameFunctions.class, remap = false)
public abstract class TrainResetSabotageMixin {
    @Inject(method = "tryResetTrain", at = @At("RETURN"))
    private static void sparkstrength$afterTrainReset(ServerWorld world, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            SabotageService.onTrainReset(world);
        }
    }
}
