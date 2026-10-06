package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.role.jester.JesterMomentService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the NoellesRoles Jester transition. Pinned NoellesRoles 1.7.6: an innocent's gunshot calls
 * {@code beginFakeDeath}, which (on success) sets {@code spectatorTicks}; when they run out, the private {@code revive}
 * teleports the Jester back to its death spot and calls {@code enterStasis} (its only caller), which records the stasis
 * lock point from the Jester's current position. So freezing at the end of {@code beginFakeDeath} and shuffling at the
 * head of {@code enterStasis} lets the stasis lock hold the Jester's shuffled spot. The component normally syncs to
 * other players only while {@code inPsychoMode}; syncing it during stasis too lets every client start NoellesRoles'
 * everyone-looks-like-the-Jester view when the transformation starts. Upstream also never tells the others when the
 * moment ends (a dead or reset Jester syncs only to itself, so their view stuck on); the sync that leaves a state
 * the others were shown now reaches them too. Selectors use only NoellesRoles names, so {@code remap = false} holds
 * everywhere.
 * 接入 NoellesRoles 小丑转变。锁定 NoellesRoles 1.7.6：好人开枪调用 {@code beginFakeDeath}，成功时设置
 * {@code spectatorTicks}；倒计时结束后私有的 {@code revive} 把小丑传回死亡处并调用 {@code enterStasis}（唯一调用方），
 * 后者按小丑当前位置记录禁锢点。因此在 {@code beginFakeDeath} 结尾定身、在 {@code enterStasis} 开头打乱位置，
 * 禁锢就会锁在小丑打乱后的新位置。该组件平时只在 {@code inPsychoMode} 时同步给其他玩家；禁锢期间也同步，
 * 各客户端才能在转变开始时开启 NoellesRoles 的“所有人都像小丑”视角。上游在时刻结束时也从不通知其他人
 * （死亡或被重置的小丑只同步给自己，导致他们的视角一直停留）；现在离开“其他人可见状态”的那次同步也会发给他们。
 * 选择器只含 NoellesRoles 名称，{@code remap = false} 处处成立。
 */
@Mixin(value = JesterPlayerComponent.class, remap = false)
public abstract class JesterMomentTransitionMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    /**
     * Others were sent the stasis or moment state, so the sync that ends it (a failed psycho start, the Jester's
     * death, a round reset) must reach them too; the component would otherwise sync only to the Jester and their view
     * would stick.
     * 其他人已收到禁锢或时刻状态，因此结束它的那次同步（疯魔启动失败、小丑死亡、对局重置）也必须发给他们；
     * 否则组件只会同步给小丑本人，他们的视角会一直停留。
     */
    @Unique
    private boolean sparkstrength$othersSawMoment;

    @Inject(method = "beginFakeDeath", at = @At("RETURN"))
    private void sparkstrength$freezeEveryone(CallbackInfo ci) {
        JesterPlayerComponent jester = (JesterPlayerComponent) (Object) this;
        if (jester.spectatorTicks > 0 && this.player instanceof ServerPlayerEntity serverJester) {
            JesterMomentService.onFakeDeathStarted(serverJester, jester.spectatorTicks);
        }
    }

    @Inject(method = "enterStasis", at = @At("HEAD"))
    private void sparkstrength$shuffleEveryone(int ticks, CallbackInfo ci) {
        if (this.player instanceof ServerPlayerEntity serverJester) {
            JesterMomentService.onTransformationStarted(serverJester);
        }
    }

    @Inject(method = "shouldSyncWith", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$syncStasisToEveryone(ServerPlayerEntity recipient, CallbackInfoReturnable<Boolean> cir) {
        JesterPlayerComponent jester = (JesterPlayerComponent) (Object) this;
        if (jester.inStasis || jester.inPsychoMode) {
            this.sparkstrength$othersSawMoment = true;
            cir.setReturnValue(true);
        } else if (this.sparkstrength$othersSawMoment) {
            cir.setReturnValue(true);
        }
    }

    /**
     * The ending sync has gone out (it runs inside this tick or in an earlier kill callback), so stop syncing to
     * everyone. Every return path of serverTick.
     * 结束同步已发出（在本 tick 内或更早的击杀回调中），停止向所有人同步。覆盖 serverTick 每个返回点。
     */
    @Inject(method = "serverTick", at = @At("RETURN"))
    private void sparkstrength$momentSyncDone(CallbackInfo ci) {
        JesterPlayerComponent jester = (JesterPlayerComponent) (Object) this;
        if (!jester.inStasis && !jester.inPsychoMode) {
            this.sparkstrength$othersSawMoment = false;
        }
    }
}
