package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.role.economy.KillerTeamEconomyService;
import annina.sparkstrength.role.economy.KillerTeamIncomeScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps Wathe's personal balance and purchase callbacks intact; the service owns team authority.
 * 保留 Wathe 个人余额与购买回调；团队资金权限由服务端服务判定。
 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class KillerTeamShopMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Shadow
    public int balance;

    @WrapMethod(method = "addToBalance", require = 1, allow = 1)
    private void sparkstrength$recordActualIncome(int amount, Operation<Void> original) {
        int before = balance;
        original.call(amount);
        long actualDelta = (long) balance - before;
        if (actualDelta > 0 && !KillerTeamIncomeScope.isSuppressed()) {
            KillerTeamEconomyService.recordIncome(player, actualDelta);
        }
    }

    @WrapOperation(
            method = "canAffordAndBuy",
            at = @At(value = "FIELD", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;balance:I",
                    opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 1,
            allow = 1
    )
    private int sparkstrength$includeTeamPurchaseFunds(PlayerShopComponent shop, Operation<Integer> original) {
        return KillerTeamEconomyService.availableForPurchase(player, original.call(shop));
    }

    @WrapOperation(
            method = "completePurchase",
            at = @At(value = "FIELD", target = "Ldev/doctor4t/wathe/cca/PlayerShopComponent;balance:I",
                    opcode = Opcodes.PUTFIELD, ordinal = 0),
            require = 1,
            allow = 1
    )
    private void sparkstrength$payPersonalBeforeTeam(PlayerShopComponent shop, int deductedBalance,
                                                   Operation<Void> original, ServerPlayerEntity buyer,
                                                   ShopEntry entry, int index, int pricePaid) {
        // Keep the full final price for onBuy/AFTER; only split the committed debit here.
        // onBuy/AFTER 保留完整最终价格；只在实际扣款处拆分个人与团队支付。
        original.call(shop, KillerTeamEconomyService.personalBalanceAfterPurchase(player, balance, pricePaid));
    }
}
