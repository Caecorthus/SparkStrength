package annina.sparkstrength.item.m67;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.entity.M67GrenadeEntity;
import annina.sparkstrength.mixin.minecraft.ItemCooldownEntryAccessor;
import annina.sparkstrength.mixin.minecraft.ItemCooldownManagerAccessor;
import annina.sparkstrength.network.m67.M67SoundPayload;
import annina.sparkstrength.role.coroner.CoronerRules;
import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.HashSet;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned sessions; packets never supply charge duration. / 服务端管理蓄力，数据包不能指定蓄力时间。 */
public final class M67UseService {
    private static final Map<ServerPlayerEntity, Equipment> EQUIPMENT = new IdentityHashMap<>();
    private static final Map<ServerPlayerEntity, M67UseState> SESSIONS = new IdentityHashMap<>();
    private static final Map<ServerPlayerEntity, M67UseState> RELEASE_SCOPES = new IdentityHashMap<>();

    private M67UseService() {
    }

    public static boolean begin(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        refreshEquipment(player);
        if (SESSIONS.containsKey(player)) {
            cancel(player);
            return false;
        }
        ServerWorld world = player.getServerWorld();
        UUID roundId = M67RoundService.currentRoundId(world);
        int opening = M67RoundService.openingRemaining(world);
        if (opening > 0) {
            preserveCooldown(player, opening);
            player.sendMessage(Text.translatable("tip.sparkstrength.m67.opening_cooldown", (opening + 19) / 20), true);
            return false;
        }
        if (roundId == null || !allowed(player) || player.isUsingItem()
                || stack.isEmpty() || !stack.isOf(SparkStrengthItems.m67())
                || player.getStackInHand(hand) != stack) {
            return false;
        }
        SESSIONS.put(player, new M67UseState(roundId, stack, hand, player.getInventory().selectedSlot,
                stack.getCount(), world.getTime()));
        player.setCurrentHand(hand);
        M67SoundService.start(player, M67SoundPayload.START_PULL, hand);
        return true;
    }

    public static void tick(ServerPlayerEntity player) {
        refreshEquipment(player);
        M67SoundService.tick(player);
        M67UseState state = SESSIONS.get(player);
        if (state != null && !valid(player, state)) {
            cancel(player);
        }
    }

    public static void release(ServerPlayerEntity player, ItemStack stack) {
        refreshEquipment(player);
        M67UseState state = SESSIONS.get(player);
        if (state == null) {
            return;
        }
        boolean valid = valid(player, state) && player.getActiveItem() == stack;
        boolean authorized = RELEASE_SCOPES.get(player) == state;
        // Remove before spawn, decrement or clear callbacks can re-enter. / 先移除会话，避免生成、扣物品和清除回调重入。
        SESSIONS.remove(player);
        M67SoundService.stop(player);
        if (!state.finish(player.getServerWorld().getTime(), authorized && valid)) {
            clearM67Use(player);
            preserveCooldown(player, M67Rules.CANCEL_COOLDOWN_TICKS);
            return;
        }

        ServerWorld world = player.getServerWorld();
        M67GrenadeEntity grenade = new M67GrenadeEntity(world, player, state.roundId());
        grenade.setVelocity(player.getRotationVec(1.0F).multiply(M67Physics.LAUNCH_SPEED));
        // Vanilla item data syncs the thrown model on the projectile copy only.
        // 仅给投掷物副本设置模型数据，由原版同步；手中剩余物品保持原贴图。
        ItemStack projectileStack = stack.copyWithCount(1);
        projectileStack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(1));
        grenade.setItem(projectileStack);
        if (!world.spawnEntity(grenade)) {
            grenade.discard();
            clearM67Use(player);
            preserveCooldown(player, M67Rules.CANCEL_COOLDOWN_TICKS);
            return;
        }
        M67RoundService.registerThrown(grenade);
        stack.decrement(1);
        // A consumed last grenade may empty one hand, but is not a new equip. / 消耗最后一枚导致空手，不是重新装备。
        ItemStack main = equippedM67(player, Hand.MAIN_HAND);
        ItemStack off = equippedM67(player, Hand.OFF_HAND);
        if (main == null && off == null) {
            EQUIPMENT.remove(player);
        } else {
            EQUIPMENT.put(player, new Equipment(world, state.roundId(), player.getInventory().selectedSlot, main, off));
        }
        preserveCooldown(player, throwCooldownTicks(player));
        clearM67Use(player);
        GameRecordManager.recordItemUse(player, SparkStrengthItems.M67_ID, null, null);
        // Broadcast only after a successful throw; canceled sessions stay silent. / 仅成功投掷后广播，取消会话不播放投掷声。
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SparkStrengthSounds.M67_THROW,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** Cancellation never adds a cooldown without a charge; idle equip audio may still stop.
     *  无蓄力时取消不增加冷却，但可停止尚在播放的装备音效。 */
    public static void cancel(ServerPlayerEntity player) {
        M67UseState state = SESSIONS.remove(player);
        M67SoundService.stop(player);
        if (state == null) {
            return;
        }
        state.finish(player.getServerWorld().getTime(), false);
        clearM67Use(player);
        preserveCooldown(player, M67Rules.CANCEL_COOLDOWN_TICKS);
    }

