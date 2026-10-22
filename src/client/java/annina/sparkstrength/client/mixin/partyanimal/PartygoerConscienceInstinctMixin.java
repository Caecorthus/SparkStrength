package annina.sparkstrength.client.mixin.partyanimal;

import annina.sparkstrength.client.role.partyanimal.PartygoerInstinctClientHooks;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a SparkTraits Conscience killer see the Partygoer in green ({@link PartygoerInstinctClientHooks#beforeConscience}):
 * SparkTraits' HEAD (default priority 1000) returns -1 for every non-player for such a viewer before Wathe's event runs.
 * HEAD callbacks run in ascending mixin priority (SparkWitch checked the Mixin 0.8.7 bytecode; see its
 * {@code WatheClientFiendHighlightMixin}), so 700 answers after the Fiend Moment (500) and Magician puppet (600) HEADs,
 * which ignore Partygoers, and before SparkTraits. Only Partygoer targets with a Conscience viewer are answered, from
 * Wathe's own event verdict, so event skips and suppression still apply; every {@code @WrapMethod} veto (Fear/Obscure,
 * USEC, Cooling Oil, Corrupt Cop, blackout, Undercover) still wraps it, and the key check reads the vetoed gate.
 * {@code remap = false}: Wathe is not a Minecraft class; the selector is name-only because the descriptor holds one.
 * 让 SparkTraits 善良杀手以绿色看到派对客（见 PartygoerInstinctClientHooks#beforeConscience）：对这类观察者，SparkTraits 的 HEAD
 * （默认优先级 1000）会在 Wathe 事件之前对所有非玩家返回 -1。HEAD 回调按 mixin 优先级升序执行（SparkWitch 已按 Mixin 0.8.7 字节码
 * 核实，见其 WatheClientFiendHighlightMixin），因此 700 在魔人时刻（500）与魔术师皮套（600）的 HEAD 之后、SparkTraits 之前作答，
 * 前两者不处理派对客。只回答「善良观察者 + 派对客」，且答案来自 Wathe 自身的事件裁决，因此事件 skip 与压制仍然生效；所有
 * {@code @WrapMethod} 否决（恐惧/障眼、USEC、风油精、黑警、熄灯、卧底）仍包在外层，按键检查读取的也是被否决层包装后的入口。
 * {@code remap = false}：Wathe 不是原版类；描述符含原版类型，因此只用方法名选择。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 700)
public abstract class PartygoerConscienceInstinctMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkstrength$partygoerBeforeConscience(Entity target, CallbackInfoReturnable<Integer> cir) {
        Integer color = PartygoerInstinctClientHooks.beforeConscience(target);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
