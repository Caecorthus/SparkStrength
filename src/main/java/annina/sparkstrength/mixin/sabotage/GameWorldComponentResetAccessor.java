package annina.sparkstrength.mixin.sabotage;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exact Adapter for Wathe's train-copy retry counter: positive while a copy is queued or retrying, negative once it
 * succeeded or when none was queued. The sabotage map scan waits for a pending copy instead of guessing with a timeout.
 * Wathe 车厢复制重试计数的精确 Adapter：复制排队或重试中为正数，成功后或从未排队时为负数。
 * 破坏系统的地图扫描会等待进行中的复制，而不是靠超时猜测。
 */
@Mixin(value = GameWorldComponent.class, remap = false)
public interface GameWorldComponentResetAccessor {
    @Accessor(value = "ticksUntilNextResetAttempt", remap = false)
    int sparkstrength$getTicksUntilNextResetAttempt();
}