    /** Scope only the vanilla RELEASE_USE_ITEM invocation, never arbitrary stopUsingItem calls. / 仅授权原版松开数据包调用。 */
    public static void withReleaseAuthorization(ServerPlayerEntity player, Runnable stopUsingItem) {
        refreshEquipment(player);
        M67UseState state = SESSIONS.get(player);
        if (state == null) {
            stopUsingItem.run();
            return;
        }
        M67UseState previous = RELEASE_SCOPES.put(player, state);
        try {
            stopUsingItem.run();
        } finally {
            if (previous == null) {
                RELEASE_SCOPES.remove(player);
            } else {
                RELEASE_SCOPES.put(player, previous);
            }
        }
    }

    public static int throwCooldownTicks(ServerPlayerEntity player) {
        return CoronerRules.isBomber(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || (CoronerService.isActualCoroner(player) && CoronerService.hasBomberDisguise(player))
                ? M67Rules.BOMBER_COOLDOWN_TICKS : M67Rules.THROW_COOLDOWN_TICKS;
    }

    /** Compare references, not counts: successful consumption is not another equip.
     *  比较引用而非数量：成功投掷扣除数量不视为重新装备。 */
    public static void refreshEquipment(ServerPlayerEntity player) {
        UUID round = M67RoundService.currentRoundId(player.getServerWorld());
        if (round == null || !eligiblePlayer(player)) {
            forgetPlayer(player);
            return;
        }
        ItemStack main = equippedM67(player, Hand.MAIN_HAND);
        ItemStack off = equippedM67(player, Hand.OFF_HAND);
        Equipment previous = EQUIPMENT.get(player);
        Equipment current = main == null && off == null ? null
                : new Equipment(player.getServerWorld(), round, player.getInventory().selectedSlot, main, off);
        if (current == null ? previous == null : current.matches(previous)) {
            return;
        }
        // Publish before cancellation can re-enter through clearActiveItem. / 先更新再取消，防止清除使用回调重入。
        if (current == null) {
            EQUIPMENT.remove(player);
        } else {
            EQUIPMENT.put(player, current);
        }
        cancel(player);
        M67SoundService.stop(player);
        if (current != null) {
            preserveCooldown(player, M67Rules.EQUIP_COOLDOWN_TICKS);
            M67SoundService.start(player, M67SoundPayload.START_EQUIP,
                    main != null ? Hand.MAIN_HAND : Hand.OFF_HAND);
        }
    }

    public static void forgetPlayer(ServerPlayerEntity player) {
        EQUIPMENT.remove(player);
        cancel(player);
        M67SoundService.stop(player);
        RELEASE_SCOPES.remove(player);
    }

    private static ItemStack equippedM67(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        return !stack.isEmpty() && stack.isOf(SparkStrengthItems.m67()) ? stack : null;
    }

    private static Set<ServerPlayerEntity> trackedPlayers() {
        Set<ServerPlayerEntity> players = new HashSet<>(EQUIPMENT.keySet());
        players.addAll(SESSIONS.keySet());
        players.addAll(RELEASE_SCOPES.keySet());
        players.addAll(M67SoundService.actors());
        return players;
    }

    static void clearWorld(ServerWorld world) {
        for (ServerPlayerEntity player : trackedPlayers()) {
            Equipment equipment = EQUIPMENT.get(player);
            if (player.getWorld() == world || (equipment != null && equipment.world() == world)
                    || M67SoundService.belongsTo(player, world)) {
                forgetPlayer(player);
            }
        }
    }

    static void clearAll() {
        for (ServerPlayerEntity player : trackedPlayers()) {
            forgetPlayer(player);
        }
    }

    private record Equipment(ServerWorld world, UUID round, int slot, ItemStack main, ItemStack off) {
        boolean matches(Equipment other) {
            return other != null && world == other.world && round.equals(other.round)
                    && slot == other.slot && main == other.main && off == other.off;
        }
    }

    static void preserveCooldown(ServerPlayerEntity player, int minimumTicks) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        Object entry = ((ItemCooldownManagerAccessor) manager).sparkstrength$getEntries().get(SparkStrengthItems.m67());
        int remaining = entry == null ? 0 : Math.max(0,
                ((ItemCooldownEntryAccessor) entry).sparkstrength$getEndTick()
                        - ((ItemCooldownManagerAccessor) manager).sparkstrength$getTick());
        if (remaining < minimumTicks) {
            manager.set(SparkStrengthItems.m67(), minimumTicks);
        }
    }

    private static boolean valid(ServerPlayerEntity player, M67UseState state) {
        if (!player.isUsingItem() || !allowed(player)) {
            return false;
        }
        ItemStack held = player.getStackInHand(player.getActiveHand());
        return held.isOf(SparkStrengthItems.m67()) && player.getActiveItem() == held
                && state.matches(M67RoundService.currentRoundId(player.getServerWorld()), held,
                player.getActiveHand(), player.getInventory().selectedSlot, held.getCount());
    }

    private static boolean allowed(ServerPlayerEntity player) {
        return M67RoundService.currentRoundId(player.getServerWorld()) != null
                && M67RoundService.openingRemaining(player.getServerWorld()) == 0
                && eligiblePlayer(player)
                && !player.getItemCooldownManager().isCoolingDown(SparkStrengthItems.m67())
                && !EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                && !SparkTraitsCompat.isKillerInteractionBlocked(player);
    }

    private static boolean eligiblePlayer(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        // Lobby use needs no assigned role; active matches keep their participant restrictions.
        // 大厅使用不需要分配角色；正式对局仍保留参赛玩家限制。
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(player.getWorld()).getGameStatus();
        return status == GameWorldComponent.GameStatus.INACTIVE
                || (status == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player));
    }

    private static void clearM67Use(ServerPlayerEntity player) {
        if (player.getActiveItem().isOf(SparkStrengthItems.m67())) {
            player.clearActiveItem();
        }
    }
}
