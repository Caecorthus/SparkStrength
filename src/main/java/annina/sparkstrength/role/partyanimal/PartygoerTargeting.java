package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.component.partyanimal.PartyBalloonMarkComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.partyanimal.nav.TrainNavGrid;
import annina.sparkstrength.role.partyanimal.nav.TrainPathfinder;
import annina.sparkstrength.role.undercover.UndercoverRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who a Partygoer may hunt, and which of them is nearest on foot.
 * 派对客可以猎捕谁，以及其中按步行距离最近的是谁。
 */
public final class PartygoerTargeting {
    private PartygoerTargeting() {
    }

    /** See {@link PartyAnimalRules#isValidPrey}. / 见 PartyAnimalRules#isValidPrey。 */
    public static boolean isValidPrey(ServerPlayerEntity player, @Nullable UUID summoner) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return PartyAnimalRules.isValidPrey(
                GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player),
                SwallowedPlayerComponent.isPlayerSwallowed(player),
                player.getUuid().equals(summoner),
                SparkTraitsCompat.isEffectiveKiller(game.getRole(player), player),
                UndercoverRoles.isUndercover(player),
                UndercoverRoles.isStage2(player),
                HeliumBuzzPlayerComponent.KEY.get(player).isActive(),
                PartyBalloonMarkComponent.isMarked(player),
                player.getVehicle() instanceof PartygoerEntity
        );
    }

    /**
     * Whether {@code killer} is the summoner or on the summoner's side, so the Partygoer won't rage at them. An offline
     * summoner is judged by its role alone.
     * killer 是否是召唤者本人或与召唤者同一方，是则派对客不会对其狂怒。召唤者离线时只按其身份判断。
     */
    public static boolean isSummonerTeam(ServerPlayerEntity killer, UUID summonerUuid,
                                         @Nullable ServerPlayerEntity summoner) {
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getWorld());
        PartyAnimalRules.Side summonerSide;
        if (summoner != null) {
            summonerSide = side(game, summoner);
        } else {
            Role role = game.getRole(summonerUuid);
            summonerSide = PartyAnimalRules.side(role != null && role.canUseKiller(), role != null && role.isInnocent(),
                    UndercoverRoles.isUndercover(role), false);
        }
        return PartyAnimalRules.isSummonerTeam(killer.getUuid().equals(summonerUuid), summonerSide, side(game, killer));
    }

    private static PartyAnimalRules.Side side(GameWorldComponent game, ServerPlayerEntity player) {
        Role role = game.getRole(player);
        return PartyAnimalRules.side(SparkTraitsCompat.isEffectiveKiller(role, player),
                SparkTraitsCompat.isEffectiveCivilian(role, player),
                UndercoverRoles.isUndercover(player), UndercoverRoles.isStage2(player));
    }

    /**
     * A player the Partygoer can still chase or eat (the rage target): alive, playing and not inside a Taotie.
     * 派对客仍可追或吃的玩家（狂怒目标）：存活、在局内且不在饕餮肚子里。
     */
    public static boolean isReachable(@Nullable ServerPlayerEntity player) {
        return player != null && !player.isRemoved()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && !SwallowedPlayerComponent.isPlayerSwallowed(player);
    }

    /**
     * The valid prey nearest to {@code fromCell} by walking distance; null when none can be reached.
     * 按步行距离离 fromCell 最近的合法猎物；没有可达者时为 null。
     */
    public static @Nullable ServerPlayerEntity nearestPrey(ServerWorld world, TrainNavGrid grid, long fromCell,
                                                          @Nullable UUID summoner) {
        Map<Long, ServerPlayerEntity> byCell = new HashMap<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isValidPrey(player, summoner)) {
                continue;
            }
            long cell = grid.snap(player.getX(), player.getY(), player.getZ());
            if (cell != Long.MIN_VALUE) {
                byCell.putIfAbsent(cell, player);
            }
        }
        if (byCell.isEmpty()) {
            return null;
        }
        long nearest = TrainPathfinder.nearest(grid, fromCell, byCell.keySet(), TrainPathfinder.DEFAULT_BUDGET);
        return nearest == Long.MIN_VALUE ? null : byCell.get(nearest);
    }

    /** Whether any player could be hunted right now (cheap pre-check, no pathing). / 当前是否存在可猎捕的玩家（不寻路的廉价预检）。 */
    public static boolean anyValidPrey(ServerWorld world, @Nullable UUID summoner) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isValidPrey(player, summoner)) {
                return true;
            }
        }
        return false;
    }
}
