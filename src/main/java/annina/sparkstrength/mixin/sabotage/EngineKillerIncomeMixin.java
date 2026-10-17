package annina.sparkstrength.mixin.sabotage;

import annina.sparkstrength.sabotage.EngineEconomy;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the engine is broken Wathe's killer passive income pays three times per period. The one
 * {@code PASSIVE_MONEY_TICKER.apply(world time)} call of {@code tickServerGameLoop} (Wathe 1.5.6 bytecode @82) sits
 * inside the per-player {@code canUseKillerFeatures} branch, with the loop's player in local slot 7 (slot 4 is the
 * also-ServerPlayerEntity {@code neutralWinner}, hence the explicit index). Only the boxed result changes, so both
 * unboxings, the cap check ({@code KillerPassiveMoneyCapMixin}), the killer team funds exclusion
 * ({@code KillerTeamPassiveIncomeMixin}) and SparkWitch's Raven wallet routing see the extra payouts exactly like the
 * normal one.
 * 动力损毁期间，Wathe 的杀手被动收入每个周期发三次。tickServerGameLoop 里唯一一次 PASSIVE_MONEY_TICKER.apply(世界时间)
 * 调用（Wathe 1.5.6 字节码 @82）位于每个玩家的 canUseKillerFeatures 分支内，循环中的玩家在局部变量槽位 7（槽位 4 是同为
 * ServerPlayerEntity 的 neutralWinner，所以显式写出 index）。只改装箱后的结果，因此两次拆箱、余额上限检查
 * （KillerPassiveMoneyCapMixin）、不计入杀手团队资金（KillerTeamPassiveIncomeMixin）和 SparkWitch 的黑羽鸦钱包转入，对额外
 * 发放与正常发放的处理完全一样。
 */
@Mixin(value = MurderGameMode.class, remap = false)
public abstract class EngineKillerIncomeMixin {
    @ModifyExpressionValue(
            method = "tickServerGameLoop",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1,
            allow = 1
    )
    private Object sparkstrength$tripleKillerIncomeWhileEngineBroken(Object income,
                                                                     @Local(argsOnly = true) ServerWorld world,
                                                                     @Local(index = 7) ServerPlayerEntity player) {
        return EngineEconomy.passiveIncome(income, world, player);
    }
}
