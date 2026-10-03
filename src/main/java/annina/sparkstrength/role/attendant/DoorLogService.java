package annina.sparkstrength.role.attendant;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.role.detective.DetectiveIdentityResolver;
import dev.doctor4t.wathe.api.event.DoorStateChanged;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side room-door capture for the Attendant door monitor. Blasts and {@code jam()} calls arrive through Wathe's
 * {@link DoorStateChanged} events (no actor); every other change is diffed around {@code SmallDoorBlock#onUse} by
 * {@code SmallDoorBlockDoorLogMixin}, which reports the clicked leaf only (a double door logs once).
 * 乘务员房门监控的服务端采集。爆破与 jam() 通过 Wathe 的 DoorStateChanged 事件到达（无操作者）；其余变化由
 * SmallDoorBlockDoorLogMixin 在 SmallDoorBlock#onUse 前后做差分，只看被点击的那扇门板（双开门只记一次）。
 *
 * <p>Room door = a non-train {@link SmallDoorBlock} whose lower-half entity has a key name; the key name is the
 * shown door name. Nothing is logged unless a game is running, and onUse-derived events also need the clicking player
 * to be playing, alive and not in creative/spectator. Only {@link DoorLogKind#KEY_OPENED} stores an actor, and only
 * the apparent identity (disguise-aware; anonymous while invisible, psycho or during a Jester moment): a disguised
 * player's real uuid is never stored.
 * 房门 = 非列车门的 SmallDoorBlock，且其下半部方块实体带钥匙名；钥匙名即显示的门名。只有对局进行中才记录，
 * 由 onUse 推导的事件还要求点击者正在游戏中、存活且非创造/旁观模式。只有 KEY_OPENED 保存操作者，且只保存表面身份
 * （考虑伪装；隐身、疯魔或小丑时刻时匿名）：伪装者的真实 UUID 永不保存。</p>
 */
public final class DoorLogService {
    /**
     * Innermost tracked onUse on this thread. Per-thread because an integrated server runs onUse on the client thread
     * too (the mixin skips the client before reaching here); linked so re-entrant onUse calls unwind in order.
     * 当前线程最内层被追踪的 onUse。按线程隔离：集成服务端下客户端线程同样会调用 onUse（mixin 已在客户端提前返回）；
     * 以链表串联，保证重入的 onUse 依序回退。
     */
    private static final ThreadLocal<Interaction> CURRENT = new ThreadLocal<>();
    private static boolean registered;

    private DoorLogService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DoorStateChanged.BLAST.register(DoorLogService::onBlast);
        DoorStateChanged.JAM.register(DoorLogService::onJam);
    }

    /**
     * Called by the onUse wrapper before the original runs (server only). Returns null when the interaction is not
     * tracked; otherwise the returned token must be handed to {@link #endUse} in a finally block.
     * 由 onUse 包装在原方法执行前调用（仅服务端）。不追踪时返回 null；否则必须在 finally 中把返回值交给 endUse。
     *
     * <p>Observation must never break door use, so any failure here only disables logging for this click.
     * 观察绝不能破坏开门，因此这里的任何异常只会让本次点击不被记录。</p>
     */
    public static @Nullable Interaction beginUse(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        try {
            return trackUse(state, world, pos, player);
        } catch (RuntimeException exception) {
            SparkStrength.LOGGER.warn("Door monitor skipped a room-door interaction at {}.", pos, exception);
            return null;
        }
    }

    private static @Nullable Interaction trackUse(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity actor)
                || !(state.getBlock() instanceof SmallDoorBlock)
                || !GameWorldComponent.KEY.get(serverWorld).isRunning()
                || !GameFunctions.isPlayerPlayingAndAlive(actor)
                || !GameFunctions.isPlayerAliveAndSurvival(actor)) {
            return null;
        }
        BlockPos lowerPos = state.get(SmallDoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
        SmallDoorBlockEntity door = roomDoor(serverWorld, lowerPos);
        if (door == null) {
            return null;
        }
        String doorName = door.getKeyName();
        // Read the hand before the call: a Key Fish is consumed by its own listener inside onUse.
        // 必须在调用前读取手持物：钥匙鱼会在 onUse 内被其监听器消耗。
        ItemStack held = actor.getMainHandStack();
        String itemId = Registries.ITEM.getId(held.getItem()).toString();
        DoorLogRules.HeldItem category = DoorLogRules.heldItem(itemId,
                DoorLogRules.WATHE_KEY.equals(itemId) && loreNamesDoor(held, doorName));
        Interaction interaction = new Interaction(CURRENT.get(), serverWorld, lowerPos.toImmutable(), doorName, actor,
                category, stateOf(door));
        CURRENT.set(interaction);
        return interaction;
    }

    /**
     * Pops the token and, when the original onUse returned normally, logs the clicked leaf's state change.
     * 弹出追踪记录；原 onUse 正常返回时，记录被点击门板的状态变化。
     */
    public static void endUse(@Nullable Interaction interaction, boolean completed) {
        if (interaction == null) {
            return;
        }
        if (interaction.outer == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(interaction.outer);
        }
        if (!completed) {
            return;
        }
        // Runs in the wrapper's finally: a failure must neither replace onUse's result nor reach the packet handler.
        // 在包装的 finally 中执行：异常既不能替换 onUse 的结果，也不能传到网络包处理器。
        try {
            logUse(interaction);
        } catch (RuntimeException exception) {
            SparkStrength.LOGGER.warn("Door monitor failed to log a room-door interaction at {}.",
                    interaction.lowerPos, exception);
        }
    }

    private static void logUse(Interaction interaction) {
        ServerWorld world = interaction.world;
        if (!GameWorldComponent.KEY.get(world).isRunning()
                || !(world.getBlockEntity(interaction.lowerPos) instanceof SmallDoorBlockEntity door)) {
            return;
        }
        for (DoorLogKind kind : DoorLogRules.classify(interaction.before, stateOf(door), interaction.held,
                interaction.jamLogged)) {
            record(world, kind, interaction.doorName, kind.namesActor() ? apparentActor(interaction.actor) : null);
        }
    }

    private static void onBlast(World world, BlockPos pos, DoorBlockEntity entity) {
        if (!(world instanceof ServerWorld serverWorld) || !GameWorldComponent.KEY.get(serverWorld).isRunning()) {
            return;
        }
        SmallDoorBlockEntity door = roomDoor(serverWorld, pos);
        if (door != null) {
            record(serverWorld, DoorLogKind.BROKEN, door.getKeyName(), null);
        }
    }

    private static void onJam(World world, BlockPos pos, DoorBlockEntity entity) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        // The event already covers this jam, so the enclosing onUse diff must not log it again.
        // 该堵门已由事件记录，外层 onUse 差分不得重复记录。
        for (Interaction interaction = CURRENT.get(); interaction != null; interaction = interaction.outer) {
            if (interaction.world == serverWorld && interaction.lowerPos.equals(pos)) {
                interaction.jamLogged = true;
            }
        }
        if (!GameWorldComponent.KEY.get(serverWorld).isRunning()) {
            return;
        }
        SmallDoorBlockEntity door = roomDoor(serverWorld, pos);
        if (door != null) {
            record(serverWorld, DoorLogKind.JAMMED, door.getKeyName(), null);
        }
    }

    /**
     * Also reached from inside {@code blast()} / {@code jam()} callers (crowbar, lockpick, listeners), so a failure is
     * logged and swallowed rather than aborting their action.
     * 也会在 blast()/jam() 的调用方（撬棍、撬锁器、监听器）内部被调用，因此异常只记录日志并吞掉，不中断其操作。
     */
    private static void record(ServerWorld world, DoorLogKind kind, String doorName, @Nullable DoorLog.Actor actor) {
        try {
            TabletWorldComponent.KEY.get(world).doorLog().record(kind, doorName, actor, world.getTime());
        } catch (RuntimeException exception) {
            SparkStrength.LOGGER.warn("Door monitor failed to record {} for {}.", kind.id(), doorName, exception);
        }
    }

    /** Lower-half entity of a keyed, non-train small door, else null. / 带钥匙名的非列车小门的下半部实体，否则为 null。 */
    private static @Nullable SmallDoorBlockEntity roomDoor(World world, BlockPos lowerPos) {
        BlockState state = world.getBlockState(lowerPos);
        if (!(state.getBlock() instanceof SmallDoorBlock) || state.getBlock() instanceof TrainDoorBlock) {
            return null;
        }
        if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door && !door.getKeyName().isEmpty()) {
            return door;
        }
        return null;
    }

    private static DoorLogRules.DoorState stateOf(DoorBlockEntity door) {
        return new DoorLogRules.DoorState(door.isOpen(), door.isBlasted(), door.isJammed());
    }

    /** Wathe's own right-key test: first lore line equals the door's key name. / 与 Wathe 相同的钥匙判定：首行 lore 等于门的钥匙名。 */
    private static boolean loreNamesDoor(ItemStack stack, String keyName) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        return lore != null && !lore.lines().isEmpty() && lore.lines().getFirst().getString().equals(keyName);
    }

    /**
     * What a witness at the door would have seen: nobody recognisable while invisible (Phantom, serum, potion), psycho
     * or during a Jester moment; otherwise the disguise-aware appearance.
     * 门口目击者会看到的身份：隐身（幻影、血清、药水）、疯魔或小丑时刻时无法辨认；否则为考虑伪装后的外观。
     */
    private static DoorLog.Actor apparentActor(ServerPlayerEntity player) {
        if (player.isInvisible() || DetectiveIdentityResolver.isSubjectAnonymised(player)) {
            return DoorLog.Actor.anonymousActor();
        }
        DetectiveIdentityResolver.DisplayedIdentity identity = DetectiveIdentityResolver.resolveAppearance(player);
        return new DoorLog.Actor(identity.displayUuid(), identity.displayName(), identity.anonymous());
    }

    /** One tracked onUse call; opaque to the mixin. / 一次被追踪的 onUse 调用；对 mixin 不透明。 */
    public static final class Interaction {
        private final @Nullable Interaction outer;
        private final ServerWorld world;
        private final BlockPos lowerPos;
        private final String doorName;
        private final ServerPlayerEntity actor;
        private final DoorLogRules.HeldItem held;
        private final DoorLogRules.DoorState before;
        private boolean jamLogged;

        private Interaction(@Nullable Interaction outer, ServerWorld world, BlockPos lowerPos, String doorName,
                            ServerPlayerEntity actor, DoorLogRules.HeldItem held, DoorLogRules.DoorState before) {
            this.outer = outer;
            this.world = world;
            this.lowerPos = lowerPos;
            this.doorName = doorName;
            this.actor = actor;
            this.held = held;
            this.before = before;
        }
    }
}
