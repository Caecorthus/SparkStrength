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
 *   listeners, SparkTraits and SparkWitch HEAD answers, tablet suspect highlights).</li>
 * </ul>
 * Ordering: MixinExtras applies {@code @WrapMethod} after every {@code @Inject}/{@code @ModifyReturnValue} from any mod
 * at any priority, so returning without {@code original.call()} skips them all; a HEAD veto could not, because HEAD
 * callbacks run in ascending priority after earlier enablers. Among {@code @WrapMethod}s the higher priority wraps
 * outermost (MixinExtras 0.5.0: mixins apply in ascending priority and each wrapper encloses the ones offered before
 * it), so priority 500 sits inside SparkWitch's Black Raven veto (3000), Fear (1500), USEC cloak and Control Expert
 * gates (1000) and SparkStrength's Corrupt Cop and blackout wrappers (1000), and outside the Undercover arbiter (400).
 * That is safe because every wrapper outside it only returns -1/false or delegates, so none can answer past this veto.
 * {@code remap = false}: Wathe is not a Minecraft class; the highlight selector is name-only because its descriptor
 * holds a Minecraft type.
 * 风油精本能屏蔽第 2、3 层，只作用于本地受害者（死亡或旁观视角不受影响）。
 * 门控层：两个本能入口返回 false，同时关闭所有需按键的高亮、杀手本能夜视亮度，以及依赖这两个入口的黑警/魔女亮度 OR 注入。
 * 描边层：{@code getInstinctHighlight} 返回 -1，常驻描边也一并隐藏（GetInstinctHighlight 监听器、SparkTraits
 * 与 SparkWitch 的 HEAD 结果、平板嫌疑人高亮）。
 * 顺序：MixinExtras 在所有模组、任意优先级的 {@code @Inject}/{@code @ModifyReturnValue} 之后应用 {@code @WrapMethod}，
 * 因此不调用 {@code original.call()} 即可全部跳过；HEAD 否决做不到，因为 HEAD 回调按优先级升序执行、排在更早的启用之后。
 * 多个 {@code @WrapMethod} 之间高优先级位于最外层（MixinExtras 0.5.0：mixin 按优先级升序应用，每个包装器包住在它之前
 * 提交的包装器），所以 500 位于 SparkWitch 黑羽鸦否决（3000）、恐惧（1500）、USEC 隐匿与控场专家入口（1000）以及
 * SparkStrength 黑警遮蔽与熄灯包装器（1000）之内，卧底裁决（400）之外。这是安全的：它外层的每个包装器都只会返回
 * -1/false 或委托原方法，没有谁能越过本否决作答。{@code remap = false}：Wathe 不是原版类；描边方法的描述符含原版类型，
 * 因此只用方法名选择。
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
