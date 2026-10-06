package annina.sparkstrength.role.pathogen;

import annina.sparkstrength.component.pathogen.PathogenStrainComponent;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Pathogen buff registration, called once from {@code SparkStrengthEvents}: economy, shop, the carrier tick, per-round
 * cleanup and the replay lines. The items, the antidote extension, the team win and the outlines are wired statically
 * (item classes, mixins, {@code PathogenClientHooks}).
 * 病原体增强注册，由 {@code SparkStrengthEvents} 调用一次：经济、商店、带毒者刻处理、单局清理与回放文本。道具、解毒剂扩展、
 * 共同胜利与描边为静态接入（道具类、mixin、{@code PathogenClientHooks}）。
 */
public final class PathogenFeatureService {
    private static boolean registered;

    private PathogenFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PathogenEconomyService.register();
        PathogenShopService.register();
        ServerTickEvents.END_WORLD_TICK.register(VirusService::tick);
        // Death ends carrying; the converted flag stays until the round is reset. / 死亡结束带毒；转化标记保留到重置。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> VirusService.clearPlayer(victim));
        ResetPlayer.EVENT.register(PathogenFeatureService::clearPlayer);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearWorld(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearWorld(world));
        registerReplayFormatters();
    }

    private static void clearPlayer(PlayerEntity player) {
        VirusService.clearPlayer(player);
        PathogenStrainComponent.KEY.get(player).clear();
    }

    private static void clearWorld(Object world) {
        if (world instanceof ServerWorld serverWorld) {
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                clearPlayer(player);
            }
        }
    }

    private static void registerReplayFormatters() {
        registerPair(VirusService.VIRUS_USED_EVENT, "replay.global.sparkstrength.pathogen_virus_used");
        registerPair(VirusService.VIRUS_SPREAD_EVENT, "replay.global.sparkstrength.pathogen_virus_spread");
        registerPair(VirusService.VIRUS_CURED_EVENT, "replay.global.sparkstrength.pathogen_virus_cured");
        registerPair(PathogenReviveService.REVIVED_EVENT, "replay.global.sparkstrength.pathogen_revived");
    }

    /** Actor and target, recorded by {@code VirusService.recordPair}. / 由 {@code VirusService.recordPair} 记录的行为者与目标。 */
    private static void registerPair(Identifier event, String key) {
        ReplayRegistry.registerGlobalEventFormatter(event, (recorded, match, world) -> {
            NbtCompound data = recorded.data();
            UUID actor = data.containsUuid("actor") ? data.getUuid("actor") : null;
            UUID target = data.containsUuid("target") ? data.getUuid("target") : null;
            if (actor == null || target == null) {
                return null;
            }
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            return Text.translatable(key,
                    ReplayGenerator.formatPlayerName(actor, playerInfoCache),
                    ReplayGenerator.formatPlayerName(target, playerInfoCache));
        });
    }
}
