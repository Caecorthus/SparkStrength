package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.block.CircuitBreakerBlock;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.mixin.sabotage.GameWorldComponentResetAccessor;
import annina.sparkstrength.network.sabotage.SabotageStatusS2CPacket;
import annina.sparkstrength.tablet.TabletAccess;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameTimeComponent;
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
    private static final int ENGINE_UNIT_CHECK_TICKS = 20;
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
            EngineRepairService.closeAll(world, 2);
            SabotageWorldComponent.KEY.get(world).clearRoundState();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SENT_STATUS.remove(handler.player.getUuid());
            JunctionBoxService.onDisconnect(handler.player);
            BreakerRepairService.onDisconnect(handler.player);
            EngineRepairService.onDisconnect(handler.player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SENT_STATUS.remove(handler.player.getUuid());
            // Sent on the next world tick, not the next 10-tick refresh, so a frozen round timer is right at once.
            // 在下一个世界 tick 发送，而不是等下一次 10 tick 刷新，让冻结的回合倒计时一进来就是对的。
            markStatusDirty(handler.player.getServerWorld());
        });
    }

    public static void markStatusDirty(ServerWorld world) {
        DIRTY.add(world.getRegistryKey());
    }

    private static void onRoundStart(ServerWorld world) {
        JunctionBoxService.clear(world);
        BreakerRepairService.closeAll(world, 2);
        EngineRepairService.closeAll(world, 2);
        SabotageWorldComponent.KEY.get(world).beginRound(world.getTime());
        markStatusDirty(world);
    }

    private static void onRoundEnd(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        finishActive(world);
        JunctionBoxService.clear(world);
        BreakerRepairService.closeAll(world, 2);
        EngineRepairService.closeAll(world, 2);
        restoreLooks(world, sabotage.junctionBoxes(), sabotage.breakers(), sabotage.engineUnits());
        sabotage.clearRoundState();
        markStatusDirty(world);
    }

    /**
     * Ends whatever sabotage is on without a repair: round over, the round stopped, the map copied again mid-round.
     * Each type ends only its own state; no cooldown starts and nobody is credited.
     * 不经维修直接结束正在进行的破坏：对局结束、对局被停止、局中重新复制了地图。每种类型只结束自己的状态；不开始冷却，
     * 也不记任何人的功劳。
     */
    public static void finishActive(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.Outage outage = sabotage.outage();
        if (outage != null && outage.type() == SabotageType.LIGHTS) {
            LightsSabotageService.finish(world, sabotage, LightsSabotageService.EndReason.ROUND_END, null);
        } else if (outage != null && outage.type() == SabotageType.ENGINE) {
            EngineSabotageService.abort(world);
        }
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
        if (sabotage.isMapReady()) {
            // The train was copied again mid-round: fixtures are back to their template looks, so nothing on can be
            // finished or seen any more. / 局中再次复制了车厢：设施都回到了模板外观，进行中的破坏既修不了也看不见了。
            finishActive(world);
        }
        List<BlockPos> boxes = new ArrayList<>();
        List<BlockPos> breakers = new ArrayList<>();
        List<BlockPos> engineUnits = new ArrayList<>();
        SabotageBlockScan.scan(world, MapVariablesWorldComponent.KEY.get(world).getPlayArea(), (pos, state) -> {
            if (state.isOf(SparkStrengthBlocks.junctionBox())) {
                boxes.add(pos);
            } else if (state.isOf(SparkStrengthBlocks.circuitBreaker())) {
                breakers.add(pos);
            } else if (state.isOf(SparkStrengthBlocks.engineUnit())) {
                engineUnits.add(pos);
            }
        });
        restoreLooks(world, boxes, breakers, engineUnits);
        boolean autoUnlocked = sabotage.isAutoUnlocked();
        sabotage.markMapReady(boxes, breakers, engineUnits);
        boolean lightsBoxes = boxes.stream()
                .anyMatch(pos -> JunctionBoxBlock.typeOf(world.getBlockState(pos)) == SabotageType.LIGHTS);
        if (SabotageRules.startsWithLightsUnlocked(lightsBoxes)) {
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

    /** Back to intact boxes, untripped breakers and intact engine units, in case the map has no reset template. / 恢复完好的箱子、未跳闸的电闸与完好的动力装置，以防地图没有重置模板。 */
    private static void restoreLooks(ServerWorld world, List<BlockPos> boxes, List<BlockPos> breakers,
                                     List<BlockPos> engineUnits) {
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
        EngineSabotageService.setUnitLooks(world, engineUnits, EngineCondition.INTACT);
    }

    private static void tick(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE && sabotage.outage() != null) {
            // A stopped round is STOPPING well before Wathe's finalize event: end the effects the moment it leaves
            // ACTIVE. / 被停止的对局在 Wathe 的 finalize 事件之前很久就进入 STOPPING：一离开 ACTIVE 就结束效果。
            finishActive(world);
        }
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            if (!sabotage.isMapReady() && sabotage.isAwaitingMapReset()
                    && SabotageRules.mayScanWithoutCopy(world.getTime() - sabotage.roundStartTick(),
                    trainCopyPending(game), playAreaLoaded(world))) {
                // Only for a round where Wathe queued no train copy. A queued or retrying copy is waited for, so a
                // half-loaded template never looks like a map without junction boxes (onTrainReset does that scan).
                // 只用于 Wathe 没有排队车厢复制的对局。排队或重试中的复制一律等待，半加载的模板不会被当成没有接线箱的
                // 地图（那次扫描由 onTrainReset 完成）。
                initializeMap(world, sabotage);
            }
            if (sabotage.isMapReady()) {
                checkEngineUnits(world, sabotage);
                JunctionBoxService.tick(world, sabotage);
                LightsSabotageService.tick(world, sabotage);
                BreakerRepairService.tick(world);
                announceReady(world, sabotage);
            }
            // Outside the map-ready gate so tooling that forces a phase still gets its transitions and panels pruned.
            // 放在地图就绪的判断之外，这样强制切换阶段的工具也能执行阶段转换、清理面板。
            EngineSabotageService.tick(world, sabotage);
            EngineRepairService.tick(world);
        }
        syncStatus(world, sabotage);
    }

    /**
     * Engine units removed or replaced mid-round stop counting; once none is left the engine sabotage can neither be
     * unlocked nor repaired, so one in progress ends without a cooldown. Unloaded chunks are left alone, never loaded.
     * 局中被移除或替换的动力装置不再计入；一台都不剩时动力破坏既不能解锁也修不了，进行中的会直接结束，不开始冷却。
     * 未加载的区块不做判断，也绝不加载。
     */
    private static void checkEngineUnits(ServerWorld world, SabotageWorldComponent sabotage) {
        if (world.getTime() % ENGINE_UNIT_CHECK_TICKS != 0 || !sabotage.hasEngineUnits()) {
            return;
        }
        boolean dropped = sabotage.pruneEngineUnits(pos -> !world.isChunkLoaded(pos)
                || world.getBlockState(pos).isOf(SparkStrengthBlocks.engineUnit()));
        if (!dropped) {
            return;
        }
        if (!sabotage.hasEngineUnits() && sabotage.isEngineOn()) {
            EngineSabotageService.abort(world);
        }
        markStatusDirty(world);
        TabletStateService.syncToTabletHolders(world);
    }

    /** Wathe's counter is positive while a copy is queued or retrying. / Wathe 的计数在复制排队或重试中为正数。 */
    private static boolean trainCopyPending(GameWorldComponent game) {
        return ((GameWorldComponentResetAccessor) game).sparkstrength$getTicksUntilNextResetAttempt() > 0;
    }

    /** A partial scan of unloaded chunks would miss junction boxes. / 扫描未加载的区块会漏掉接线箱。 */
    private static boolean playAreaLoaded(ServerWorld world) {
        net.minecraft.util.math.Box area = MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        return area != null && world.isRegionLoaded(BlockPos.ofFloored(area.minX, area.minY, area.minZ),
                BlockPos.ofFloored(area.maxX, area.maxY, area.maxZ));
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

    /**
     * Tablet sabotage page: trigger {@code typeWire}. Both types share the checks (unlocked, armed, team cooldown, and
     * nothing else on: one sabotage at a time, Wathe's own blackout included); each type then starts itself.
     * 平板破坏页：触发 typeWire。两种类型共用同一套检查（已解锁、准备完毕、团队冷却，以及没有别的破坏：同一时间只能有一个破坏，
     * Wathe 自己的熄灯也算）；之后各类型自己启动。
     */
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
        String denied = switch (type) {
            case LIGHTS -> switch (LightsSabotageService.trigger(world, sabotage, player)) {
                case OK -> null;
                case BUSY -> "message.sparkstrength.sabotage.trigger_denied.active";
                case NO_LAMPS -> "message.sparkstrength.sabotage.no_lamps";
            };
            case ENGINE -> switch (EngineSabotageService.trigger(world, sabotage, player)) {
                case OK -> null;
                case BUSY -> "message.sparkstrength.sabotage.trigger_denied.active";
                case NO_ENGINE_UNITS -> "message.sparkstrength.sabotage.trigger_denied.no_engine_units";
            };
        };
        if (denied != null) {
            player.sendMessage(Text.translatable(denied), true);
            return;
        }
        sabotage.log(now, SabotageWorldComponent.LogKind.TRIGGERED, player.getGameProfile().getName(), type);
        markStatusDirty(world);
        TabletStateService.syncToTabletHolders(world);
    }

    /**
     * The Engineer's power restoration just reset Wathe's blackout. It only ever ends a lights outage; an engine
     * sabotage carries on in every phase.
     * 工程师的电力恢复刚刚重置了 Wathe 停电。它只会结束灯光熄灯；动力破坏在任何阶段都照常进行。
     */
    public static void onPowerRestored(ServerWorld world) {
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        LightsSabotageService.finish(world, sabotage, LightsSabotageService.EndReason.POWER_RESTORED, null);
        TabletStateService.syncToTabletHolders(world);
    }

    /**
     * Admin {@code clearCooldown} while holding the tablet: the team's sabotage timers only. A sabotage in progress
     * (an engine one on emergency power included) is left alone and still blocks new triggers.
     * 手持平板时管理员清除冷却：只清团队破坏计时。进行中的破坏（包括应急动力中的动力破坏）不受影响，照样挡住新的触发。
     */
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

    /** Send changed status right away, before anything that depends on the client already having it. / 立即发送变化的状态，先于依赖客户端已收到它的任何操作。 */
    public static void syncStatusNow(ServerWorld world) {
        DIRTY.remove(world.getRegistryKey());
        sendStatus(world, SabotageWorldComponent.KEY.get(world));
    }

    private static void syncStatus(ServerWorld world, SabotageWorldComponent sabotage) {
        boolean dirty = DIRTY.remove(world.getRegistryKey());
        if (!dirty && world.getTime() % STATUS_REFRESH_TICKS != 0) {
            return;
        }
        sendStatus(world, sabotage);
    }

    private static void sendStatus(ServerWorld world, SabotageWorldComponent sabotage) {
        SabotageWorldComponent.Outage outage = sabotage.outage();
        boolean lightsOut = outage != null && outage.type() == SabotageType.LIGHTS;
        EnginePhase enginePhase = sabotage.enginePhase(world.getTime());
        long engineTriggeredAt = enginePhase == null ? 0L : sabotage.engineTriggeredAt();
        // Coordinates for the long-range outline, only while it is drawn. / 远距离描边用的坐标，只在需要描边时下发。
        List<BlockPos> engineUnits = enginePhase == null ? List.of() : List.copyOf(sabotage.engineUnits());
        boolean resyncTimer = false;
        for (ServerPlayerEntity player : world.getPlayers()) {
            SabotageStatusS2CPacket status = new SabotageStatusS2CPacket(lightsOut, lightsOut ? outage.id() : 0,
                    lightsOut ? outage.mode().wire() : 0, sabotage.isMapReady() && SabotageAccess.canUse(player),
                    EnginePhase.wireOf(enginePhase), engineTriggeredAt, engineUnits);
            SabotageStatusS2CPacket previous = SENT_STATUS.get(player.getUuid());
            if (!status.equals(previous) && ServerPlayNetworking.canSend(player, SabotageStatusS2CPacket.ID)) {
                ServerPlayNetworking.send(player, status);
                SENT_STATUS.put(player.getUuid(), status);
                resyncTimer |= freezes(status) != (previous != null && freezes(previous));
            }
        }
        if (resyncTimer) {
            // The client just started or stopped skipping its own countdown: correct it to the server's time.
            // 客户端刚开始或停止跳过自己的倒计时：用服务端时间校正。
            GameTimeComponent.KEY.get(world).sync();
        }
    }

    private static boolean freezes(SabotageStatusS2CPacket status) {
        EnginePhase phase = EnginePhase.byWire(status.enginePhaseWire());
        return phase != null && phase.freezesRoundTimer();
    }
}
