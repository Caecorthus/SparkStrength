package annina.sparkstrength.role.recaller;

import annina.sparkstrength.role.economy.RolePassiveIncome;
import net.minecraft.server.world.ServerWorld;

/**
 * 回溯者的定时被动金币收入。
 *
 * <p>这套逻辑参考 Wathe 杀手阵营的被动收入：在服务端世界 tick 中按固定间隔发钱。
 * 与完成任务奖励不同，它不依赖玩家完成任务，只要求玩家当前仍是存活的回溯者。</p>
 */
public final class RecallerEconomyService {
    private RecallerEconomyService() {
    }

    public static void tick(ServerWorld world) {
        RolePassiveIncome.tick(world, RecallerShopRules.PASSIVE_INCOME_INTERVAL_TICKS, RecallerShopRules::isRecaller,
                RecallerShopRules.PASSIVE_INCOME_AMOUNT, RecallerShopRules.PASSIVE_INCOME_BALANCE_CAP);
    }
}
