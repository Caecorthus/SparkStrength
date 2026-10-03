package annina.sparkstrength.client.mixin.corruptcop;

import annina.sparkstrength.client.role.corruptcop.CorruptCopClientHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Outermost veto for the Corrupt Cop's close-range instinct concealment. {@code @WrapMethod} wraps every
 * {@code @Inject}/{@code @ModifyReturnValue} on WatheClient from any mod, so returning -1 without
 * {@code original.call()} also hides SparkTraits' HEAD answers (Impostor/Conscience viewers); a plain
 * GetInstinctHighlight skip could not. Exemptions live in CorruptCopConcealmentRules.
 * 黑警近距离本能屏蔽的最外层否决。{@code @WrapMethod} 包裹任意模组对 WatheClient 的全部
 * {@code @Inject}/{@code @ModifyReturnValue}，因此不调用 {@code original.call()} 直接返回 -1，
 * 也能屏蔽 SparkTraits 的 HEAD 结果（内鬼/良心观察者）；普通的 GetInstinctHighlight 跳过做不到。
 * 豁免条件见 CorruptCopConcealmentRules。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class WatheClientInstinctConcealMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkstrength$concealNearCorruptCop(Entity target, Operation<Integer> original) {
        if (CorruptCopClientHooks.shouldConcealInstinct(target)) {
            return -1;
        }
        return original.call(target);
    }
}
