package annina.sparkstrength.client.mixin.perfumer;

import annina.sparkstrength.client.role.perfumer.PerfumerClientEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Cooling Oil instinct block, layers 2 and 3, for the local victim only (dead or spectating viewers are untouched).
 * <ul>
 *   <li>Gate: {@code isInstinctEnabled()} / {@code isInstinctEnabledAndIsKiller()} read false, which also turns off
 *   every key-gated highlight, the killer instinct night-vision light and the Corrupt Cop / witch light ORs that
 *   consult these gates.</li>
 *   <li>Outline: {@code getInstinctHighlight} returns -1, hiding always-on highlights too (GetInstinctHighlight
 *   listeners, SparkTraits HEAD answers, SparkWitch Insider/Fear wrappers, tablet outlines).</li>
 * </ul>
 * Ordering: MixinExtras applies {@code @WrapMethod} after every {@code @Inject}/{@code @ModifyReturnValue} from any mod
 * at any priority, so returning without {@code original.call()} skips them all; a HEAD veto could not, because HEAD
 * callbacks run in ascending priority after earlier enablers. Among {@code @WrapMethod}s the lower priority wraps
 * outermost: priority 500 sits outside SparkWitch Insider (1000), Fear (1500) and Control Expert (1000), and nests with
 * the Corrupt Cop conceal wrapper because every wrapper only returns -1/false or delegates. {@code remap = false}:
 * Wathe is not a Minecraft class; the highlight selector is name-only because its descriptor holds a Minecraft type.
 * 风油精本能屏蔽第 2、3 层，只作用于本地受害者（死亡或旁观视角不受影响）。
 * 门控层：两个本能入口返回 false，同时关闭所有需按键的高亮、杀手本能夜视亮度，以及依赖这两个入口的黑警/魔女亮度 OR 注入。
 * 描边层：{@code getInstinctHighlight} 返回 -1，常驻描边也一并隐藏（GetInstinctHighlight 监听器、SparkTraits 的 HEAD
 * 结果、SparkWitch 内应/恐惧包装器、平板描边）。
 * 顺序：MixinExtras 在所有模组、任意优先级的 {@code @Inject}/{@code @ModifyReturnValue} 之后应用 {@code @WrapMethod}，
 * 因此不调用 {@code original.call()} 即可全部跳过；HEAD 否决做不到，因为 HEAD 回调按优先级升序执行、排在更早的启用之后。
 * 多个 {@code @WrapMethod} 之间低优先级位于最外层：500 包在 SparkWitch 内应（1000）、恐惧（1500）与控场专家（1000）外面，
 * 并可与黑警遮蔽包装器嵌套共存，因为所有包装器都只会返回 -1/false 或委托原方法。{@code remap = false}：Wathe 不是
 * 原版类；描边方法的描述符含原版类型，因此只用方法名选择。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 500)
public abstract class PerfumerInstinctGateMixin {
    @WrapMethod(method = "isInstinctEnabled()Z")
    private static boolean sparkstrength$gateInstinctWhileBlinded(Operation<Boolean> original) {
        if (PerfumerClientEffects.blocksInstinct()) {
            return false;
        }
        return original.call();
    }

    @WrapMethod(method = "isInstinctEnabledAndIsKiller()Z")
    private static boolean sparkstrength$gateKillerInstinctWhileBlinded(Operation<Boolean> original) {
        if (PerfumerClientEffects.blocksInstinct()) {
            return false;
        }
        return original.call();
    }

    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkstrength$hideHighlightsWhileBlinded(Entity target, Operation<Integer> original) {
        if (PerfumerClientEffects.blocksInstinct()) {
            return -1;
        }
        return original.call(target);
    }
}
