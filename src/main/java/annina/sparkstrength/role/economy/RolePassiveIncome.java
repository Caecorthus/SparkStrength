package annina.sparkstrength.role.economy;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.function.Predicate;

/**
 * Timed passive coin income shared by the Recaller and the Coroner (which copies it). Each role keeps its own interval,
 * amount, balance cap and role check in its rules class, and its own world-tick registration and listener position.
 * 回溯者与（照搬它的）验尸官共用的定时被动金币收入。各职业在自己的规则类中保留间隔、金额、余额上限与身份判定，
 * 并保留各自的世界 tick 注册与监听顺序。
 */
public final class RolePassiveIncome {
    private RolePassiveIncome() {
    }

    /**
     * On world ticks that are a multiple of {@code intervalTicks} (never when it is not positive), pays every playing,
     * living player whose current role passes {@code isRole}, outside the killer team's contribution accounting.
     * 在世界时间为 {@code intervalTicks} 整数倍的 tick（该值非正时从不发放），给当前身份通过 {@code isRole} 的每名存活局内玩家
     * 发钱，且不计入杀手阵营的贡献。
     */
    public static void tick(ServerWorld world, int intervalTicks, Predicate<Role> isRole, int amount, int balanceCap) {
        long worldTime = world.getTime();
        if (intervalTicks <= 0 || worldTime % intervalTicks != 0) {
            return;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }

            Role role = game.getRole(player);
            if (!isRole.test(role)) {
                continue;
            }

            PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
            int amountToAdd = amountToAdd(amount, balanceCap, shop.getBalance());
            if (amountToAdd > 0) {
                KillerTeamIncomeScope.withoutContribution(() -> shop.addToBalance(amountToAdd));
            }
        }
    }

    /**
     * A negative cap means no cap; otherwise the income tops the balance up to the cap at most and never past it.
     * 上限为负数表示没有上限；否则被动收入最多把余额补到上限，不会越过上限。
     */
    private static int amountToAdd(int amount, int balanceCap, int currentBalance) {
        if (amount <= 0) {
            return 0;
        }
        if (balanceCap < 0) {
            return amount;
        }
        return Math.max(0, Math.min(amount, balanceCap - currentBalance));
    }
}
