package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.agmas.noellesroles.item.PoisonNeedleItem;
import org.agmas.noellesroles.item.TimedBombItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoellesRoles timed bomb and poison needle right-clicked on a Partygoer. Both items kill a player later (the bomb's
 * timer, the needle's poison) and both drop every non-player at their first instruction, so this HEAD hook answers for
 * a Partygoer before that filter: it dies at once ({@code PartygoerWeaponHits#onTimedBomb} / {@code #onPoisonNeedle}
 * repeat the role, Coroner-disguise and cooldown gates the items and SparkStrength's Coroner redirects apply). Anything
 * else, and every refused use, runs NoellesRoles untouched.
 * NoellesRoles 定时炸弹与毒针对派对客右键使用。两者都会稍后杀死玩家（炸弹计时、毒针中毒），且都在第一条指令处丢弃所有非玩家目标，
 * 因此此 HEAD 钩子在该过滤之前为派对客作答：它立即死亡（PartygoerWeaponHits#onTimedBomb / #onPoisonNeedle 复查物品本身与
 * SparkStrength 验尸官重定向所施加的身份、验尸官伪装与冷却门槛）。其他目标以及被拒绝的使用都原样交给 NoellesRoles。
 */
@Mixin({TimedBombItem.class, PoisonNeedleItem.class})
public abstract class PartygoerNoellesItemsMixin {
    @Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$killPartygoer(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand,
                                             CallbackInfoReturnable<ActionResult> cir) {
        if (!(entity instanceof PartygoerEntity partygoer) || user == null) {
            return;
        }
        ActionResult result = (Object) this instanceof TimedBombItem
                ? PartygoerWeaponHits.onTimedBomb(user, stack, partygoer)
                : PartygoerWeaponHits.onPoisonNeedle(user, stack.getItem(), partygoer);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
