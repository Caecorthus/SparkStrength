package annina.sparkstrength.mixin.sabotage;

import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exact Adapter for one Wathe lamp record: its position and remaining time.
 * Wathe 单盏灯记录的精确 Adapter：位置与剩余时间。
 */
@Mixin(value = WorldBlackoutComponent.BlackoutDetails.class, remap = false)
public interface BlackoutDetailsAccessor {
    @Accessor(value = "pos", remap = false)
    BlockPos sparkstrength$getPos();

    @Accessor(value = "time", remap = false)
    int sparkstrength$getTime();

    @Accessor(value = "time", remap = false)
    void sparkstrength$setTime(int time);
}
