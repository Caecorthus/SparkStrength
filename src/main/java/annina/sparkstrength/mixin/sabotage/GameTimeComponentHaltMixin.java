package annina.sparkstrength.mixin.sabotage;

import annina.sparkstrength.sabotage.EngineHalt;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the engine is wrecked the round timer skips its natural tick on both sides. The whole tick is skipped, so a
 * frozen value that happens to be a multiple of 400 does not sync every tick; {@code setTime}/{@code addTime} (kills,
 * Timekeeper, Time Stealer ...) still change and sync the time, and zero still ends the round.
 * 动力损毁期间，回合倒计时在两端都跳过自然 tick。跳过的是整个 tick，所以冻结值恰好是 400 的倍数时也不会每 tick 同步；
 * setTime/addTime（击杀、计时员、窃时者……）照常修改并同步时间，减到零仍会结束对局。
 */
@Mixin(value = GameTimeComponent.class, remap = false)
public abstract class GameTimeComponentHaltMixin {
    @Shadow
    @Final
    public World world;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$pauseWhileEngineWrecked(CallbackInfo ci) {
        if (EngineHalt.freezesRoundTimer(world)) {
            ci.cancel();
        }
    }
}
