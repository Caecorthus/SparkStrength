package annina.sparkstrength.role.coroner;

import annina.sparkstrength.role.economy.RolePassiveIncome;
import net.minecraft.server.world.ServerWorld;

/**
 * 验尸官的定时被动金币收入。
 *
 * <p>用户明确要求“照搬回溯者”，所以这里的间隔、单次金额和余额上限都直接使用
 * {@link CoronerRules} 中与回溯者一致的数值。</p>
 */
public final class CoronerEconomyService {
    private CoronerEconomyService() {
    }

    public static void tick(ServerWorld world) {
        RolePassiveIncome.tick(world, CoronerRules.PASSIVE_INCOME_INTERVAL_TICKS, CoronerRules::isCoroner,
                CoronerRules.PASSIVE_INCOME_AMOUNT, CoronerRules.PASSIVE_INCOME_BALANCE_CAP);
    }
}
