package annina.sparkstrength.role.undercover;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.network.sabotage.ChannelProgressS2CPacket;
import annina.sparkstrength.role.attendant.DoorLogService;
import annina.sparkstrength.sabotage.SabotageActionLocks;
import annina.sparkstrength.sabotage.SabotageRules;
import dev.doctor4t.wathe.api.event.DoorInteraction;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheBlocks;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The stage-2 Undercover chops doors down with the tactical tomahawk (owner 2026-10-19): holding right-click on a door
 * it cannot simply open (locked, jammed, or a train door during the round) takes 5 s for glass, 10 s for wood and 30 s
 * for steel, then the door is gone: both halves, plus the paired leaf of a double door. Afterwards the tomahawk cools
 * down 10 / 20 / 60 s. An unlocked door just opens as usual. Removed doors are recorded and put back at round end (and
 * on server stop), so maps without Wathe's train copy never lose them. Server thread only.
 * 二阶段卧底用战术斧拆门（负责人 2026-10-19）：对着自己打不开的门（锁着、被堵、或对局中的列车门）按住右键，
 * 玻璃门 5 秒、木门 10 秒、钢门 30 秒后整扇门消失：上下两半连同双开门的另一扇。之后战术斧冷却 10 / 20 / 60 秒。
 * 没锁的门照常打开。被拆的门会记录下来，在回合结束（以及服务器关闭）时原样放回，所以没有 Wathe 车厢复制的地图也不会少门。
 * 仅限服务端线程。
 *
 * <p>The progress bar, crack overlay and client cancel reuse the junction box channel plumbing
 * ({@link ChannelProgressS2CPacket#KIND_BREACH}); channel ids start high so they never collide with the boxes'.
 * 读条、裂纹与客户端取消复用接线箱的读条管线（KIND_BREACH）；读条编号从很大的值开始，不会与接线箱冲突。</p>
 */
public final class DoorBreachService {
    private static final int FIRST_CHANNEL_ID = 1 << 24;
    private static final int CHOP_SOUND_INTERVAL_TICKS = 10;
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<RegistryKey<World>, List<RemovedBlock>> REMOVED = new HashMap<>();
    private static int nextChannelId = FIRST_CHANNEL_ID;
    private static boolean registered;

    private DoorBreachService() {
    }

    private record RemovedBlock(BlockPos pos, BlockState state, @Nullable NbtCompound blockEntity) {
    }

    private static final class Channel {
        final int id = nextChannelId++;
        final RegistryKey<World> world;
        final BlockPos lowerPos;
        final UndercoverRules.DoorKind kind;
        long lastHeartbeatTick;
        long lastResyncTick;
        final long startTick;
        int progress;

        Channel(RegistryKey<World> world, BlockPos lowerPos, UndercoverRules.DoorKind kind, long now) {
            this.world = world;
            this.lowerPos = lowerPos.toImmutable();
            this.kind = kind;
            this.lastHeartbeatTick = now;
            this.lastResyncTick = now;
            this.startTick = now;
        }
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DoorInteraction.EVENT.register(DoorBreachService::onDoorInteraction);
        ServerTickEvents.END_WORLD_TICK.register(DoorBreachService::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CHANNELS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                restoreAll(world);
            }
        });
    }

    /** Both sides: HANDLED keeps Wathe's door logic out and lets the held button resend every 4 ticks. / 双端：HANDLED 让 Wathe 门逻辑不执行，按住时每 4 tick 重发。 */
    private static DoorInteraction.DoorInteractionResult onDoorInteraction(DoorInteraction.DoorInteractionContext context) {
        if (!context.getHandItem().isOf(SparkStrengthItems.tacticalTomahawk())
                || !UndercoverRoles.isLivingStage2(context.getPlayer())) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        UndercoverRules.DoorKind kind = kindOf(context.getState());
        if (kind == null || !blocksHolder(context)) {
            return DoorInteraction.DoorInteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayerEntity player && context.getWorld() instanceof ServerWorld world) {
            onBreach(world, player, context.getLowerPos(), kind);
        }
        return DoorInteraction.DoorInteractionResult.HANDLED;
    }

    /** Glass, wood or steel; other doors (vanilla metal sheet, cockpit) are not breachable. / 玻璃、木或钢；其他门（原版金属片门、驾驶室门）不可拆。 */
    public static @Nullable UndercoverRules.DoorKind kindOf(BlockState state) {
        if (state.isOf(WatheBlocks.SMALL_GLASS_DOOR)) {
            return UndercoverRules.DoorKind.GLASS;
        }
        if (state.isOf(WatheBlocks.SMALL_WOOD_DOOR)) {
            return UndercoverRules.DoorKind.WOOD;
        }
        return state.getBlock() instanceof TrainDoorBlock ? UndercoverRules.DoorKind.STEEL : null;
    }

    /**
     * A closed, intact door the holder could not open by hand: keyed or jammed small doors, or a train door while the
     * round runs. An unlocked door opens as usual instead.
     * 关着且完好、持有者徒手打不开的门：带钥匙名或被堵的小门，或对局中的列车门。没锁的门照常打开。
     */
    private static boolean blocksHolder(DoorInteraction.DoorInteractionContext context) {
        DoorBlockEntity door = context.getEntity();
        if (door.isOpen() || door.isBlasted() || context.getPlayer().isCreative()) {
            return false;
        }
        if (context.getDoorType() == DoorInteraction.DoorType.TRAIN_DOOR) {
            return GameWorldComponent.KEY.get(context.getWorld()).getGameStatus()
                    != GameWorldComponent.GameStatus.INACTIVE;
        }
        return !door.getKeyName().isEmpty() || door.isJammed();
    }

    private static void onBreach(ServerWorld world, ServerPlayerEntity player, BlockPos lowerPos,
                                 UndercoverRules.DoorKind kind) {
        if (SabotageActionLocks.isLocked(player)) {
            return;
        }
        if (player.getItemCooldownManager().isCoolingDown(SparkStrengthItems.tacticalTomahawk())) {
            player.sendMessage(Text.translatable("message.sparkstrength.undercover.tomahawk_cooling"), true);
            return;
        }
        long now = world.getTime();
        Channel current = CHANNELS.get(player.getUuid());
        if (current != null && current.lowerPos.equals(lowerPos) && current.world == world.getRegistryKey()) {
            current.lastHeartbeatTick = now;
            return;
        }
        stop(player, ChannelProgressS2CPacket.STATE_STOPPED);
        Channel channel = new Channel(world.getRegistryKey(), lowerPos, kind, now);
        CHANNELS.put(player.getUuid(), channel);
        send(player, channel, ChannelProgressS2CPacket.STATE_RUNNING);
    }

    /** Only the channel the client means; ids from the junction boxes fall through. / 只取消客户端所指的读条；接线箱的编号直接略过。 */
    public static boolean handleCancel(ServerPlayerEntity player, int channelId) {
        Channel channel = CHANNELS.get(player.getUuid());
        if (channel != null && channel.id == channelId) {
            stop(player, ChannelProgressS2CPacket.STATE_STOPPED);
            return true;
        }
        return false;
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        Iterator<Map.Entry<UUID, Channel>> iterator = CHANNELS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Channel> entry = iterator.next();
            Channel channel = entry.getValue();
            if (channel.world != world.getRegistryKey()) {
                continue;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.getServerWorld() != world) {
                iterator.remove();
                continue;
            }
            if (!isStillValid(world, player, channel, now)) {
                iterator.remove();
                clearCracks(world, channel.lowerPos);
                send(player, channel, ChannelProgressS2CPacket.STATE_STOPPED);
                continue;
            }
            channel.progress++;
            long elapsed = now - channel.startTick;
            if (elapsed % CHOP_SOUND_INTERVAL_TICKS == 0) {
                world.playSound(null, channel.lowerPos.up(), chopSound(channel.kind), SoundCategory.BLOCKS,
                        0.8F, 0.9F + world.random.nextFloat() * 0.2F);
            }
            int stage = Math.min(9, channel.progress * 10 / channel.kind.breachTicks());
            world.setBlockBreakingInfo(crackId(channel.lowerPos), channel.lowerPos, stage);
            world.setBlockBreakingInfo(crackId(channel.lowerPos.up()), channel.lowerPos.up(), stage);
            if (channel.progress >= channel.kind.breachTicks()) {
                iterator.remove();
                send(player, channel, ChannelProgressS2CPacket.STATE_FINISHED);
                removeDoor(world, channel.lowerPos, channel.kind, player);
                player.getItemCooldownManager().set(SparkStrengthItems.tacticalTomahawk(),
                        channel.kind.cooldownTicks());
                continue;
            }
            if (now - channel.lastResyncTick >= SabotageRules.PROGRESS_RESYNC_TICKS) {
                channel.lastResyncTick = now;
                send(player, channel, ChannelProgressS2CPacket.STATE_RUNNING);
            }
        }
    }

    private static boolean isStillValid(ServerWorld world, ServerPlayerEntity player, Channel channel, long now) {
        if (!UndercoverRoles.isLivingStage2(player) || player.isSneaking() || SabotageActionLocks.isLocked(player)
                || !player.getMainHandStack().isOf(SparkStrengthItems.tacticalTomahawk())
                || player.getItemCooldownManager().isCoolingDown(SparkStrengthItems.tacticalTomahawk())
                || now - channel.lastHeartbeatTick > SabotageRules.HEARTBEAT_TIMEOUT_TICKS) {
            return false;
        }
        BlockState state = world.getBlockState(channel.lowerPos);
        if (kindOf(state) != channel.kind || !(world.getBlockEntity(channel.lowerPos) instanceof DoorBlockEntity door)
                || door.isOpen() || door.isBlasted()) {
            return false;
        }
        double reach = SabotageRules.CHANNEL_RANGE + 0.5;
        if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(channel.lowerPos).add(0.0, 0.5, 0.0)) > reach * reach) {
            return false;
        }
        HitResult hit = player.raycast(reach, 1.0F, false);
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && (blockHit.getBlockPos().equals(channel.lowerPos) || blockHit.getBlockPos().equals(channel.lowerPos.up()));
    }

    /** Takes both halves (and the paired leaf) out of the world, keeping exactly what was there. / 把上下两半（及成对的另一扇）从世界里拿走，并原样记下。 */
    private static void removeDoor(ServerWorld world, BlockPos lowerPos, UndercoverRules.DoorKind kind,
                                   ServerPlayerEntity player) {
        List<BlockPos> lowers = new ArrayList<>();
        lowers.add(lowerPos);
        BlockState lowerState = world.getBlockState(lowerPos);
        if (lowerState.getBlock() instanceof SmallDoorBlock) {
            SmallDoorBlockEntity neighbor = SmallDoorBlock.getNeighborDoorEntity(lowerState, world, lowerPos);
            if (neighbor != null) {
                lowers.add(neighbor.getPos().toImmutable());
            }
        }
        // Log before the block entity (and its key name) disappears. / 在方块实体（及钥匙名）消失前记录。
        DoorLogService.recordBroken(world, lowerPos);
        // Capture everything first: removing one half makes the other one update itself away.
        // 先全部记下：移除一半会让另一半在更新中自行消失。
        List<RemovedBlock> taken = new ArrayList<>();
        for (BlockPos lower : lowers) {
            for (BlockPos pos : List.of(lower, lower.up())) {
                BlockState state = world.getBlockState(pos);
                if (kindOf(state) == null) {
                    continue;
                }
                BlockEntity blockEntity = world.getBlockEntity(pos);
                taken.add(new RemovedBlock(pos.toImmutable(), state, blockEntity == null ? null
                        : blockEntity.createNbtWithIdentifyingData(world.getRegistryManager())));
            }
        }
        for (RemovedBlock block : taken) {
            world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, block.pos(), Block.getRawIdFromState(block.state()));
            world.setBlockState(block.pos(), Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
            world.setBlockBreakingInfo(crackId(block.pos()), block.pos(), -1);
        }
        REMOVED.computeIfAbsent(world.getRegistryKey(), key -> new ArrayList<>()).addAll(taken);
        world.playSound(null, lowerPos.up(), breakSound(kind), SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.swingHand(Hand.MAIN_HAND, true);
        NbtCompound extra = new NbtCompound();
        GameRecordManager.putBlockPos(extra, "pos", lowerPos);
        GameRecordManager.recordItemUse(player, SparkStrengthItems.TACTICAL_TOMAHAWK_ID, null, extra);
    }

    /** Round end and server stop: every removed door goes back where it was, unless something else is there now. / 回合结束与服务器关闭：被拆的门原样放回，除非那里已有别的东西。 */
    public static void restoreAll(ServerWorld world) {
        CHANNELS.values().removeIf(channel -> channel.world == world.getRegistryKey());
        List<RemovedBlock> removed = REMOVED.remove(world.getRegistryKey());
        if (removed == null) {
            return;
        }
        for (RemovedBlock block : removed) {
            if (world.getBlockState(block.pos()).isAir()) {
                world.setBlockState(block.pos(), block.state(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
        }
        for (RemovedBlock block : removed) {
            BlockEntity blockEntity = world.getBlockEntity(block.pos());
            if (block.blockEntity() != null && blockEntity != null && world.getBlockState(block.pos()) == block.state()) {
                blockEntity.read(block.blockEntity(), world.getRegistryManager());
                blockEntity.markDirty();
                world.updateListeners(block.pos(), block.state(), block.state(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static SoundEvent chopSound(UndercoverRules.DoorKind kind) {
        return switch (kind) {
            case GLASS -> SoundEvents.BLOCK_GLASS_HIT;
            case WOOD -> SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR;
            case STEEL -> SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR;
        };
    }

    private static SoundEvent breakSound(UndercoverRules.DoorKind kind) {
        return switch (kind) {
            case GLASS -> SoundEvents.BLOCK_GLASS_BREAK;
            case WOOD -> SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR;
            case STEEL -> SoundEvents.BLOCK_ANVIL_LAND;
        };
    }

    private static void stop(ServerPlayerEntity player, int state) {
        Channel channel = CHANNELS.remove(player.getUuid());
        if (channel != null) {
            clearCracks(player.getServerWorld(), channel.lowerPos);
            send(player, channel, state);
        }
    }

    private static void clearCracks(ServerWorld world, BlockPos lowerPos) {
        world.setBlockBreakingInfo(crackId(lowerPos), lowerPos, -1);
        world.setBlockBreakingInfo(crackId(lowerPos.up()), lowerPos.up(), -1);
    }

    private static void send(ServerPlayerEntity player, Channel channel, int state) {
        if (ServerPlayNetworking.canSend(player, ChannelProgressS2CPacket.ID)) {
            ServerPlayNetworking.send(player, new ChannelProgressS2CPacket(channel.id,
                    ChannelProgressS2CPacket.KIND_BREACH, channel.lowerPos, state, channel.progress,
                    channel.kind.breachTicks(), 1));
        }
    }

    /** A stable negative breaker id per block, never a real entity id. / 每格一个稳定的负数破坏者 id，永远不会是真实实体 id。 */
    private static int crackId(BlockPos pos) {
        return -2048 - Math.floorMod(pos.hashCode() * 31 + 7, 1 << 27);
    }
}
