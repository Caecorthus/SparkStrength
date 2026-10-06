package annina.sparkstrength.role.pathogen;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.pathogen.VirusCarrierComponent;
import annina.sparkstrength.compat.SparkFactionCompat;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.pathogen.InfectedPlayerComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Virus (病毒). Using it on a player makes that player a carrier: NoellesRoles counts the player as
 * infected, and the target sneezes at once. Every 30 s a carrier infects the nearest uninfected non-Pathogen within 3
 * blocks in line of sight and sneezes again. Those new cases are plain infections and never carriers themselves (Q1).
 * The Toxicologist's antidote cures a carrier of both the virus and the infection (Q3).
 * 病毒的服务端逻辑。对玩家使用后该玩家成为带毒者：NoellesRoles 将其视为已感染，目标立刻打喷嚏。带毒者每 30 秒感染 3 格内
 * 视线中最近的一名未感染非病原体并再次打喷嚏；被传染者只是普通感染，不会继续带毒（Q1）。毒理学家的解毒剂会同时清除
 * 带毒者的病毒与感染（Q3）。
 */
public final class VirusService {
    /** SparkFactionAPI action id for the Virus. / 病毒在 SparkFactionAPI 中的行为 id。 */
    public static final Identifier VIRUS_ACTION_ID = SparkStrength.id("pathogen_virus");
    public static final Identifier VIRUS_USED_EVENT = SparkStrength.id("pathogen_virus_used");
    public static final Identifier VIRUS_SPREAD_EVENT = SparkStrength.id("pathogen_virus_spread");
    public static final Identifier VIRUS_CURED_EVENT = SparkStrength.id("pathogen_virus_cured");

    private VirusService() {
    }

    /**
     * Server only: tries the Virus on {@code target}; true when it took effect (the caller consumes the item).
     * 仅服务端：尝试对 {@code target} 使用病毒；生效时返回 true（由调用方消耗物品）。
     */
    public static boolean useVirus(ServerPlayerEntity user, ServerPlayerEntity target) {
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        boolean userIsLivingPathogen = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && isLivingParticipant(user)
                && PathogenVisibility.isPathogen(user);
        PathogenRules.VirusVerdict verdict = PathogenRules.virusVerdict(
                userIsLivingPathogen,
                SwallowedPlayerComponent.isPlayerSwallowed(user),
                isLivingParticipant(target),
                SwallowedPlayerComponent.isPlayerSwallowed(target),
                PathogenVisibility.isPathogen(target),
                VirusCarrierComponent.isCarrier(target),
                target.getBoundingBox().squaredMagnitude(user.getEyePos()),
                user.canSee(target),
                SparkFactionCompat.canAffectPlayer(user, target, VIRUS_ACTION_ID)
        );
        switch (verdict) {
            case UNAVAILABLE -> {
                user.sendMessage(Text.translatable("message.sparkstrength.pathogen.virus_unavailable"), true);
                return false;
            }
            case PATHOGEN_TARGET -> {
                user.sendMessage(Text.translatable("message.sparkstrength.pathogen.virus_pathogen_target"), true);
                return false;
            }
            case ALREADY_CARRIER -> {
                user.sendMessage(Text.translatable("message.sparkstrength.pathogen.virus_already_carrier"), true);
                return false;
            }
            case OUT_OF_RANGE -> {
                user.sendMessage(Text.translatable("message.sparkstrength.pathogen.virus_out_of_range"), true);
                return false;
            }
            case OK -> {
            }
        }

        // An infected non-carrier is upgraded (Q9); a fresh one also gets NoellesRoles' own delayed notice.
        // 已感染但未带毒者被升级（Q9）；新感染者同时获得 NoellesRoles 自带的延迟提示。
        InfectedPlayerComponent infected = InfectedPlayerComponent.KEY.get(target);
        if (!infected.isInfected()) {
            infected.setInfected(true, user.getUuid());
        }
        VirusCarrierComponent.KEY.get(target).startCarrying(user.getUuid());
        sneeze(target);
        recordPair(user, VIRUS_USED_EVENT, target);
        user.sendMessage(Text.translatable("message.sparkstrength.pathogen.virus_applied", target.getDisplayName()),
                true);
        return true;
    }

