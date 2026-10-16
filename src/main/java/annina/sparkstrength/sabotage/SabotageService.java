package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.block.CircuitBreakerBlock;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.sabotage.SabotageStatusS2CPacket;
import annina.sparkstrength.tablet.TabletAccess;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Round lifecycle, triggering, unlocking and per-player status of the sabotage system. Server thread only.
 * 破坏系统的回合生命周期、触发、解锁与按玩家的状态同步。仅限服务端线程。
 *
 * <p>Wathe only queues the train copy at round start, so the map is scanned once the copy really finished
 * ({@link #onTrainReset}), with a short fallback for maps whose copy never succeeds.
 * Wathe 开局只是把车厢复制排进队列，因此等复制真正完成（onTrainReset）才扫描地图；复制始终不成功的地图有一个短暂的兜底。</p>
 */
public final class SabotageService {
    private static final int STATUS_REFRESH_TICKS = 10;
    private static final Map<UUID, SabotageStatusS2CPacket> SENT_STATUS = new HashMap<>();
    private static final Set<RegistryKey<World>> DIRTY = new HashSet<>();
    private static boolean registered;

    private SabotageService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                onRoundStart(serverWorld);
            }
        });
        GameEvents.ON_WIN_DETERMINED.register((world, game, winStatus, neutralWinner) -> {
            if (world instanceof ServerWorld serverWorld) {
                onRoundEnd(serverWorld);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                onRoundEnd(serverWorld);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(SabotageService::tick);
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            JunctionBoxService.clear(world);
            BreakerRepairService.closeAll(world, 2);
            SabotageWorldComponent.KEY.get(world).clearRoundState();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SENT_STATUS.remove(handler.player.getUuid());
            JunctionBoxService.onDisconnect(handler.player);
            BreakerRepairService.onDisconnect(handler.player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                SENT_STATUS.remove(handler.player.getUuid()));
    }

    public static void markStatusDirty(ServerWorld world) {
        DIRTY.add(world.getRegistryKey());
    }

    private static void onRoundStart(ServerWorld world) {
        JunctionBoxService.clear(world);
        BreakerRepairService.closeAll(world, 2);
        SabotageWorldComponent.KEY.get(world).beginRound(world.getTime());
        markStatusDirty(world);
    }

    private static void onRoundEnd(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        LightsSabotageService.finish(world, sabotage, LightsSabotageService.EndReason.ROUND_END, null);
        JunctionBoxService.clear(world);
        BreakerRepairService.closeAll(world, 2);
        restoreLooks(world, sabotage.junctionBoxes(), sabotage.breakers());
        sabotage.clearRoundState();
        markStatusDirty(world);
    }

    /** Wathe's train copy for this round just succeeded. / 本局的 Wathe 车厢复制刚刚成功。 */
    public static void onTrainReset(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        if (!game.isRunning() && !sabotage.isAwaitingMapReset()) {
            return;
        }
        initializeMap(world, sabotage);
    }

    private static void initializeMap(ServerWorld world, SabotageWorldComponent sabotage) {
        List<BlockPos> boxes = new ArrayList<>();
        List<BlockPos> breakers = new ArrayList<>();
        SabotageBlockScan.scan(world, MapVariablesWorldComponent.KEY.get(world).getPlayArea(), (pos, state) -> {
            if (state.isOf(SparkStrengthBlocks.junctionBox())) {
                boxes.add(pos);
            } else {
                breakers.add(pos);
            }
        });
        restoreLooks(world, boxes, breakers);
        boolean autoUnlocked = sabotage.isAutoUnlocked();
        sabotage.markMapReady(boxes, breakers);
        if (SabotageRules.startsWithLightsUnlocked(!boxes.isEmpty())) {
            // Made before junction boxes existed: lights work as before, from the tablet, after the usual wait.
            // 在接线箱出现之前做的地图：灯光照旧可用，入口在平板，同样要等待解锁时间。
            sabotage.autoUnlock(SabotageType.LIGHTS, world.getTime() + SabotageRules.UNLOCK_DELAY_TICKS);
        } else if (autoUnlocked) {
            // A fallback scan ran before the copy finished; the real map has junction boxes after all.
            // 兜底扫描早于复制完成；真正的地图其实有接线箱。
            sabotage.revokeAutoUnlock();
        }
        markStatusDirty(world);
    }

    /** Back to intact boxes and untripped breakers, in case the map has no reset template. / 恢复完好的箱子与未跳闸的电闸，以防地图没有重置模板。 */
    private static void restoreLooks(ServerWorld world, List<BlockPos> boxes, List<BlockPos> breakers) {
        for (BlockPos pos : boxes) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(SparkStrengthBlocks.junctionBox())
                    && state.get(JunctionBoxBlock.STATE) != JunctionBoxState.CLOSED) {
                world.setBlockState(pos, state.with(JunctionBoxBlock.STATE, JunctionBoxState.CLOSED), Block.NOTIFY_ALL);
            }
        }
        for (BlockPos pos : breakers) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(SparkStrengthBlocks.circuitBreaker()) && state.get(CircuitBreakerBlock.TRIPPED)) {
                world.setBlockState(pos, state.with(CircuitBreakerBlock.TRIPPED, false), Block.NOTIFY_ALL);
            }
        }
    }

    private static void tick(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            if (!sabotage.isMapReady() && sabotage.isAwaitingMapReset()
                    && world.getTime() - sabotage.roundStartTick() >= SabotageRules.MAP_READY_FALLBACK_TICKS) {
                initializeMap(world, sabotage);
            }
            if (sabotage.isMapReady()) {
                JunctionBoxService.tick(world, sabotage);
                LightsSabotageService.tick(world, sabotage);
                BreakerRepairService.tick(world);
                announceReady(world, sabotage);
            }
        }
        syncStatus(world, sabotage);
    }

    private static void announceReady(ServerWorld world, SabotageWorldComponent sabotage) {
        long now = world.getTime();
        for (SabotageType type : sabotage.unlocked()) {
            if (now >= sabotage.readyTick(type) && sabotage.markReadyAnnounced(type)) {
                sabotage.log(now, SabotageWorldComponent.LogKind.READY, "", type);
                Text message = Text.translatable("message.sparkstrength.sabotage.ready",
                        Text.translatable(type.translationKey())).formatted(Formatting.GOLD);
                for (ServerPlayerEntity player : saboteurs(world)) {
                    player.sendMessage(message, false);
                    player.playSoundToPlayer(SparkStrengthSounds.SABOTAGE_READY, SoundCategory.PLAYERS, 0.6F, 1.2F);
                }
                TabletStateService.syncToTabletHolders(world);
            }
        }
    }

    /** A junction box of {@code type} was just hacked by {@code actor}. / 某个 type 类接线箱刚被 actor 入侵。 */
    static void unlock(ServerWorld world, SabotageWorldComponent sabotage, SabotageType type, ServerPlayerEntity actor) {
        long now = world.getTime();
        boolean fresh = !sabotage.isUnlocked(type);
        sabotage.unlock(type, now + SabotageRules.UNLOCK_DELAY_TICKS);
        sabotage.log(now, SabotageWorldComponent.LogKind.BOX_HACKED, actor.getGameProfile().getName(), type);
        if (fresh) {
            Text message = Text.translatable("message.sparkstrength.sabotage.unlocked", actor.getGameProfile().getName(),
                    Text.translatable(type.translationKey()), SabotageRules.UNLOCK_DELAY_TICKS / 20)
                    .formatted(Formatting.GOLD);
            for (ServerPlayerEntity player : saboteurs(world)) {
                player.sendMessage(message, false);
            }
        }
        TabletStateService.syncToTabletHolders(world);
    }

    /** Tablet sabotage page: trigger {@code typeWire}. / 平板破坏页：触发 typeWire。 */
    public static void handleTrigger(ServerPlayerEntity player, int typeWire) {
        SabotageType type = SabotageType.byWire(typeWire);
        if (type == null || !SabotageAccess.canUse(player) || !TabletAccess.hasTabletInHotbar(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        if (!sabotage.isMapReady()) {
            return;
        }
        long now = world.getTime();
        boolean busy = sabotage.outage() != null || WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
        SabotageRules.TriggerCheck check = SabotageRules.canTrigger(sabotage.isUnlocked(type),
                sabotage.readyTick(type), sabotage.teamCooldownEndTick(), busy, now);
        if (check != SabotageRules.TriggerCheck.OK) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.trigger_denied."
                    + check.name().toLowerCase(java.util.Locale.ROOT)), true);
            return;
        }
        LightsSabotageService.TriggerResult result = LightsSabotageService.trigger(world, sabotage, player);
        switch (result) {
            case OK -> {
                sabotage.log(now, SabotageWorldComponent.LogKind.TRIGGERED, player.getGameProfile().getName(), type);
                markStatusDirty(world);
                TabletStateService.syncToTabletHolders(world);
            }
            case BUSY -> player.sendMessage(
                    Text.translatable("message.sparkstrength.sabotage.trigger_denied.active"), true);
            case NO_LAMPS -> player.sendMessage(
                    Text.translatable("message.sparkstrength.sabotage.no_lamps"), true);
        }
    }

    /** The Engineer's power restoration just reset Wathe's blackout. / 工程师的电力恢复刚刚重置了 Wathe 停电。 */
    public static void onPowerRestored(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        LightsSabotageService.finish(world, sabotage, LightsSabotageService.EndReason.POWER_RESTORED, null);
        TabletStateService.syncToTabletHolders(world);
    }

    /** Admin {@code clearCooldown} while holding the tablet: the team's sabotage timers. / 手持平板时管理员清除冷却：清掉团队破坏计时。 */
    public static void onAdminCooldownCleared(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        long now = world.getTime();
        sabotage.setTeamCooldownEndTick(now);
        for (SabotageType type : sabotage.unlocked()) {
            if (sabotage.readyTick(type) > now) {
                sabotage.setReadyTick(type, now);
            }
        }
        TabletStateService.syncToTabletHolders(world);
    }

    /** Everyone who may sabotage right now, Undercover included. / 当前可以破坏的所有人，包括卧底。 */
    static List<ServerPlayerEntity> saboteurs(ServerWorld world) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (SabotageAccess.canUse(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private static void syncStatus(ServerWorld world, SabotageWorldComponent sabotage) {
        boolean dirty = DIRTY.remove(world.getRegistryKey());
        if (!dirty && world.getTime() % STATUS_REFRESH_TICKS != 0) {
            return;
        }
        SabotageWorldComponent.Outage outage = sabotage.outage();
        boolean lightsOut = outage != null && outage.type() == SabotageType.LIGHTS;
        for (ServerPlayerEntity player : world.getPlayers()) {
            SabotageStatusS2CPacket status = new SabotageStatusS2CPacket(lightsOut, lightsOut ? outage.id() : 0,
                    lightsOut ? outage.mode().wire() : 0, sabotage.isMapReady() && SabotageAccess.canUse(player));
            if (!status.equals(SENT_STATUS.get(player.getUuid()))
                    && ServerPlayNetworking.canSend(player, SabotageStatusS2CPacket.ID)) {
                ServerPlayNetworking.send(player, status);
                SENT_STATUS.put(player.getUuid(), status);
            }
        }
    }
}
