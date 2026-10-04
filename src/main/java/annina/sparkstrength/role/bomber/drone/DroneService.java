package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.item.DroneItem;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import annina.sparkstrength.role.coroner.CoronerRules;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Round lifecycle and ownership registry for Bomber drones. Everything is transient and per {@link ServerWorld}.
 * 炸弹客无人机的回合生命周期与归属登记。全部为临时状态，按 {@link ServerWorld} 区分。
 *
 * <ul>
 *   <li>Round identity reuses {@code M67RoundService.currentRoundId}, but only while the match is ACTIVE (no lobby
 *   drones); drones of a stale round are discarded.
 *   回合标识复用 M67 回合 id，但仅在对局 ACTIVE 时有效（大厅无无人机）；旧回合的无人机会被移除。</li>
 *   <li>Starter grant: every real Bomber gets exactly one grenade drone into the hotbar, at {@code ON_FINISH_INITIALIZE}
 *   and by a reconciliation pass every 20 ticks while ACTIVE; once per player per round, skipped if they already hold
 *   one anywhere (cursor included), have one placed, or have one waiting to be returned.
 *   开局发放：每名真实炸弹客获得一架投弹无人机（仅快捷栏），在开局时发放，并在 ACTIVE 期间每 20 刻对账补发；每人每局一次，
 *   若已持有（含光标）、已放置或正在等待归还则跳过。</li>
 *   <li>Opening lock: both drone items carry the M67 opening cooldown (90 s), kept synced every tick.
 *   开局锁：两种无人机物品带有 M67 开局冷却（90 秒），每刻同步。</li>
 *   <li>{@link #returnGrenadeDrone}: a destroyed/depleted grenade drone goes back into the owner's hotbar, queued until a
 *   hotbar slot frees up (or the owner is back in survival); queued returns show on the owner's tablet.
 *   被毁或耗尽的投弹无人机回到主人快捷栏，无空位（或主人暂不在生存模式）时排队等待；排队中的归还显示在主人平板上。</li>
 *   <li>Round end, phase change, world unload: pilot sessions end, then every drone is discarded.
 *   回合结束、阶段变化、世界卸载：先结束驾驶会话，再移除所有无人机。</li>
 *   <li>Idle keep-alive: every live drone of the active round holds a small expiring chunk ticket, so a drone nobody
 *   is near keeps ticking (hover drain, owner-loss checks) and its chunk never unloads under it.
 *   空闲保活：本回合每架存活无人机都持有一张会过期的小范围区块票据，使无人附近的无人机照常 tick（悬停耗电、主人失联检查），
 *   其所在区块也不会被卸载。</li>
 * </ul>
 */
public final class DroneService {
    private static final int GRANT_RECONCILE_INTERVAL_TICKS = 20;
    /**
     * Radius 2 makes the drone's own chunk entity-ticking (level 31); refreshed every tick for live drones, so a
     * removed drone or an ended round simply lets it expire (no bookkeeping). Argument: the drone's entity id.
     * 半径 2 使无人机所在区块达到实体刻等级（31）；存活无人机每刻刷新，因此无人机被移除或回合结束后票据自行过期（无需记录）。参数为无人机实体 id。
     */
    private static final int IDLE_TICKET_RADIUS = 2;
    private static final ChunkTicketType<Integer> IDLE_TICKET =
            ChunkTicketType.create(SparkStrength.MOD_ID + ":drone_idle", Integer::compare, 40);
    private static final Map<ServerWorld, Round> ROUNDS = new IdentityHashMap<>();
    private static boolean registered;

    private DroneService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Registered after M67RoundService.initialize (SparkStrengthEvents order), so the M67 round already exists here.
        // 注册顺序在 M67RoundService.initialize 之后，此时 M67 回合已经建立。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                startRound(serverWorld, game);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                endRound(serverWorld);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(DroneService::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncOpening(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> disconnect(handler.player));
        ServerWorldEvents.UNLOAD.register((server, world) -> endRound(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerWorld world : ROUNDS.keySet().toArray(ServerWorld[]::new)) {
                endRound(world);
            }
        });
    }

    /** Real round role is the NoellesRoles Bomber (Coroner disguises never count). / 真实局内身份为炸弹客（验尸官伪装不算）。 */
    public static boolean isRealBomber(@Nullable PlayerEntity player) {
        return player != null && CoronerRules.isBomber(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Real Bomber who is online, alive and playing in survival: the only player who may place, pilot or pick up drones
     * and receive a returned grenade drone into the hotbar right now.
     * 在线、存活、参与对局且处于生存状态的真实炸弹客：唯一可以此刻放置、驾驶、回收无人机并把归还的投弹无人机放入快捷栏的玩家。
     */
    public static boolean isActiveBomber(@Nullable PlayerEntity player) {
        return keepsDrones(player) && GameFunctions.isPlayerAliveAndSurvival(player);
    }

    /**
     * Real Bomber still alive in the round per Wathe, even while temporarily out of survival (NoellesRoles' Taotie
     * swallow switches the victim to SPECTATOR without killing it): keeps control of placed drones and keeps queued
     * grenade drone returns. Only death, disconnect or losing the Bomber role makes drones lose control.
     * 按 Wathe 判定仍在对局中存活的真实炸弹客，即使暂时不在生存模式（NoellesRoles 饕餮吞噬会把对象切成旁观且不杀死）：
     * 保留已放置无人机的控制权与排队中的投弹无人机归还。只有死亡、断线或失去炸弹客身份才会使无人机失控。
     */
    public static boolean keepsDrones(@Nullable PlayerEntity player) {
        return player != null && !player.isRemoved() && player.isAlive()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && isRealBomber(player);
    }

    /**
     * Current match round, or null outside an ACTIVE match. Never mints M67's lobby round.
     * 当前对局回合；对局未处于 ACTIVE 时为 null。不会创建 M67 的大厅回合。
     */
    public static @Nullable UUID currentRoundId(ServerWorld world) {
        if (GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return null;
        }
        return M67RoundService.currentRoundId(world);
    }

    /** Remaining round-start lock in ticks (0 when free). / 剩余开局锁刻数（0 表示已解锁）。 */
    public static int openingRemaining(ServerWorld world) {
        return M67RoundService.openingRemaining(world);
    }

    /** Register a freshly spawned drone; drones of another round are discarded. / 登记刚生成的无人机；其他回合的无人机直接移除。 */
    public static void track(DroneEntity drone) {
        if (!(drone.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Round round = activeRound(world);
        if (round == null || drone.roundId() == null || !drone.roundId().equals(round.id)) {
            drone.discard();
            return;
        }
        round.drones.add(drone);
    }

    /** Live drones owned by this player in the current round. / 该玩家在本回合场上的无人机。 */
    public static List<DroneEntity> dronesOf(ServerWorld world, UUID ownerUuid) {
        Round round = activeRound(world);
        if (round == null || ownerUuid == null) {
            return List.of();
        }
        List<DroneEntity> owned = new ArrayList<>();
        for (DroneEntity drone : round.drones) {
            if (!drone.isRemoved() && ownerUuid.equals(drone.ownerUuid())) {
                owned.add(drone);
            }
        }
        return owned;
    }

    /** Opening lock over and fewer than the allowed undetonated bomb drones in play. / 开局锁已过且场上未引爆炸弹无人机未达上限。 */
    public static boolean canPlaceBombDrone(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (openingRemaining(world) > 0) {
            return false;
        }
        int live = 0;
        for (DroneEntity drone : dronesOf(world, player.getUuid())) {
            if (drone.kind() == DroneKind.BOMB) {
                live++;
            }
        }
        return live < DroneRules.MAX_ACTIVE_BOMB_DRONES;
    }

    /**
     * The owner picks a placed grenade drone back up: its pilot session ends, the item (same charge and payload) goes
     * into a free hotbar slot and the drone is removed. Refused while Engineer-stunned or SparkTraits-interaction
     * blocked, and with a tip when the hotbar is full.
     * 主人收回已放置的投弹无人机：结束其驾驶会话，物品（保持电量与挂载）放入空闲快捷栏格，并移除实体。被工程师定身或被 SparkTraits
     * 阻止交互时拒绝；快捷栏已满时提示并拒绝。
     */
    public static boolean recoverGrenadeDrone(ServerPlayerEntity owner, DroneEntity drone) {
        ServerWorld world = owner.getServerWorld();
        Round round = activeRound(world);
        if (round == null || drone.isRemoved() || drone.getWorld() != world || drone.kind() != DroneKind.GRENADE
                || drone.state() == DroneState.FALLING || !owner.getUuid().equals(drone.ownerUuid())
                || !round.id.equals(drone.roundId()) || !isActiveBomber(owner)
                || EngineerStunnedPlayerComponent.KEY.get(owner).isStunned()
                || SparkTraitsCompat.isKillerInteractionBlocked(owner)) {
            return false;
        }
        int slot = freeHotbarSlot(owner);
        if (slot < 0) {
            owner.sendMessage(Text.translatable("tip.sparkstrength.drone.hotbar_full"), true);
            return false;
        }
        ItemStack stack = new ItemStack(SparkStrengthItems.grenadeDrone());
        DroneItem.setCharge(stack, drone.charge());
        DroneItem.setPayload(stack, drone.hasPayload());
        // Camera back to the body before the entity goes away. / 实体移除前先把镜头还给本体。
        DronePilotService.endFor(drone, DronePilotEndReason.EXIT);
        owner.getInventory().setStack(slot, stack);
        world.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SparkStrengthSounds.DRONE_PLACE,
                SoundCategory.PLAYERS, 0.8F, 1.3F);
        round.drones.remove(drone);
        drone.discard();
        return true;
    }

    /**
     * Give the grenade drone back to its owner with this charge and cooldown (cooldown 0 for a plain recovery). No-op
     * when the owner is offline, dead or no longer the real Bomber ({@link #keepsDrones}); queued until the owner is
     * back in survival with a free hotbar slot otherwise. Payload never comes back (the bound M67 was dropped by the
     * caller).
     * 把投弹无人机以指定电量与冷却归还给主人（正常回收时冷却为 0）。主人离线、死亡或已不是真实炸弹客时不做任何事；否则排队，
     * 直到主人回到生存模式且快捷栏有空位。挂载不随之归还（绑定的 M67 已由调用方掉落）。
     */
    public static void returnGrenadeDrone(ServerWorld world, UUID ownerUuid, int charge, int cooldownTicks) {
        Round round = activeRound(world);
        if (round == null || ownerUuid == null) {
            return;
        }
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
        if (!keepsDrones(owner) || owner.getServerWorld() != world) {
            return;
        }
        long now = world.getTime();
        int cooldown = Math.max(0, cooldownTicks);
        PendingReturn pending = new PendingReturn(DroneRules.clampCharge(charge), now, now + cooldown);
        if (cooldown > 0) {
            // Start the native clock now; queued returns are also listed on the tablet (pendingReturns).
            // 立即开始原版冷却计时；排队中的归还也会列在平板上（见 pendingReturns）。
            preserveCooldown(owner, SparkStrengthItems.grenadeDrone(), cooldown);
        }
        List<PendingReturn> queue = round.returns.get(ownerUuid);
        boolean active = isActiveBomber(owner);
        if ((queue == null || queue.isEmpty()) && active && deliver(world, owner, pending)) {
            return;
        }
        round.returns.computeIfAbsent(ownerUuid, uuid -> new ArrayList<>()).add(pending);
        if (active) {
            // Only the hotbar holds it back; an owner out of survival just gets it later. / 仅因快捷栏已满才提示；不在生存模式的主人稍后自动取回。
            owner.sendMessage(Text.translatable("tip.sparkstrength.drone.return_pending"), true);
        }
    }

    /**
     * Queued grenade drone returns of this owner (hotbar full or owner out of survival), oldest first: charge as it
     * would be delivered now and remaining cooldown. Read by the owner's own tablet only.
     * 该主人排队中的投弹无人机归还（快捷栏已满或主人不在生存模式），按先后顺序：此刻交付时的电量与剩余冷却。仅供主人自己的平板读取。
     */
    public static List<PendingReturnStatus> pendingReturns(ServerWorld world, UUID ownerUuid) {
        Round round = activeRound(world);
        List<PendingReturn> queue = round == null || ownerUuid == null ? null : round.returns.get(ownerUuid);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        long now = world.getTime();
        List<PendingReturnStatus> statuses = new ArrayList<>(queue.size());
        for (PendingReturn pending : queue) {
            statuses.add(new PendingReturnStatus(chargeNow(pending, now), (int) Math.max(0L, pending.readyAt() - now)));
        }
        return statuses;
    }

    /** It keeps recharging while queued, as if carried. / 排队期间照常回充，如同携带中。 */
    private static int chargeNow(PendingReturn pending, long now) {
        long intervals = Math.max(0L, now - pending.queuedAt()) / DroneRules.RECHARGE_INTERVAL_TICKS;
        return DroneRules.clampCharge((int) Math.min(DroneRules.CHARGE_MAX,
                pending.charge() + intervals * DroneRules.RECHARGE_PER_INTERVAL));
    }

    private static boolean deliver(ServerWorld world, ServerPlayerEntity owner, PendingReturn pending) {
        int slot = freeHotbarSlot(owner);
        if (slot < 0) {
            return false;
        }
        long now = world.getTime();
        ItemStack stack = new ItemStack(SparkStrengthItems.grenadeDrone());
        DroneItem.setCharge(stack, chargeNow(pending, now));
        owner.getInventory().setStack(slot, stack);
        int remaining = (int) Math.max(0L, pending.readyAt() - now);
        if (remaining > 0) {
            preserveCooldown(owner, SparkStrengthItems.grenadeDrone(), remaining);
            owner.sendMessage(Text.translatable("tip.sparkstrength.drone.returned", (remaining + 19) / 20), true);
        }
        return true;
    }

    private static void startRound(ServerWorld world, GameWorldComponent game) {
        endRound(world);
        // Status is still STARTING: the round id is adopted on first access once ACTIVE. Roles are final here (after
        // every RoleAssigned, including SparkTraits Conscience rewrites).
        // 此时状态仍为 STARTING：回合 id 在进入 ACTIVE 后首次访问时采用。此处身份已是最终结果（所有 RoleAssigned 之后，含 SparkTraits 良心改写）。
        Round round = new Round();
        ROUNDS.put(world, round);
        for (ServerPlayerEntity player : world.getPlayers()) {
            preserveCooldown(player, SparkStrengthItems.grenadeDrone(), DroneRules.OPENING_TICKS);
            preserveCooldown(player, SparkStrengthItems.bombDrone(), DroneRules.OPENING_TICKS);
            if (game.hasAnyRole(player) && !player.isSpectator() && isRealBomber(player)) {
                settleStarter(round, player);
            }
        }
    }

    private static void endRound(ServerWorld world) {
        Round round = ROUNDS.remove(world);
        if (round != null) {
            for (DroneEntity drone : List.copyOf(round.drones)) {
                if (!drone.isRemoved()) {
                    DronePilotService.endFor(drone, DronePilotEndReason.ROUND_OVER);
                    drone.discard();
                }
            }
            round.drones.clear();
            round.returns.clear();
        }
        // Opening/lost cooldowns must not leak into the lobby or the next round. / 开局锁与损毁冷却不能带入大厅或下一局。
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.getItemCooldownManager().remove(SparkStrengthItems.grenadeDrone());
            player.getItemCooldownManager().remove(SparkStrengthItems.bombDrone());
        }
    }

    private static void tick(ServerWorld world) {
        Round round = ROUNDS.get(world);
        if (round != null && currentRoundId(world) == null) {
            // Phase change (STOPPING/INACTIVE) or an aborted start; STARTING only exists inside initializeGame.
            // 阶段变化（STOPPING/INACTIVE）或开局中止；STARTING 只存在于 initializeGame 内部。
            if (round.id != null || GameWorldComponent.KEY.get(world).getGameStatus()
                    != GameWorldComponent.GameStatus.STARTING) {
                endRound(world);
            }
            return;
        }
        round = activeRound(world);
        if (round == null) {
            return;
        }
        sweep(world, round);
        keepDronesTicking(world, round);
        deliverPending(world, round);
        for (ServerPlayerEntity player : world.getPlayers()) {
            syncOpening(player);
        }
        if (world.getTime() % GRANT_RECONCILE_INTERVAL_TICKS == 0) {
            reconcileStarters(world, round);
        }
    }

    /**
     * Forget removed drones. A drone whose chunk unloaded vanished without being destroyed (drones are never saved),
     * so a grenade drone goes back to its owner as if lost; its bound M67 is gone with it.
     * 清理已移除的无人机。区块卸载导致的消失并非损毁（无人机不存档），投弹无人机按损毁归还主人；其挂载的 M67 随之消失。
     */
    private static void sweep(ServerWorld world, Round round) {
        List<DroneEntity> unloaded = new ArrayList<>();
        round.drones.removeIf(drone -> {
            if (!drone.isRemoved()) {
                return false;
            }
            Entity.RemovalReason reason = drone.getRemovalReason();
            if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
                unloaded.add(drone);
            }
            return true;
        });
        for (DroneEntity drone : unloaded) {
            DronePilotService.endFor(drone, DronePilotEndReason.DESTROYED);
            if (drone.kind() == DroneKind.GRENADE && drone.ownerUuid() != null) {
                returnGrenadeDrone(world, drone.ownerUuid(), drone.charge(), DroneRules.GRENADE_LOST_COOLDOWN_TICKS);
            }
        }
    }

    /** Refresh every live drone's idle chunk ticket (see {@link #IDLE_TICKET}). / 刷新每架存活无人机的空闲区块票据。 */
    private static void keepDronesTicking(ServerWorld world, Round round) {
        for (DroneEntity drone : round.drones) {
            if (!drone.isRemoved() && drone.getWorld() == world) {
                world.getChunkManager().addTicket(IDLE_TICKET, drone.getChunkPos(), IDLE_TICKET_RADIUS, drone.getId());
            }
        }
    }

    private static void deliverPending(ServerWorld world, Round round) {
        Iterator<Map.Entry<UUID, List<PendingReturn>>> entries = round.returns.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, List<PendingReturn>> entry = entries.next();
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (!keepsDrones(owner) || owner.getServerWorld() != world) {
                // The drone dies with its owner or the owner's Bomber role. / 无人机随主人死亡或失去炸弹客身份而作废。
                entries.remove();
                continue;
            }
            if (!isActiveBomber(owner)) {
                // Out of survival for now (e.g. swallowed): keep it queued. / 暂时不在生存模式（如被吞噬）：继续排队。
                continue;
            }
            List<PendingReturn> queue = entry.getValue();
            while (!queue.isEmpty() && deliver(world, owner, queue.getFirst())) {
                queue.removeFirst();
            }
            if (queue.isEmpty()) {
                entries.remove();
            }
        }
    }

    /**
     * Mid-round reconciliation, like TabletShopService#tick: SparkWitch recruitment rewrites inventories after
     * RoleAssigned and Wraith promotion never fires it, so new Bombers are found by polling. Settled once either way; a
     * missing drone never re-triggers a grant (disguise stashes would duplicate it).
     * 局中对账，同 TabletShopService#tick：SparkWitch 招募会在 RoleAssigned 后重写物品栏，冤魂晋升根本不触发该事件，因此靠轮询发现新的
     * 炸弹客。无论是否发放都只结算一次；无人机缺失绝不会再次触发发放（伪装暂存物品会导致重复）。
     */
    private static void reconcileStarters(ServerWorld world, Round round) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isActiveBomber(player)) {
                settleStarter(round, player);
            }
        }
    }

    private static void settleStarter(Round round, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (round.settled.contains(uuid)) {
            return;
        }
        if (!hasGrenadeDrone(round, player)) {
            int slot = freeHotbarSlot(player);
            if (slot < 0) {
                // Hotbar full: stay unsettled so the next pass retries. / 快捷栏已满：保持未结算，下一轮重试。
                return;
            }
            // No chat line: the Bomber's tablet grant message already explains drones. / 不发聊天提示：炸弹客的平板发放消息已介绍无人机。
            player.getInventory().setStack(slot, new ItemStack(SparkStrengthItems.grenadeDrone()));
        }
        round.settled.add(uuid);
    }

    private static boolean hasGrenadeDrone(Round round, ServerPlayerEntity player) {
        Item item = SparkStrengthItems.grenadeDrone();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return true;
            }
        }
        if (player.currentScreenHandler != null && player.currentScreenHandler.getCursorStack().isOf(item)) {
            return true;
        }
        for (DroneEntity drone : round.drones) {
            if (!drone.isRemoved() && drone.kind() == DroneKind.GRENADE && player.getUuid().equals(drone.ownerUuid())) {
                return true;
            }
        }
        List<PendingReturn> queue = round.returns.get(player.getUuid());
        return queue != null && !queue.isEmpty();
    }

    private static int freeHotbarSlot(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static void syncOpening(ServerPlayerEntity player) {
        int remaining = openingRemaining(player.getServerWorld());
        if (remaining > 0) {
            // Native cooldown is sent even without an owned stack, so a bought bomb drone shows it immediately.
            // 即使未持有也发送原版冷却，购买的炸弹无人机会立即显示。
            preserveCooldown(player, SparkStrengthItems.grenadeDrone(), remaining);
            preserveCooldown(player, SparkStrengthItems.bombDrone(), remaining);
        }
    }

    private static void disconnect(ServerPlayerEntity player) {
        // Placed drones notice the lost owner on their own tick; queued returns die with the session.
        // 已放置的无人机会在自身 tick 中发现主人失联；排队中的归还随会话作废。
        for (Round round : ROUNDS.values()) {
            round.returns.remove(player.getUuid());
        }
    }

    /** Raise (never shorten) the vanilla cooldown of {@code item}. / 提高（绝不缩短）该物品的原版冷却。 */
    private static void preserveCooldown(ServerPlayerEntity player, Item item, int minimumTicks) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        Object entry = ((ItemCooldownManagerAccessor) manager).sparkstrength$getEntries().get(item);
        int remaining = entry == null ? 0 : Math.max(0,
                ((ItemCooldownEntryAccessor) entry).sparkstrength$getEndTick()
                        - ((ItemCooldownManagerAccessor) manager).sparkstrength$getTick());
        if (remaining < minimumTicks) {
            manager.set(item, minimumTicks);
        }
    }

    /**
     * This world's round, created or reset to match the current ACTIVE match id; null outside a match.
     * 本世界的回合状态，按当前 ACTIVE 对局 id 创建或重置；对局外为 null。
     */
    private static @Nullable Round activeRound(ServerWorld world) {
        UUID current = currentRoundId(world);
        if (current == null) {
            return null;
        }
        Round round = ROUNDS.get(world);
        if (round != null && round.id != null && !round.id.equals(current)) {
            endRound(world);
            round = null;
        }
        if (round == null) {
            round = new Round();
            ROUNDS.put(world, round);
        }
        if (round.id == null) {
            round.id = current;
        }
        return round;
    }

    private record PendingReturn(int charge, long queuedAt, long readyAt) {
    }

    /** Tablet view of one queued return: charge in basis points, cooldown in ticks. / 一条排队归还的平板视图：电量（万分比）与冷却刻数。 */
    public record PendingReturnStatus(int charge, int cooldownTicks) {
    }

    private static final class Round {
        /** Adopted from M67RoundService once the match is ACTIVE. / 对局进入 ACTIVE 后采用 M67 回合 id。 */
        private @Nullable UUID id;
        private final Set<UUID> settled = new HashSet<>();
        private final Set<DroneEntity> drones = new LinkedHashSet<>();
        private final Map<UUID, List<PendingReturn>> returns = new HashMap<>();
    }
}
