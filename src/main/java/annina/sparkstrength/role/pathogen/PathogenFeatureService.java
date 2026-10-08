package annina.sparkstrength.role.pathogen;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.pathogen.PathogenStrainComponent;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Pathogen buff registration, called once from {@code SparkStrengthEvents}: economy, shop, the carrier tick, per-round
 * cleanup, the converted Pathogens' text mute and the replay lines. The items, the antidote extension, the team win and the outlines are wired statically
 * (item classes, mixins, {@code PathogenClientHooks}).
 * 病原体增强注册，由 {@code SparkStrengthEvents} 调用一次：经济、商店、带毒者刻处理、单局清理、转化病原体的文字禁言与回放文本。道具、解毒剂扩展、
 * 共同胜利与描边为静态接入（道具类、mixin、{@code PathogenClientHooks}）。
 */
public final class PathogenFeatureService {
    /**
     * Runs before every default-phase chat listener, so a muted Pathogen's line is dropped before NoellesRoles' Taotie
     * stomach or Noisemaker relays (or SparkStrength's Reporter channel) can forward it.
     * 先于所有默认阶段的聊天监听器执行，使被禁言病原体的消息在 NoellesRoles 饕餮胃内 / 大嗓门转发（以及 SparkStrength
     * 记者频道）转发之前就被丢弃。
     */
    static final Identifier MUTE_PHASE = SparkStrength.id("pathogen_mute");
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
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.addPhaseOrdering(MUTE_PHASE, Event.DEFAULT_PHASE);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(MUTE_PHASE,
                (message, sender, params) -> mayUseText(sender));
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.addPhaseOrdering(MUTE_PHASE, Event.DEFAULT_PHASE);
        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register(MUTE_PHASE,
                (message, source, params) -> source.getPlayer() == null || mayUseText(source.getPlayer()));
        registerReplayFormatters();
    }

    /** Text half of the 2026-10-07 mute; voice lives in the voice chat plugin. / 禁言的文字部分；语音部分见语音插件。 */
    private static boolean mayUseText(ServerPlayerEntity sender) {
        if (!PathogenVisibility.isMuted(sender)) {
            return true;
        }
        sender.sendMessage(Text.translatable("message.sparkstrength.pathogen.muted"), true);
        return false;
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
