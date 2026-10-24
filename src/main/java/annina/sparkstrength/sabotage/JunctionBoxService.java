package annina.sparkstrength.sabotage;

import annina.sparkstrength.SparkStrengthBlocks;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.block.JunctionBoxBlock;
import annina.sparkstrength.compat.SparkTraitsSabotageCompat;
import annina.sparkstrength.component.sabotage.SabotageWorldComponent;
import annina.sparkstrength.network.sabotage.ChannelProgressS2CPacket;
import annina.sparkstrength.role.undercover.UndercoverService;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.item.RepairToolItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Junction box interactions: prying with a lockpick (continuous, the damage stays), hacking with the tablet (restarts
 * when interrupted), weapon hits from other mods, the Engineer's repair, healing and the vanilla crack overlay. Server
 * thread only.
 * 接线箱交互：用开锁器撬（可连续，伤害保留）、用平板入侵（中断后重来）、其他模组的武器命中、工程师修复、自然恢复与原版裂纹显示。
 * 仅限服务端线程。
 *
 * <p>Holding right-click re-sends the block interaction every 4 ticks; each one refreshes the channel's heartbeat, and
 * the client sends {@code CancelChannelC2SPacket} the moment the button is released or the aim leaves the box.
 * 按住右键时原版每 4 tick 重发一次方块交互，每次都刷新读条心跳；松开按键或准星离开箱子时，客户端立刻发送取消包。</p>
 */
public final class JunctionBoxService {
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static int nextChannelId = 1;

    private JunctionBoxService() {
    }

    /** Wathe's lockpick, or the Impostor's pry tool, which pries boxes the same way. / Wathe 开锁器，或撬箱效果相同的内鬼撬锁工具。 */
    public static boolean isPryTool(ItemStack stack) {
        return stack.isOf(WatheItems.LOCKPICK) || stack.isOf(SparkStrengthItems.impostorLockpick());
    }

    private static final class Channel {
        final int id = nextChannelId++;
        final int kind;
        /** The box's sabotage when the channel began; a rewired box ends it. / 读条开始时箱子的破坏类型；箱子改线后读条结束。 */
        final SabotageType type;
        final RegistryKey<World> world;
        final BlockPos pos;
        final int total;
        final int rate;
        long lastHeartbeatTick;
        long lastResyncTick;
        long startTick;
        int progress;

        Channel(int kind, SabotageType type, RegistryKey<World> world, BlockPos pos, int total, int rate, long now) {
            this.kind = kind;
            this.type = type;
            this.world = world;
            this.pos = pos.toImmutable();
            this.total = total;
            this.rate = rate;
            this.lastHeartbeatTick = now;
            this.lastResyncTick = now;
            this.startTick = now;
        }
    }

