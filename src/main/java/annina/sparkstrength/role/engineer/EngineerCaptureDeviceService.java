package annina.sparkstrength.role.engineer;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.entity.CaptureDeviceEntity;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative capture-device domain service.
 * 服务端权威的捕捉装置领域服务。
 *
 * <p>Items and entities adapt Minecraft lifecycle/state into this Module. This service owns placement,
 * candidate selection, capture effects, reports, sounds, replay events, and round cleanup.
 * 物品与实体只把 Minecraft 生命周期/状态接入本 Module；放置、候选筛选、捕捉效果、报告、声音、
 * 回放与局清理由本服务所有。</p>
 */
public final class EngineerCaptureDeviceService {
    private EngineerCaptureDeviceService() {
    }

    public static ActionResult place(ItemUsageContext context) {
        Direction side = context.getSide();
        if (side != Direction.UP && side != Direction.DOWN) {
            return ActionResult.PASS;
        }

        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }

        World world = player.getWorld();
        if (!world.isClient()) {
            CaptureDeviceEntity entity = SparkStrengthEntities.captureDevice().create(world);
            if (entity == null) {
                return ActionResult.FAIL;
            }

            Vec3d hitPos = context.getHitPos();
            entity.setPosition(hitPos.x, hitPos.y, hitPos.z);
            entity.setYaw(player.getHeadYaw());
            entity.setOwnerUuid(player.getUuid());
            entity.setCeilingMounted(side == Direction.DOWN);
            world.spawnEntity(entity);

            if (player instanceof ServerPlayerEntity serverPlayer) {
                recordPlaced(serverPlayer);
                serverPlayer.playSoundToPlayer(
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        SoundCategory.PLAYERS,
                        1.0F,
                        1.0F
                );
            }

            if (!player.isCreative()) {
                player.getStackInHand(context.getHand()).decrement(1);
            }
        }

        return ActionResult.SUCCESS;
    }

    /**
     * Handles one server tick while the entity retains only tracked and persisted Minecraft state.
     * 处理一次服务端 tick；实体仅保留追踪与持久化所需的 Minecraft 状态。
     */
    public static void tick(CaptureDeviceEntity entity, ServerWorld world, int lifetimeTicks) {
        if (EngineerRules.decideCaptureTick(lifetimeTicks, false) == EngineerRules.CaptureTickDecision.EXPIRE) {
            recordExpired(world, entity.getOwnerUuid());
            entity.discard();
            return;
        }

        List<ServerPlayerEntity> capturedPlayers = findCapturedPlayers(entity, world);
        if (EngineerRules.decideCaptureTick(lifetimeTicks, !capturedPlayers.isEmpty())
                == EngineerRules.CaptureTickDecision.TRIGGER) {
            trigger(entity.getOwnerUuid(), world, capturedPlayers);
            entity.discard();
        }
    }

    public static void recordReleased(ServerPlayerEntity player) {
        GameRecordManager.recordGlobalEvent(
                player.getServerWorld(),
                SparkStrengthReplayFormatters.CAPTURE_DEVICE_RELEASED,
                player,
                null
        );
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        EngineerStunnedPlayerComponent.KEY.get(player).clear();
    }

    public static void clearRoundState(ServerWorld world) {
        for (CaptureDeviceEntity entity : world.getEntitiesByType(
                SparkStrengthEntities.captureDevice(),
                captureDevice -> true
        )) {
            entity.discard();
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            clearPlayer(player);
        }
    }

    private static List<ServerPlayerEntity> findCapturedPlayers(
            CaptureDeviceEntity entity,
            ServerWorld world
    ) {
        UUID ownerUuid = entity.getOwnerUuid();
        Box box = entity.getBoundingBox().expand(EngineerRules.CAPTURE_RADIUS);
        return world.getEntitiesByClass(ServerPlayerEntity.class, box, player ->
                EngineerRules.isCaptureCandidate(
                        player.getUuid().equals(ownerUuid),
                        GameFunctions.isPlayerPlayingAndAlive(player),
                        GameFunctions.isPlayerAliveAndSurvival(player),
                        player.squaredDistanceTo(entity)
                )
        );
    }

    private static void trigger(
            @Nullable UUID ownerUuid,
            ServerWorld world,
            List<ServerPlayerEntity> capturedPlayers
    ) {
        for (ServerPlayerEntity player : capturedPlayers) {
            stunAndNotify(player);
        }
        giveReport(ownerUuid, world, capturedPlayers);
    }

    private static void stunAndNotify(ServerPlayerEntity player) {
        // CCA stores/syncs stun state; the client blocks input while the server locks position and velocity.
        // CCA 保存并同步定身状态；客户端禁输入，服务端锁位置并清速度。
        EngineerStunnedPlayerComponent.KEY.get(player).stun(EngineerRules.CAPTURE_STUN_TICKS);
        player.sendMessage(Text.translatable("message.sparkstrength.capture_device.captured")
                .formatted(Formatting.RED), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 1.0F);
        GameRecordManager.recordGlobalEvent(
                player.getServerWorld(),
                SparkStrengthReplayFormatters.CAPTURE_DEVICE_TRIGGERED,
                player,
                null
        );
    }

    private static void giveReport(
            @Nullable UUID ownerUuid,
            ServerWorld world,
            List<ServerPlayerEntity> capturedPlayers
    ) {
        if (ownerUuid == null) {
            return;
        }

        PlayerEntity owner = world.getPlayerByUuid(ownerUuid);
        if (owner == null) {
            return;
        }

        EngineerCaptureReport.removeOldReports(owner);
        ItemStack reportStack = createReport(owner, capturedPlayers);
        owner.getInventory().offerOrDrop(reportStack);
        if (owner instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static ItemStack createReport(
            PlayerEntity owner,
            List<ServerPlayerEntity> capturedPlayers
    ) {
        ItemStack reportStack = Items.PAPER.getDefaultStack();
        reportStack.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.sparkstrength.capture_report").formatted(Formatting.RESET, Formatting.GOLD));
        EngineerCaptureReport.mark(reportStack, owner.getUuid());

        List<Text> loreLines = new ArrayList<>();
        loreLines.add(Text.translatable("item.sparkstrength.capture_report.tooltip", capturedPlayers.size())
                .formatted(Formatting.GRAY));
        for (ServerPlayerEntity capturedPlayer : capturedPlayers) {
            loreLines.add(Text.literal(" - " + capturedPlayer.getName().getString())
                    .formatted(Formatting.WHITE));
        }
        reportStack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
        return reportStack;
    }

    private static void recordPlaced(ServerPlayerEntity player) {
        GameRecordManager.recordGlobalEvent(
                player.getServerWorld(),
                SparkStrengthReplayFormatters.CAPTURE_DEVICE_PLACED,
                player,
                null
        );
    }

    private static void recordExpired(ServerWorld world, @Nullable UUID ownerUuid) {
        PlayerEntity ownerPlayer = ownerUuid == null ? null : world.getPlayerByUuid(ownerUuid);
        ServerPlayerEntity owner = ownerPlayer instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
        NbtCompound extra = null;
        if (owner == null && ownerUuid != null) {
            extra = new NbtCompound();
            extra.putUuid("actor", ownerUuid);
        }
        GameRecordManager.recordGlobalEvent(
                world,
                SparkStrengthReplayFormatters.CAPTURE_DEVICE_EXPIRED,
                owner,
                extra
        );
    }
}
