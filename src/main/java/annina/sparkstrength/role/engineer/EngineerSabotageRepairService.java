package annina.sparkstrength.role.engineer;

import annina.sparkstrength.record.BlackoutRecorder;
import annina.sparkstrength.sabotage.EngineSabotageService;
import annina.sparkstrength.sabotage.SabotageService;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 工程师破坏维修：远程修复当前的一次破坏（灯光或动力）。
 *
 * <p>购买是否成功由商店 {@code onBuy} 回调决定；购买成功后的扣钱、商店购买记录仍交给 Wathe。
 * 我们只在 {@link ShopPurchase#AFTER} 里拿到真实支付价格，然后把这笔钱平分给存活杀手。
 * 远程修复后的团队冷却由破坏系统改为较短的一档（SabotageRules.REMOTE_REPAIR_COOLDOWN_TICKS）。</p>
 */
public final class EngineerSabotageRepairService {
    private static boolean registered;

    private EngineerSabotageRepairService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ShopPurchase.AFTER.register(EngineerSabotageRepairService::afterPurchase);
    }

    /** Wathe calls {@code onBuy} on the server only. / Wathe 只在服务端调用 onBuy。 */
    public static boolean tryRepair(PlayerEntity buyer) {
        if (!(buyer instanceof ServerPlayerEntity repairer)) {
            return false;
        }
        ServerWorld world = repairer.getServerWorld();
        // The engine holds the one sabotage slot until it is repaired, in every phase.
        // 动力破坏在任何阶段都占着唯一的破坏名额，直到修好。
        if (EngineSabotageService.repairRemotely(world, repairer)) {
            return true;
        }

        WorldBlackoutComponent blackout = WorldBlackoutComponent.KEY.get(world);
        if (!blackout.isBlackoutActive()) {
            repairer.sendMessage(Text.translatable("message.sparkstrength.power_restoration.unavailable"), true);
            return false;
        }

        blackout.reset();
        clearBlackoutPotionEffects(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.playSoundToPlayer(WatheSounds.BLOCK_LIGHT_TOGGLE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        // Ends a lights sabotage too; its team cooldown becomes the short one.
        // 同时结束灯光破坏；其团队冷却改为较短的那一档。
        SabotageService.onLightsRepairedRemotely(world);
        BlackoutRecorder.restored(repairer);
        return true;
    }

    private static void afterPurchase(ServerPlayerEntity buyer, ShopEntry entry, int index, int pricePaid) {
        if (!EngineerRules.SABOTAGE_REPAIR_ENTRY_ID.equals(entry.id())) {
            return;
        }

        ServerWorld world = buyer.getServerWorld();
        List<ServerPlayerEntity> aliveKillers = aliveKillers(world);
        distributeRepairCost(aliveKillers, pricePaid);
    }

    private static void clearBlackoutPotionEffects(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Wathe 停电会给杀手/老兵夜视，给普通存活玩家失明；这里仅清理这两个停电指定效果。
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }

    private static List<ServerPlayerEntity> aliveKillers(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        List<ServerPlayerEntity> killers = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.canUseKillerFeatures(player) && GameFunctions.isPlayerPlayingAndAlive(player)) {
                killers.add(player);
            }
        }
        return killers;
    }

    private static void distributeRepairCost(List<ServerPlayerEntity> aliveKillers, int pricePaid) {
        if (aliveKillers.isEmpty() || pricePaid <= 0) {
            return;
        }

        int baseShare = pricePaid / aliveKillers.size();
        int remainder = pricePaid % aliveKillers.size();
        for (int i = 0; i < aliveKillers.size(); i++) {
            int share = baseShare + (i < remainder ? 1 : 0);
            if (share > 0) {
                PlayerShopComponent.KEY.get(aliveKillers.get(i)).addToBalance(share);
            }
        }
    }
}
