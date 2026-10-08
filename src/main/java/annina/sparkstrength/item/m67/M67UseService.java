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

    /**
     * How the server treats a throw. The item is not role-bound; only a live match participant's throw has gameplay
     * effects. Decided on the server for every check; the client never supplies it.
     * 服务端对一次投掷的判定。物品不绑定角色；只有存活的对局参赛者投掷才有玩法效果。每次检查都由服务端判定，客户端无法指定。
     */
    enum ThrowMode {
        /** ACTIVE match, live participant: opening lock, Bomber cooldown, kills. / 对局中存活的参赛者：开局锁、炸弹客冷却、击杀。 */
        MATCH,
        /**
         * Everyone else holding it (INACTIVE, STARTING, STOPPING, or no role while ACTIVE): full visuals, no gameplay
         * effect, no opening lock. / 其他任何持有者（INACTIVE、STARTING、STOPPING，或 ACTIVE 时无角色）：完整表现，无玩法效果，无开局锁。
         */
        PRESENTATION,
        /** Dead or spectating, or a role holder no longer live in the ACTIVE match. / 死亡或旁观，或已不在对局中存活的角色持有者。 */
        REFUSED
    }

    /**
     * A role holder during ACTIVE is a participant: live means a match throw, anything else is refused. SparkWitch infers
     * a match grenade at detonation from the same facts (ACTIVE and the thrower holds a role), so both must stay aligned.
     * ACTIVE 期间持有角色即为参赛者：存活则为对局投掷，否则拒绝。SparkWitch 在引爆时以同样条件（ACTIVE 且投掷者持有角色）推断对局手雷，两边须保持一致。
     */
    static ThrowMode throwMode(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator()) {
            return ThrowMode.REFUSED;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || !game.hasAnyRole(player)) {
            // Creative stays allowed here, as lobby use always was. / 此处仍允许创造模式，与原大厅用法一致。
            return ThrowMode.PRESENTATION;
        }
        return GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player)
                ? ThrowMode.MATCH : ThrowMode.REFUSED;
    }

    public static boolean begin(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        refreshEquipment(player);
        if (SESSIONS.containsKey(player)) {
            cancel(player);
            return false;
        }
        ServerWorld world = player.getServerWorld();
        UUID roundId = M67RoundService.throwRoundId(world);
        int opening = throwMode(player) == ThrowMode.PRESENTATION ? 0 : M67RoundService.openingRemaining(player);
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
        // Decided at release, alongside validity; the grenade keeps it for its whole flight. / 与有效性一同在松手时判定，手雷整个飞行期间保留。
        boolean presentation = throwMode(player) == ThrowMode.PRESENTATION;
        boolean authorized = RELEASE_SCOPES.get(player) == state;
        // Remove before spawn, decrement or clear callbacks can re-enter. / 先移除会话，避免生成、扣物品和清除回调重入。
        SESSIONS.remove(player);
        M67SoundService.stop(player);
        if (!state.finish(player.getServerWorld().getTime(), authorized && valid, chargeTicks(player))) {
            clearM67Use(player);
            preserveCooldown(player, M67Rules.CANCEL_COOLDOWN_TICKS);
            return;
        }

        ServerWorld world = player.getServerWorld();
        M67GrenadeEntity grenade = new M67GrenadeEntity(world, player, state.roundId(), presentation);
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
        // Presentation throws take the normal cooldown and stay out of the match replay. / 表现投掷使用普通冷却，且不写入对局回放。
        preserveCooldown(player, presentation ? M67Rules.THROW_COOLDOWN_TICKS : throwCooldownTicks(player));
        clearM67Use(player);
        if (!presentation) {
            GameRecordManager.recordItemUse(player, SparkStrengthItems.M67_ID, null, null);
        }
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
        state.finish(player.getServerWorld().getTime(), false, M67Rules.CHARGE_TICKS);
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

    /** Resolved on the server from the player's traits; a packet never supplies it.
     *  由服务端按玩家天赋解析，数据包不能指定。 */
    static int chargeTicks(ServerPlayerEntity player) {
        return SparkTraitsCompat.getThrowChargeTicks(player, M67Rules.CHARGE_TICKS);
    }

    public static int throwCooldownTicks(ServerPlayerEntity player) {
        return CoronerRules.isBomber(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                || (CoronerService.isActualCoroner(player) && CoronerService.hasBomberDisguise(player))
                ? M67Rules.BOMBER_COOLDOWN_TICKS : M67Rules.THROW_COOLDOWN_TICKS;
    }

    /** Compare references, not counts: successful consumption is not another equip.
     *  比较引用而非数量：成功投掷扣除数量不视为重新装备。 */
    public static void refreshEquipment(ServerPlayerEntity player) {
        UUID round = M67RoundService.throwRoundId(player.getServerWorld());
        if (round == null || throwMode(player) == ThrowMode.REFUSED) {
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
                && state.matches(M67RoundService.throwRoundId(player.getServerWorld()), held,
                player.getActiveHand(), player.getInventory().selectedSlot, held.getCount());
    }

    private static boolean allowed(ServerPlayerEntity player) {
        ThrowMode mode = throwMode(player);
        return M67RoundService.throwRoundId(player.getServerWorld()) != null
                && mode != ThrowMode.REFUSED
                && (mode == ThrowMode.PRESENTATION || M67RoundService.openingRemaining(player) == 0)
                && !player.getItemCooldownManager().isCoolingDown(SparkStrengthItems.m67())
                && !EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                && !SparkTraitsCompat.isKillerInteractionBlocked(player);
    }

    private static void clearM67Use(ServerPlayerEntity player) {
        if (player.getActiveItem().isOf(SparkStrengthItems.m67())) {
            player.clearActiveItem();
        }
    }
}
