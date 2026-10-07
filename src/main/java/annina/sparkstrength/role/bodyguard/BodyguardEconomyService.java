package annina.sparkstrength.role.bodyguard;

import annina.sparkstrength.component.bodyguard.BodyguardGearComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.bodyguard.BodyguardPlayerComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

import java.util.UUID;

/**
 * The Bodyguard loses 50 coins when its protected target (the Serial Killer's target) dies or is swallowed.
 * Reads getCurrentTarget(), which the Bodyguard only refreshes in its own tick, so during the death it still names the
 * target NoellesRoles is just reassigning.
 * 保镖的保护目标（连环杀手的目标）死亡或被吞时，保镖扣 50 金币。读取 getCurrentTarget()：保镖只在自己的 tick 刷新它，
 * 所以在死亡结算时它仍是 NoellesRoles 正在重新分配的那个目标。
 */
public final class BodyguardEconomyService {
    private static boolean registered;

    private BodyguardEconomyService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register((victim, killer, deathReason) -> onTargetGone(victim));
    }

    /** A player died or was swallowed; charge every Bodyguard guarding them. / 有玩家死亡或被吞；向守护其的保镖扣款。 */
    public static void onTargetGone(ServerPlayerEntity victim) {
        if (victim == null) {
            return;
        }
        UUID victimId = victim.getUuid();
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getServerWorld());
        for (ServerPlayerEntity bodyguard : victim.getServerWorld().getPlayers()) {
            if (bodyguard == victim || !BodyguardRules.isBodyguard(game.getRole(bodyguard))) {
                continue;
            }
            BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(bodyguard);
            boolean target = victimId.equals(BodyguardPlayerComponent.KEY.get(bodyguard).getCurrentTarget());
            if (!BodyguardRules.shouldPenalize(
                    GameFunctions.isPlayerPlayingAndAlive(bodyguard),
                    SwallowedPlayerComponent.isPlayerSwallowed(bodyguard),
                    SparkTraitsCompat.hasImpostor(bodyguard),
                    target,
                    gear.wasPenalized(victimId))) {
                continue;
            }
            gear.markPenalized(victimId);
            PlayerShopComponent shop = PlayerShopComponent.KEY.get(bodyguard);
            int amount = BodyguardRules.penaltyAmount(shop.getBalance());
            if (amount > 0) {
                shop.addToBalance(-amount);
            }
            bodyguard.sendMessage(Text.translatable("tip.sparkstrength.bodyguard.target_lost", amount)
                    .formatted(Formatting.RED), true);
        }
    }
}