    /** END_WORLD_TICK: carrier upkeep and the 30 s spread. / 世界刻末：带毒者维护与 30 秒传播。 */
    public static void tick(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        boolean resync = world.getTime() % PathogenRules.CARRIER_RESYNC_INTERVAL_TICKS == 0;
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            VirusCarrierComponent carrier = VirusCarrierComponent.KEY.get(player);
            if (!carrier.isCarrier()) {
                continue;
            }
            if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
                carrier.clear();
                continue;
            }
            if (resync) {
                // Toxicologists and Pathogens can appear mid-round (disguises, revives); re-send the flag.
                // 毒理学家与病原体可能在局中出现（伪装、复活）；定期重发标记。
                carrier.sync();
            }
            if (SwallowedPlayerComponent.isPlayerSwallowed(player)) {
                // Inside the Taotie NoellesRoles' own stomach spread applies; the countdown waits.
                // 在饕餮肚子里沿用 NoellesRoles 自身的胃内传染；倒计时暂停。
                continue;
            }
            int countdown = carrier.getSpreadCountdown();
            if (PathogenRules.spreadsThisTick(countdown)) {
                trySpread(world, player, carrier);
            }
            carrier.setSpreadCountdown(PathogenRules.nextSpreadCountdown(countdown));
        }
    }

    private static void trySpread(ServerWorld world, ServerPlayerEntity carrierPlayer, VirusCarrierComponent carrier) {
        double rangeSquared = PathogenRules.SPREAD_RANGE * PathogenRules.SPREAD_RANGE;
        List<PathogenRules.SpreadCandidate> candidates = new ArrayList<>();
        Map<UUID, ServerPlayerEntity> byId = new HashMap<>();
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other == carrierPlayer) {
                continue;
            }
            double distanceSquared = carrierPlayer.squaredDistanceTo(other);
            if (distanceSquared >= rangeSquared) {
                continue;
            }
            boolean eligible = isLivingParticipant(other)
                    && !PathogenVisibility.isPathogen(other)
                    && !InfectedPlayerComponent.KEY.get(other).isInfected()
                    && !SwallowedPlayerComponent.isPlayerSwallowed(other);
            candidates.add(new PathogenRules.SpreadCandidate(other.getUuid(), distanceSquared, eligible,
                    eligible && carrierPlayer.canSee(other)));
            byId.put(other.getUuid(), other);
        }
        UUID chosen = PathogenRules.pickSpreadTarget(candidates);
        ServerPlayerEntity target = chosen == null ? null : byId.get(chosen);
        if (target == null) {
            return;
        }
        UUID source = carrier.getSource() != null ? carrier.getSource() : carrierPlayer.getUuid();
        InfectedPlayerComponent.KEY.get(target).setInfected(true, source);
        sneeze(carrierPlayer);
        recordPair(carrierPlayer, VIRUS_SPREAD_EVENT, target);
    }

    /**
     * Antidote cure (Q3): clears the virus and the infection. Returns whether {@code target} was a carrier.
     * 解毒剂治疗（Q3）：清除病毒与感染。返回 {@code target} 是否为带毒者。
     */
    public static boolean cureCarrier(@Nullable PlayerEntity curer, PlayerEntity target) {
        VirusCarrierComponent carrier = VirusCarrierComponent.KEY.get(target);
        if (!carrier.isCarrier()) {
            return false;
        }
        carrier.clear();
        InfectedPlayerComponent.KEY.get(target).reset();
        if (curer instanceof ServerPlayerEntity serverCurer && target instanceof ServerPlayerEntity serverTarget) {
            recordPair(serverCurer, VIRUS_CURED_EVENT, serverTarget);
        }
        return true;
    }

    /** Death, reset and round end. / 死亡、重置与局末。 */
    public static void clearPlayer(PlayerEntity player) {
        VirusCarrierComponent.KEY.get(player).clear();
    }

    static boolean isLivingParticipant(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && !player.isSpectator() && !player.isCreative();
    }

    /** NoellesRoles' sneeze, at the sneezing player. / NoellesRoles 的喷嚏音效，在打喷嚏的玩家处播放。 */
    static void sneeze(PlayerEntity at) {
        at.getWorld().playSound(null, at.getBlockPos(), SoundEvents.ENTITY_PANDA_SNEEZE, SoundCategory.PLAYERS,
                PathogenRules.SNEEZE_VOLUME, PathogenRules.SNEEZE_PITCH);
    }

    static void recordPair(ServerPlayerEntity actor, Identifier event, ServerPlayerEntity target) {
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", target.getUuid());
        GameRecordManager.recordGlobalEvent(actor.getServerWorld(), event, actor, extra);
    }
}
