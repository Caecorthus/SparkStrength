package annina.sparkstrength.role.jester;

import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapEnhancementsWorldComponent;
import dev.doctor4t.wathe.config.datapack.RoomConfig;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server side of the Jester Moment tweaks: voice cut-off for the Jester and the shooter's random room teleport.
 * The kill-driven grayscale is client-only and reads NoellesRoles' synced kill count directly.
 * 小丑时刻调整的服务端部分：切断小丑语音、把开枪者随机传送进房间。按击杀累积的灰度只在客户端，
 * 直接读取 NoellesRoles 同步的击杀数。
 */
public final class JesterMomentService {
    private JesterMomentService() {
    }

    /**
     * Voice thread: whether this player is a Jester whose moment has been triggered, so they can neither speak nor hear.
     * The component flags are checked first, so ordinary players never touch the role map off-thread.
     * 语音线程：该玩家是否为已触发小丑时刻的小丑（既不能说话也听不到）。先查组件标记，普通玩家不会在主线程外读取角色表。
     */
    public static boolean isVoiceBlocked(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        JesterPlayerComponent jester = JesterPlayerComponent.KEY.get(player);
        boolean inPsychoMode = jester.inPsychoMode;
        boolean transitioning = jester.isTransitioning();
        if (!inPsychoMode && !transitioning) {
            return false;
        }
        boolean isJester = GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.JESTER);
        return JesterMomentRules.isVoiceBlocked(isJester, inPsychoMode, transitioning);
    }

    /**
     * Server: the Jester Moment has just begun. The innocent whose shot triggered it is thrown into a random room
     * (away from the Jester when possible); on a map without rooms they stay where they are.
     * 服务端：小丑时刻刚刚开始。触发它的那名开枪者被随机传送进一个房间（尽量远离小丑）；地图没有房间则原地不动。
     */
    public static void onMomentStarted(ServerPlayerEntity jester, @Nullable UUID shooterUuid) {
        if (shooterUuid == null) {
            return;
        }
        ServerWorld world = jester.getServerWorld();
        ServerPlayerEntity shooter = world.getServer().getPlayerManager().getPlayer(shooterUuid);
        if (shooter == null || shooter == jester || shooter.getServerWorld() != world
                || !GameFunctions.isPlayerPlayingAndAlive(shooter)
                || !GameFunctions.isPlayerAliveAndSurvival(shooter)
                // A swallowed shooter is inside a Taotie; pulling them out would break the stomach.
                // 被吞的开枪者在饕餮腹中，把他拉出来会破坏吞噬状态。
                || SwallowedPlayerComponent.isPlayerSwallowed(shooter)) {
            return;
        }

        MapEnhancementsWorldComponent enhancements = MapEnhancementsWorldComponent.KEY.get(world);
        List<JesterMomentRules.RoomSpot> spots = new ArrayList<>();
        List<RoomConfig.SpawnPoint> points = new ArrayList<>();
        int roomCount = enhancements.getRoomCount();
        for (int room = 1; room <= roomCount; room++) {
            int roomNumber = room;
            enhancements.getRoomConfig(roomNumber).ifPresent(config -> {
                for (RoomConfig.SpawnPoint point : config.spawnPoints()) {
                    spots.add(new JesterMomentRules.RoomSpot(roomNumber, point.x(), point.y(), point.z()));
                    points.add(point);
                }
            });
        }
        int index = JesterMomentRules.pickShooterSpot(spots, jester.getX(), jester.getY(), jester.getZ(),
                bound -> world.getRandom().nextInt(bound));
        if (index < 0) {
            return;
        }

        int room = spots.get(index).room();
        RoomConfig.SpawnPoint point = points.get(index);
        String roomName = enhancements.getRoomConfig(room)
                .map(config -> config.getName(room))
                .orElse("Room " + room);
        shooter.teleport(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        shooter.fallDistance = 0.0F;
        shooter.sendMessage(Text.translatable("message.sparkstrength.jester_moment.shooter_teleported", roomName)
                .formatted(Formatting.LIGHT_PURPLE), true);

        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", jester.getUuid());
        extra.putString("room", roomName);
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.JESTER_SHOOTER_TELEPORTED, shooter, extra);
    }
}