    public static void onPry(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!SabotageAccess.canPryOrHack(player)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.cannot_pry"), true);
            return;
        }
        if (SabotageActionLocks.isLocked(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        if (!sabotage.isMapReady()) {
            return;
        }
        JunctionBoxState look = state.get(JunctionBoxBlock.STATE);
        if (look != JunctionBoxState.CLOSED) {
            player.sendMessage(Text.translatable(look == JunctionBoxState.BROKEN
                    ? "message.sparkstrength.sabotage.box_already_open"
                    : "message.sparkstrength.sabotage.box_already_hacked"), true);
            return;
        }
        SabotageType type = JunctionBoxBlock.typeOf(state);
        if (type == null) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.box_unwired"), true);
            return;
        }
        if (!type.availableOn(sabotage.hasEngineUnits())) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.no_engine_units"), true);
            return;
        }
        if (sabotage.isUnlocked(type)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.already_unlocked",
                    Text.translatable(type.translationKey())), true);
            return;
        }
        long now = world.getTime();
        Channel current = CHANNELS.get(player.getUuid());
        if (current != null && current.kind == ChannelProgressS2CPacket.KIND_PRY && current.pos.equals(pos)
                && current.world == world.getRegistryKey()) {
            current.lastHeartbeatTick = now;
            return;
        }
        stop(player, ChannelProgressS2CPacket.STATE_STOPPED);
        sabotage.addJunctionBox(pos);
        int rate = SabotageRules.pryPerTick(SparkTraitsSabotageCompat.isMasterSaboteur(player));
        Channel channel = new Channel(ChannelProgressS2CPacket.KIND_PRY, type, world.getRegistryKey(), pos,
                SabotageRules.DURABILITY_MAX, rate, now);
        channel.progress = SabotageRules.DURABILITY_MAX - sabotage.box(pos).durability;
        CHANNELS.put(player.getUuid(), channel);
        world.playSound(null, pos, SparkStrengthSounds.JUNCTION_BOX_PRY, SoundCategory.BLOCKS, 1.0F, 1.0F);
        send(player, channel, ChannelProgressS2CPacket.STATE_RUNNING);
    }

    public static void onHack(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!SabotageAccess.canPryOrHack(player)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.cannot_hack"), true);
            return;
        }
        if (SabotageActionLocks.isLocked(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        if (!sabotage.isMapReady() || state.get(JunctionBoxBlock.STATE) != JunctionBoxState.BROKEN) {
            return;
        }
        SabotageType type = JunctionBoxBlock.typeOf(state);
        if (type == null || !type.availableOn(sabotage.hasEngineUnits())) {
            return;
        }
        if (sabotage.isUnlocked(type)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.already_unlocked",
                    Text.translatable(type.translationKey())), true);
            return;
        }
        long now = world.getTime();
        Channel current = CHANNELS.get(player.getUuid());
        if (current != null && current.kind == ChannelProgressS2CPacket.KIND_HACK && current.pos.equals(pos)
                && current.world == world.getRegistryKey()) {
            current.lastHeartbeatTick = now;
            return;
        }
        stop(player, ChannelProgressS2CPacket.STATE_STOPPED);
        int total = SabotageRules.hackTicks(SparkTraitsSabotageCompat.isMasterSaboteur(player));
        Channel channel = new Channel(ChannelProgressS2CPacket.KIND_HACK, type, world.getRegistryKey(), pos, total, 1,
                now);
        CHANNELS.put(player.getUuid(), channel);
        send(player, channel, ChannelProgressS2CPacket.STATE_RUNNING);
    }

    public static void onRepair(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!SabotageAccess.isEngineer(player)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.engineer_only"), true);
            return;
        }
        if (player.getItemCooldownManager().isCoolingDown(ModItems.REPAIR_TOOL)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.repair_tool_cooling"), true);
            return;
        }
        ServerWorld world = player.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        SabotageWorldComponent.BoxProgress progress = sabotage.existingBox(pos);
        boolean open = state.get(JunctionBoxBlock.STATE).isOpen();
        if (!open && (progress == null || progress.durability >= SabotageRules.DURABILITY_MAX)) {
            player.sendMessage(Text.translatable("message.sparkstrength.sabotage.box_intact"), true);
            return;
        }
        stopAllAt(world, pos);
        if (open) {
            world.setBlockState(pos, state.with(JunctionBoxBlock.STATE, JunctionBoxState.CLOSED), Block.NOTIFY_ALL);
        }
        if (progress != null) {
            progress.durability = SabotageRules.DURABILITY_MAX;
        }
        world.setBlockBreakingInfo(crackId(pos), pos, -1);
        sabotage.forgetBox(pos);
        player.getItemCooldownManager().set(ModItems.REPAIR_TOOL, RepairToolItem.COOLDOWN_TICKS);
        world.playSound(null, pos, SparkStrengthSounds.JUNCTION_BOX_REPAIR, SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.sendMessage(Text.translatable("message.sparkstrength.sabotage.box_repaired"), true);
        SabotageType repairedType = JunctionBoxBlock.typeOf(state);
        if (repairedType != null) {
            sabotage.log(world.getTime(), SabotageWorldComponent.LogKind.BOX_REPAIRED, repairedType);
        }
    }

    /**
     * Frozen cross-mod seam: a weapon hit on a closed junction box, e.g. the SparkWitch Hunter's shotgun. SparkWitch's
     * {@code compat/SparkStrengthJunctionBoxCompat} reaches it through reflection, so the class
     * {@code annina.sparkstrength.sabotage.JunctionBoxService}, the name {@code damageByWeapon} and the parameter types
     * {@code (ServerPlayerEntity, BlockPos, int)} are frozen. Server thread only.
     * 冻结的跨模组接缝：武器命中未打开的接线箱，例如 SparkWitch 猎人的猎枪。SparkWitch 的
     * compat/SparkStrengthJunctionBoxCompat 通过反射调用，因此类名、方法名 damageByWeapon 与参数类型
     * (ServerPlayerEntity, BlockPos, int) 均已冻结。仅限服务端线程。
     *
     * <p>Same gates as {@link #onPry}, but silent (a shot is not an interaction) and fail-closed: a null argument,
     * {@code damage <= 0}, a client world or any refused gate returns false and changes nothing. The caller owns
     * {@code damage}, in durability units ({@link SabotageRules#DURABILITY_MAX} = 100%); Master Saboteur adds nothing.
     * A hit delays healing like a pry tick; the crack overlay and anyone prying the same box catch up in {@link #tick};
     * a hit that empties the box opens it exactly like a pry, credited to {@code attacker}.
     * 门槛与 onPry 相同，但静默（开枪不是交互）且失败即关闭：参数为 null、damage <= 0、客户端世界或任一门槛不通过时返回
     * false 且不做任何改动。damage 由调用方决定，单位为耐久（DURABILITY_MAX = 100%）；破坏大师不加成。命中像撬一样推迟恢复；
     * 裂纹与正在撬同一箱子的人在 tick 中跟上；打空耐久的一击与撬开完全相同地打开箱子，记在 attacker 名下。</p>
     *
     * @return true iff the box at {@code pos} took the damage / 仅当 pos 处的箱子确实受到伤害时返回 true
     */
    public static boolean damageByWeapon(ServerPlayerEntity attacker, BlockPos pos, int damage) {
        if (attacker == null || pos == null || damage <= 0 || attacker.getWorld().isClient()) {
            return false;
        }
        if (!SabotageAccess.canPryOrHack(attacker) || SabotageActionLocks.isLocked(attacker)) {
            return false;
        }
        ServerWorld world = attacker.getServerWorld();
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        if (!sabotage.isMapReady()) {
            return false;
        }
        BlockPos at = pos.toImmutable();
        BlockState state = world.getBlockState(at);
        if (!state.isOf(SparkStrengthBlocks.junctionBox())
                || state.get(JunctionBoxBlock.STATE) != JunctionBoxState.CLOSED) {
            return false;
        }
        SabotageType type = JunctionBoxBlock.typeOf(state);
        if (type == null || !type.availableOn(sabotage.hasEngineUnits()) || sabotage.isUnlocked(type)) {
            return false;
        }
        sabotage.addJunctionBox(at);
        SabotageWorldComponent.BoxProgress box = sabotage.box(at);
        box.durability = SabotageRules.pry(box.durability, damage);
        box.lastDamageTick = world.getTime();
        if (box.durability <= 0) {
            // Pry channels on this box stop on their next tick, since isStillValid wants CLOSED.
            // 此箱上的撬读条在下一 tick 停止，因为 isStillValid 要求 CLOSED。
            breakOpen(world, sabotage, at, state, attacker);
        } else {
            Vec3d front = frontOf(at, state.get(JunctionBoxBlock.FACING));
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 10, 0.12, 0.12, 0.12, 0.35);
            world.playSound(null, at, SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 0.6F,
                    1.3F + world.random.nextFloat() * 0.2F);
        }
        return true;
    }

    /** Only the channel the client means; a stale cancel for an earlier box is ignored. / 只取消客户端所指的读条；针对之前箱子的过期取消被忽略。 */
    public static void handleCancel(ServerPlayerEntity player, int channelId) {
        Channel channel = CHANNELS.get(player.getUuid());
        if (channel != null && channel.id == channelId) {
            stop(player, ChannelProgressS2CPacket.STATE_STOPPED);
        }
    }

    public static void onDisconnect(ServerPlayerEntity player) {
        CHANNELS.remove(player.getUuid());
    }

    public static void clear(ServerWorld world) {
        Iterator<Map.Entry<UUID, Channel>> iterator = CHANNELS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Channel> entry = iterator.next();
            if (entry.getValue().world == world.getRegistryKey()) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
                iterator.remove();
                if (player != null) {
                    send(player, entry.getValue(), ChannelProgressS2CPacket.STATE_STOPPED);
                }
            }
        }
        SabotageWorldComponent sabotage = SabotageWorldComponent.KEY.get(world);
        for (BlockPos pos : sabotage.boxProgress().keySet()) {
            world.setBlockBreakingInfo(crackId(pos), pos, -1);
        }
    }

    public static void tick(ServerWorld world, SabotageWorldComponent sabotage) {
        long now = world.getTime();
        Set<BlockPos> pried = new HashSet<>();
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
            BlockState state = world.getBlockState(channel.pos);
            if (!isStillValid(player, channel, state, sabotage, now)) {
                iterator.remove();
                send(player, channel, ChannelProgressS2CPacket.STATE_STOPPED);
                continue;
            }
            if (channel.kind == ChannelProgressS2CPacket.KIND_PRY) {
                pried.add(channel.pos);
                SabotageWorldComponent.BoxProgress box = sabotage.box(channel.pos);
                box.durability = SabotageRules.pry(box.durability, channel.rate);
                box.lastDamageTick = now;
                channel.progress = SabotageRules.DURABILITY_MAX - box.durability;
                long elapsed = now - channel.startTick;
                if (elapsed > 0 && elapsed % SabotageRules.PRY_SOUND_INTERVAL_TICKS == 0) {
                    world.playSound(null, channel.pos, SparkStrengthSounds.JUNCTION_BOX_PRY, SoundCategory.BLOCKS,
                            1.0F, 0.9F + world.random.nextFloat() * 0.2F);
                }
                if (elapsed % SabotageRules.SWING_INTERVAL_TICKS == 0) {
                    player.swingHand(Hand.MAIN_HAND, true);
                }
                if (box.durability <= 0) {
                    iterator.remove();
                    send(player, channel, ChannelProgressS2CPacket.STATE_FINISHED);
                    breakOpen(world, sabotage, channel.pos, state, player);
                    continue;
                }
            } else {
                channel.progress++;
                long elapsed = now - channel.startTick;
                if (elapsed > 0 && elapsed % SabotageRules.HACK_SOUND_INTERVAL_TICKS == 0) {
                    world.playSound(null, channel.pos, SparkStrengthSounds.JUNCTION_BOX_HACK, SoundCategory.BLOCKS,
                            0.5F, 1.2F + world.random.nextFloat() * 0.6F);
                }
                if (channel.progress >= channel.total) {
                    iterator.remove();
                    send(player, channel, ChannelProgressS2CPacket.STATE_FINISHED);
                    hack(world, sabotage, channel.pos, state, player);
                    continue;
                }
            }
            if (now - channel.lastResyncTick >= SabotageRules.PROGRESS_RESYNC_TICKS) {
                channel.lastResyncTick = now;
                send(player, channel, ChannelProgressS2CPacket.STATE_RUNNING);
            }
        }
        tickBoxes(world, sabotage, pried, now);
    }

    private static boolean isStillValid(ServerPlayerEntity player, Channel channel, BlockState state,
                                        SabotageWorldComponent sabotage, long now) {
        if (!SabotageAccess.canPryOrHack(player) || player.isSneaking() || SabotageActionLocks.isLocked(player)
                || now - channel.lastHeartbeatTick > SabotageRules.HEARTBEAT_TIMEOUT_TICKS) {
            return false;
        }
        if (!state.isOf(SparkStrengthBlocks.junctionBox()) || JunctionBoxBlock.typeOf(state) != channel.type
                || sabotage.isUnlocked(channel.type) || !channel.type.availableOn(sabotage.hasEngineUnits())) {
            // Rewired by the configurator mid-channel, or the map lost its last engine unit. / 读条途中被配置器改线，或地图失去了最后一台动力装置。
            return false;
        }
        JunctionBoxState expected = channel.kind == ChannelProgressS2CPacket.KIND_PRY
                ? JunctionBoxState.CLOSED : JunctionBoxState.BROKEN;
        if (state.get(JunctionBoxBlock.STATE) != expected) {
            return false;
        }
        ItemStack held = player.getMainHandStack();
        boolean rightItem = channel.kind == ChannelProgressS2CPacket.KIND_PRY
                ? isPryTool(held) : held.isOf(SparkStrengthItems.tablet());
        if (!rightItem) {
            return false;
        }
        double reach = SabotageRules.CHANNEL_RANGE + 0.5;
        if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(channel.pos)) > reach * reach) {
            return false;
        }
        HitResult hit = player.raycast(SabotageRules.CHANNEL_RANGE + 0.5, 1.0F, false);
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(channel.pos);
    }

    private static void tickBoxes(ServerWorld world, SabotageWorldComponent sabotage, Set<BlockPos> pried, long now) {
        Iterator<Map.Entry<BlockPos, SabotageWorldComponent.BoxProgress>> iterator =
                sabotage.boxProgress().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, SabotageWorldComponent.BoxProgress> entry = iterator.next();
            BlockPos pos = entry.getKey();
            SabotageWorldComponent.BoxProgress box = entry.getValue();
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(SparkStrengthBlocks.junctionBox())) {
                world.setBlockBreakingInfo(crackId(pos), pos, -1);
                iterator.remove();
                continue;
            }
            boolean open = state.get(JunctionBoxBlock.STATE).isOpen();
            if (!pried.contains(pos)) {
                box.durability = SabotageRules.regen(box.durability, now - box.lastDamageTick, open);
            }
            int stage = SabotageRules.crackStage(box.durability, open);
            if (stage != box.lastCrackStage
                    || (stage >= 0 && now - box.lastCrackSentTick >= SabotageRules.CRACK_REFRESH_TICKS)) {
                world.setBlockBreakingInfo(crackId(pos), pos, stage);
                box.lastCrackStage = stage;
                box.lastCrackSentTick = now;
            }
            if (!open && box.durability >= SabotageRules.DURABILITY_MAX && stage < 0) {
                iterator.remove();
            }
        }
    }

    private static void breakOpen(ServerWorld world, SabotageWorldComponent sabotage, BlockPos pos, BlockState state,
                                  ServerPlayerEntity player) {
        world.setBlockState(pos, state.with(JunctionBoxBlock.STATE, JunctionBoxState.BROKEN), Block.NOTIFY_ALL);
        world.setBlockBreakingInfo(crackId(pos), pos, -1);
        Vec3d front = frontOf(pos, state.get(JunctionBoxBlock.FACING));
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 40, 0.2, 0.2, 0.2, 0.6);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, front.x, front.y, front.z, 6, 0.15, 0.15, 0.15, 0.01);
        world.playSound(null, pos, SparkStrengthSounds.JUNCTION_BOX_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.sendMessage(Text.translatable("message.sparkstrength.sabotage.box_pried"), true);
        SabotageType type = JunctionBoxBlock.typeOf(state);
        if (type != null) {
            sabotage.log(world.getTime(), SabotageWorldComponent.LogKind.BOX_BROKEN, player.getUuid(),
                    player.getGameProfile().getName(), type);
        }
    }

    private static void hack(ServerWorld world, SabotageWorldComponent sabotage, BlockPos pos, BlockState state,
                             ServerPlayerEntity player) {
        world.setBlockState(pos, state.with(JunctionBoxBlock.STATE, JunctionBoxState.HACKED), Block.NOTIFY_ALL);
        Vec3d front = frontOf(pos, state.get(JunctionBoxBlock.FACING));
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 16, 0.15, 0.15, 0.15, 0.3);
        world.playSound(null, pos, SparkStrengthSounds.JUNCTION_BOX_HACKED, SoundCategory.BLOCKS, 0.7F, 1.0F);
        SabotageType type = JunctionBoxBlock.typeOf(state);
        if (type != null) {
            SabotageService.unlock(world, sabotage, type, player);
        }
        UndercoverService.onHackCompleted(player);
    }

    private static void stop(ServerPlayerEntity player, int state) {
        Channel channel = CHANNELS.remove(player.getUuid());
        if (channel != null) {
            send(player, channel, state);
        }
    }

    private static void stopAllAt(ServerWorld world, BlockPos pos) {
        Iterator<Map.Entry<UUID, Channel>> iterator = CHANNELS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Channel> entry = iterator.next();
            Channel channel = entry.getValue();
            if (channel.world == world.getRegistryKey() && channel.pos.equals(pos)) {
                iterator.remove();
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
                if (player != null) {
                    send(player, channel, ChannelProgressS2CPacket.STATE_STOPPED);
                }
            }
        }
    }

    private static void send(ServerPlayerEntity player, Channel channel, int state) {
        if (ServerPlayNetworking.canSend(player, ChannelProgressS2CPacket.ID)) {
            ServerPlayNetworking.send(player, new ChannelProgressS2CPacket(channel.id, channel.kind, channel.pos, state,
                    channel.progress, channel.total, channel.rate));
        }
    }

    /**
     * Just in front of the box face. The box hugs the wall behind it (z 12-16 for north), so its face sits a quarter
     * block from the center, on the side away from {@code facing}.
     * 紧贴箱子正面的位置。箱子贴着身后的墙（朝北时 z 为 12-16），所以正面在离中心四分之一格、背离 facing 的一侧。
     */
    public static Vec3d frontOf(BlockPos pos, Direction facing) {
        Vec3d center = Vec3d.ofCenter(pos);
        return center.add(-facing.getOffsetX() * 0.15, 0.0, -facing.getOffsetZ() * 0.15);
    }

    /** A stable negative breaker id per box, never a real entity id. / 每个箱子一个稳定的负数破坏者 id，永远不会是真实实体 id。 */
    static int crackId(BlockPos pos) {
        return -1024 - Math.floorMod(pos.hashCode(), 1 << 28);
    }
}
