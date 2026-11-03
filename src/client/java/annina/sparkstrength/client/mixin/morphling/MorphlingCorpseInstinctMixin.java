package annina.sparkstrength.client.mixin.morphling;

import annina.sparkstrength.client.role.morphling.MorphlingCorpseInstinctClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Corpse-mode Morphling instinct cloak ({@link MorphlingCorpseInstinctClient}): the target is evaluated with Wathe's
 * instinct gates reading false. {@code @WrapMethod} sits outside every {@code @Inject} from any mod, so the gates answer
 * false even past SparkWitch/SparkTraits HEAD enablers. Those two gates are the only readers of the instinct key in
 * Wathe, NoellesRoles and the Spark mods (checked 2026-11-03), so unlike blackout sight the key binding itself is left
 * alone. The other wrappers here either veto or delegate, or (the Undercover arbiter) answer only for stage-2
 * Undercover targets and the keyless thermal view, so their nesting order does not matter for this cloak.
 * {@code remap = false}: Wathe is not a Minecraft class.
 * 尸体模式变形者的本能隐蔽（见 MorphlingCorpseInstinctClient）：在 Wathe 本能入口返回 false 的状态下计算该目标的描边。
 * {@code @WrapMethod} 位于任意模组的所有 {@code @Inject} 之外，因此即使 SparkWitch/SparkTraits 的 HEAD 注入想开启，入口也会
 * 返回 false。在 Wathe、NoellesRoles 与各 Spark 模组中只有这两个入口读取本能键（2026-11-03 核对），因此与熄灯视野不同，
 * 按键绑定本身保持不变。这里的其他包装器要么否决、要么委托原方法，卧底裁决器也只为二阶段卧底目标与无需按键的热成像作答，
 * 因此对本隐蔽而言嵌套顺序无关紧要。{@code remap = false}：Wathe 不是原版类。
 */
@Mixin(value = WatheClient.class, remap = false)
public abstract class MorphlingCorpseInstinctMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkstrength$cloakCorpseMorphling(Entity target, Operation<Integer> original) {
        return MorphlingCorpseInstinctClient.highlight(target, () -> original.call(target));
    }

    @WrapMethod(method = "isInstinctEnabled()Z")
    private static boolean sparkstrength$corpseMorphlingInstinctGate(Operation<Boolean> original) {
        return !MorphlingCorpseInstinctClient.releasesKey() && original.call();
    }

    @WrapMethod(method = "isInstinctEnabledAndIsKiller()Z")
    private static boolean sparkstrength$corpseMorphlingKillerInstinctGate(Operation<Boolean> original) {
        return !MorphlingCorpseInstinctClient.releasesKey() && original.call();
    }
}
