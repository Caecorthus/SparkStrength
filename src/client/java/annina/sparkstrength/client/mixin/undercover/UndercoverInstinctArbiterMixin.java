package annina.sparkstrength.client.mixin.undercover;

import annina.sparkstrength.client.role.undercover.UndercoverClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Colour arbiter for the reworked Undercover: killers see a stage-2 Undercover silver in line of sight and nothing
 * otherwise; a stage-2 Undercover's thermal paints living players in sight magenta. It sits inside the veto wrappers
 * (Cooling Oil 500, Corrupt Cop, blackout, SparkWitch fear) and outside every HEAD answer, so vetoes still hide the
 * target while SparkTraits/SparkWitch HEAD colours cannot override the rule. Per Astra's bytecode check of MixinExtras,
 * a higher {@code @WrapMethod} priority wraps further out, so 400 is the innermost of SparkStrength's wrappers; the
 * arbiter also re-checks SparkStrength's own vetoes, so either order keeps them. {@code remap = false}: Wathe class.
 * 重做后卧底的颜色裁决：杀手看视线内的二阶段卧底为银色，看不见时什么都没有；二阶段卧底开热成像时，视线内的活人为品红。
 * 它位于否决包装器（风油精 500、黑警、熄灯、SparkWitch 恐惧）之内、所有 HEAD 结果之外，所以否决仍能隐藏目标，
 * 而 SparkTraits/SparkWitch 的 HEAD 颜色不能推翻本规则。按 Astra 对 MixinExtras 字节码的核查，@WrapMethod 优先级越高越在外层，
 * 400 是 SparkStrength 包装器中最内层的；裁决同时会再检查 SparkStrength 自己的否决，所以任一顺序下它们都保留。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 400)
public abstract class UndercoverInstinctArbiterMixin {
    @WrapMethod(method = "getInstinctHighlight")
    private static int sparkstrength$undercoverArbiter(Entity target, Operation<Integer> original) {
        int decided = UndercoverClient.highlight(target);
        return decided != UndercoverClient.NO_DECISION ? decided : original.call(target);
    }
}
