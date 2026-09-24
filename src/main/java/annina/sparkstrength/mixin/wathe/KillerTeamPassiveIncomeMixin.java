package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.role.economy.KillerTeamIncomeScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.gamemode.MurderGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses only Wathe's timed receipt, not action rewards reached during the same tick.
 * 仅排除 Wathe 定时入账，不排除同一 tick 内触发的行动奖励。
 */
@Mixin(value = MurderGameMode.class, remap = false)
public abstract class KillerTeamPassiveIncomeMixin {
    @WrapOperation(
            method = "tickServerGameLoop",
            at = @At(value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;addToBalance(I)V", ordinal = 0),
            require = 1,
            allow = 1
    )
    private void sparkstrength$excludeTimedIncome(PlayerShopComponent shop, int amount, Operation<Void> original) {
        KillerTeamIncomeScope.withoutContribution(() -> original.call(shop, amount));
    }
}
