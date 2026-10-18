package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.BlackoutSightClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Blackout instinct rules ({@link BlackoutSightClient}): a player the key may not reveal is evaluated with Wathe's
 * instinct gates reading false. {@code @WrapMethod} sits outside every {@code @Inject} from any mod, so the gates
 * answer false even past SparkWitch/SparkTraits HEAD enablers; the key itself is released in
 * {@link BlackoutInstinctKeyMixin} for hooks that read it directly. Every other wrapper here only returns -1/false or
 * delegates, so their nesting order does not matter. {@code remap = false}: Wathe is not a Minecraft class.
 * 熄灯本能规则（见 BlackoutSightClient）：本能键不能透视的玩家，在 Wathe 本能入口返回 false 的状态下计算描边。
 * {@code @WrapMethod} 位于任意模组的所有 {@code @Inject} 之外，因此即使 SparkWitch/SparkTraits 的 HEAD 注入想开启，
 * 入口也会返回 false；直接读取按键的钩子由 BlackoutInstinctKeyMixin 松开按键处理。这里的其他包装器都只会返回
 * -1/false 或委托原方法，因此嵌套顺序无关紧要。{@code remap = false}：Wathe 不是原版类。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class BlackoutInstinctSightMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkstrength$blackoutSight(Entity target, Operation<Integer> original) {
        return BlackoutSightClient.highlight(target, () -> original.call(target));
    }

    @WrapMethod(method = "isInstinctEnabled()Z")
    private static boolean sparkstrength$blackoutInstinctGate(Operation<Boolean> original) {
        return !BlackoutSightClient.releasesKey() && original.call();
    }

    @WrapMethod(method = "isInstinctEnabledAndIsKiller()Z")
    private static boolean sparkstrength$blackoutKillerInstinctGate(Operation<Boolean> original) {
        return !BlackoutSightClient.releasesKey() && original.call();
    }
}
