package annina.sparkstrength.role.vulture;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.component.vulture.SkateboardRideComponent;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server authority for the Vulture's skateboard: the starter grant, the 60 s opening lock, and each 10 s ride
 * (a temporary x3 movement-speed modifier plus the synced {@link SkateboardRideComponent}).
 * 秃鹫滑板的服务端权威逻辑：开局发放、60 秒开局锁，以及每次 10 秒的滑行（临时 x3 移速修饰符与同步的
 * {@link SkateboardRideComponent}）。
 */
public final class VultureSkateboardService {
    /**
     * Stable modifier id; it is temporary (never saved), so only this service ever adds or removes it.
     * 稳定的修饰符 ID；它是临时修饰符（不会存档），只由本服务添加和移除。
     */
    public static final Identifier SPEED_MODIFIER_ID = SparkStrength.id("skateboard_speed");
    private static final int COOLDOWN_SYNC_TOLERANCE_TICKS = 5;
    private static final Map<ServerWorld, Round> ROUNDS = new IdentityHashMap<>();
    private static boolean registered;

    private VultureSkateboardService() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Roles are final here (after every RoleAssigned); the status is still STARTING and turns ACTIVE right after.
        // 此时身份已是最终结果（所有 RoleAssigned 之后）；状态仍为 STARTING，随后立即变为 ACTIVE。
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
        // Wathe clears status effects but never attribute modifiers, so every exit path removes ours.
        // Wathe 只清除状态效果、从不清除属性修饰符，因此每条退出路径都要移除我们的修饰符。
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                endRide(serverPlayer, false);
            }
        });
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (victim instanceof ServerPlayerEntity serverPlayer) {
                endRide(serverPlayer, false);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(VultureSkateboardService::tick);
        ServerWorldEvents.UNLOAD.register((server, world) -> ROUNDS.remove(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ROUNDS.clear());
    }

    /**
     * Real Vulture who is alive, playing and in survival right now (not swallowed by the Taotie).
     * 此刻存活、参与对局、处于生存模式（未被饕餮吞噬）的真实秃鹫。
     */
    public static boolean isActiveVulture(@Nullable PlayerEntity player) {
        return player != null
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && !SwallowedPlayerComponent.isPlayerSwallowed(player)
                && VultureSkateboardRules.isVulture(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Server half of {@code SkateboardItem.use}. Returns true when a ride started.
     * {@code SkateboardItem.use} 的服务端部分；开始滑行时返回 true。
     */
    public static boolean tryStartRide(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Item skateboard = SparkStrengthItems.skateboard();
        if (!isActive(world) || !isActiveVulture(player)
                || player.getItemCooldownManager().isCoolingDown(skateboard)
                || SkateboardRideComponent.KEY.get(player).hasRide()
                || isSkillLocked(player)) {
            return false;
        }
        Round round = ROUNDS.get(world);
        long now = world.getTime();
        int opening = round == null ? 0 : round.openingRemaining(player.getUuid(), now);
        if (opening > 0) {
            preserveCooldown(player, skateboard, opening);
            player.sendMessage(Text.translatable("tip.sparkstrength.skateboard.opening_cooldown", (opening + 19) / 20), true);
            return false;
        }
        int wait = round == null ? 0 : round.cooldownRemaining(player.getUuid(), now);
        if (wait > 0) {
            // Rejoining gives a fresh entity with no item cooldown; the round still remembers the last ride.
            // 重新加入会得到没有物品冷却的新实体；回合仍记得上一次滑行。
            preserveCooldown(player, skateboard, wait);
            return false;
        }

        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return false;
        }
        speed.removeModifier(SPEED_MODIFIER_ID);
        speed.addTemporaryModifier(new EntityAttributeModifier(
                SPEED_MODIFIER_ID,
                VultureSkateboardRules.SPEED_MODIFIER_AMOUNT,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
        SkateboardRideComponent.KEY.get(player).start(now + VultureSkateboardRules.RIDE_TICKS);
        player.getItemCooldownManager().set(skateboard, VultureSkateboardRules.USE_COOLDOWN_TICKS);
        if (round != null) {
            round.readyAt.put(player.getUuid(), now + VultureSkateboardRules.USE_COOLDOWN_TICKS);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BAMBOO_WOOD_PLACE,
                SoundCategory.PLAYERS, 0.7F, 1.3F);
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.SKATEBOARD_RIDE_STARTED, player, null);
        return true;
    }

    /**
     * Admin path only: SparkFactionAPI {@code /sparkfactionapi:clearCooldown} just removed this player's skateboard
     * cooldown (called through {@code SparkFactionAdminClear}). That remove alone does not stick (the opening lock
     * and the ride cooldown are re-synced every tick from the round), so the player's board ignores the opening lock
     * for the rest of this round and the current ride + cooldown is over now. A ride in progress keeps going; the next
     * ride sets a normal cooldown. No-op outside a round; role mechanics that remove cooldowns never call this.
     * 仅管理员路径：SparkFactionAPI /sparkfactionapi:clearCooldown 刚移除了该玩家的滑板冷却（经由 SparkFactionAdminClear
     * 调用）。仅移除原版冷却无效（开局锁与滑行冷却每刻按回合状态重新同步），因此本回合剩余时间内该玩家的滑板不再受开局锁约束，
     * 当前的“滑行 + 冷却”也立即结束。进行中的滑行继续；下一次滑行照常设置冷却。不在回合中时不做任何事；移除冷却的职业机制
     * 不会调用此方法。
     */
    public static void onAdminCooldownCleared(ServerPlayerEntity player) {
        Round round = ROUNDS.get(player.getServerWorld());
        if (round != null) {
            round.openingReleased.add(player.getUuid());
            round.readyAt.remove(player.getUuid());
        }
    }

    /**
     * Ends {@code player}'s ride, if any: removes the speed modifier and clears the synced state. The item cooldown
     * was already set for ride + cooldown when the ride started.
     * 结束该玩家的滑行（若有）：移除移速修饰符并清除同步状态。物品冷却已在上板时按“滑行 + 冷却”设置。
     */
    public static void endRide(ServerPlayerEntity player, boolean playSound) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        SkateboardRideComponent ride = SkateboardRideComponent.KEY.get(player);
        if (!ride.hasRide()) {
            return;
        }
        ride.clear();
        if (playSound) {
            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_BAMBOO_WOOD_HIT, SoundCategory.PLAYERS, 0.6F, 1.5F);
        }
    }

    private static void startRound(ServerWorld world, GameWorldComponent game) {
        endRound(world);
        Round round = new Round(world.getTime());
        ROUNDS.put(world, round);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player) && !player.isSpectator() && isRealVulture(player)) {
                settleStarter(round, player);
                preserveCooldown(player, SparkStrengthItems.skateboard(), VultureSkateboardRules.OPENING_TICKS);
            }
        }
    }

    private static void endRound(ServerWorld world) {
        ROUNDS.remove(world);
        // Rides and the opening lock must not leak into the lobby or the next round.
        // 滑行与开局锁不能带入大厅或下一局。
        for (ServerPlayerEntity player : world.getPlayers()) {
            endRide(player, false);
            player.getItemCooldownManager().remove(SparkStrengthItems.skateboard());
        }
    }

    private static void tick(ServerWorld world) {
        Round round = ROUNDS.get(world);
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(world).getGameStatus();
        if (round != null && status != GameWorldComponent.GameStatus.ACTIVE
                && status != GameWorldComponent.GameStatus.STARTING) {
            // Phase change (STOPPING/INACTIVE) or an aborted start. / 阶段变化（STOPPING/INACTIVE）或开局中止。
            endRound(world);
            round = null;
        }
        boolean active = status == GameWorldComponent.GameStatus.ACTIVE;
        long now = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (hasRideState(player)) {
                boolean riding = SkateboardRideComponent.KEY.get(player).isRiding();
                boolean eligible = active && isActiveVulture(player);
                if (!riding || !eligible || isSkillLocked(player)) {
                    // Only a ride that simply ran out makes the hop-off sound; forced ends (swallowed, stunned, role
                    // change, round over) stay silent so the sound never marks where something happened.
                    // 只有自然结束的滑行才发出下板声；强制结束（被吞噬、被定身、身份变化、回合结束）保持安静，避免声音暴露事发位置。
                    endRide(player, !riding && eligible && !isSkillLocked(player));
                }
            }
            if (round != null && active && isActiveVulture(player)) {
                // Mid-round reconciliation, like DroneService: Wraith promotion and SparkWitch recruitment can make a
                // Vulture without a RoleAssigned we can rely on, so new Vultures are found by polling.
                // 局中对账，同 DroneService：冤魂晋升与 SparkWitch 招募可能产生秃鹫而不触发可依赖的 RoleAssigned，因此靠轮询发现。
                settleStarter(round, player);
                int lock = Math.max(round.openingRemaining(player.getUuid(), now),
                        round.cooldownRemaining(player.getUuid(), now));
                if (lock > 0) {
                    preserveCooldown(player, SparkStrengthItems.skateboard(), lock);
                }
            }
        }
    }

    private static boolean hasRideState(ServerPlayerEntity player) {
        if (SkateboardRideComponent.KEY.get(player).hasRide()) {
            return true;
        }
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SPEED_MODIFIER_ID);
    }

    /**
     * Grants one skateboard per player per round. Settled once either way, so a lost board never re-triggers a grant.
     * 每名玩家每局发放一次滑板。无论是否发放都只结算一次，丢失的滑板不会再次触发发放。
     */
    private static void settleStarter(Round round, ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (round.settled.contains(uuid)) {
            return;
        }
        if (!hasSkateboard(player)) {
            int slot = freeHotbarSlot(player);
            if (slot < 0) {
                // Hotbar full: stay unsettled so the next pass retries. / 快捷栏已满：保持未结算，下一轮重试。
                return;
            }
            player.getInventory().setStack(slot, new ItemStack(SparkStrengthItems.skateboard()));
        }
        round.settled.add(uuid);
    }

    private static boolean hasSkateboard(ServerPlayerEntity player) {
        Item item = SparkStrengthItems.skateboard();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                return true;
            }
        }
        return player.currentScreenHandler != null && player.currentScreenHandler.getCursorStack().isOf(item);
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

    private static boolean isRealVulture(PlayerEntity player) {
        return VultureSkateboardRules.isVulture(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    private static boolean isActive(ServerWorld world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /**
     * Riding is a role skill: Engineer stuns and SparkTraits skill locks refuse a ride and end one in progress, as they
     * do for the Bomber's drone piloting.
     * 滑行属于职业技能：工程师定身与 SparkTraits 技能封锁会拒绝上板并结束进行中的滑行，与炸弹客驾驶无人机一致。
     */
    private static boolean isSkillLocked(ServerPlayerEntity player) {
        return EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                || SparkTraitsDroneCompat.isRoleSkillBlocked(player)
                || SparkTraitsDroneCompat.isLastStandPending(player);
    }

    /**
     * Raise (never shorten) the vanilla cooldown of {@code item}. The item-cooldown clock and world time may sit a tick
     * apart, so a small shortfall is tolerated instead of re-sending (and restarting the sweep of) the cooldown each tick.
     * 提高（绝不缩短）该物品的原版冷却。物品冷却计时与世界时间可能相差一刻，因此容忍少量差额，避免每刻重发冷却（并重置冷却动画）。
     */
    private static void preserveCooldown(ServerPlayerEntity player, Item item, int minimumTicks) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        Object entry = ((ItemCooldownManagerAccessor) manager).sparkstrength$getEntries().get(item);
        int remaining = entry == null ? 0 : Math.max(0,
                ((ItemCooldownEntryAccessor) entry).sparkstrength$getEndTick()
                        - ((ItemCooldownManagerAccessor) manager).sparkstrength$getTick());
        if (remaining + COOLDOWN_SYNC_TOLERANCE_TICKS < minimumTicks) {
            manager.set(item, minimumTicks);
        }
    }

    private static final class Round {
        private final long startTick;
        private final Set<UUID> settled = new HashSet<>();
        /** World time each player's board is ready again (ride + cooldown). / 每名玩家滑板再次可用的世界时间（滑行 + 冷却）。 */
        private final Map<UUID, Long> readyAt = new HashMap<>();
        /** Players an admin cooldown clear released from the opening lock. / 管理员清除冷却后解除开局锁的玩家。 */
        private final Set<UUID> openingReleased = new HashSet<>();

        private Round(long startTick) {
            this.startTick = startTick;
        }

        private int openingRemaining(UUID uuid, long now) {
            return openingReleased.contains(uuid) ? 0 : VultureSkateboardRules.openingRemaining(startTick, now);
        }

        private int cooldownRemaining(UUID uuid, long now) {
            Long ready = readyAt.get(uuid);
            return ready == null ? 0 : (int) Math.max(0L, ready - now);
        }
    }
}
