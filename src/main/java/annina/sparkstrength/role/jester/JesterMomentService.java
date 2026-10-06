package annina.sparkstrength.role.jester;

import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the Jester Moment tweaks. An innocent's shot freezes everyone (no movement, locked view, no items or
 * skills: the Engineer stun lock) through the Jester's fake death. When the Jester revives and starts transforming,
 * everyone, the Jester included, is shuffled onto someone else's spot and set free to run before the moment begins;
 * NoellesRoles' everyone-looks-like-the-Jester view starts at the same time (synced below and in the client mixin).
 * The Jester is cut off from voice throughout. The kill-driven grayscale is client-only.
 * 小丑时刻调整的服务端部分。好人一枪打中小丑后，全员立即定身（不能移动、视角锁定、不能用物品和技能，复用工程师定身），
 * 持续整个假死阶段。小丑复活开始转变时，所有人（含小丑）被打乱到别人的位置并解除定身，可以在时刻开始前逃跑；
 * NoellesRoles 的“所有人都像小丑”视角同时开启（见下方同步与客户端 mixin）。小丑全程听不到也说不出。按击杀的灰度只在客户端。
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
     * Server: an innocent's shot just put the Jester into its fake death. Everyone else freezes until it revives.
     * 服务端：好人的一枪刚让小丑进入假死。其余所有人定身，直到小丑复活。
     */
    public static void onFakeDeathStarted(ServerPlayerEntity jester, int fakeDeathTicks) {
        int ticks = JesterMomentRules.freezeTicks(fakeDeathTicks);
        for (ServerPlayerEntity player : participants(jester.getServerWorld())) {
            if (player != jester) {
                EngineerStunnedPlayerComponent.KEY.get(player).freeze(ticks);
            }
        }
    }

    /**
     * Server: the Jester has revived at its death spot and is about to enter stasis (the transformation). Everyone,
     * the Jester included, moves to someone else's frozen spot and is released, so the others can run before the moment
     * starts. Runs before NoellesRoles records the stasis point, so the Jester's stasis lock holds its new spot.
     * 服务端：小丑已在死亡处复活，即将进入禁锢（转变）。所有人（含小丑）移到别人被定住的位置并解除定身，
     * 其他人可以在时刻开始前逃跑。在 NoellesRoles 记录禁锢点之前执行，因此小丑的禁锢会锁在新位置。
     */
    public static void onTransformationStarted(ServerPlayerEntity jester) {
        ServerWorld world = jester.getServerWorld();
        List<ServerPlayerEntity> players = participants(world);
        if (!players.contains(jester)) {
            players.add(jester);
        }
        List<Spot> spots = new ArrayList<>(players.size());
        for (ServerPlayerEntity player : players) {
            spots.add(new Spot(player.getPos(), player.getYaw(), player.getPitch()));
        }
        int[] targets = JesterMomentRules.shuffleSpots(players.size(), bound -> world.getRandom().nextInt(bound));
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            Spot spot = spots.get(targets[i]);
            // Release before moving: a frozen player is pulled back to their lock point every tick.
            // 先解除再传送：定身中的玩家每 tick 都会被拉回锁定点。
            EngineerStunnedPlayerComponent.KEY.get(player).clear();
            player.teleport(world, spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), spot.pitch());
            player.fallDistance = 0.0F;
            if (player != jester) {
                player.sendMessage(Text.translatable("message.sparkstrength.jester_moment.shuffled")
                        .formatted(Formatting.LIGHT_PURPLE), true);
            }
        }

        NbtCompound extra = new NbtCompound();
        extra.putInt("players", players.size());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.JESTER_POSITIONS_SHUFFLED, jester, extra);
    }

    /**
     * Living, in-round survival players; a player swallowed by a Taotie is inside it and stays there.
     * 局内存活的生存模式玩家；被饕餮吞下的玩家在其腹中，不参与。
     */
    private static List<ServerPlayerEntity> participants(ServerWorld world) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (GameFunctions.isPlayerPlayingAndAlive(player)
                    && GameFunctions.isPlayerAliveAndSurvival(player)
                    && !SwallowedPlayerComponent.isPlayerSwallowed(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private record Spot(Vec3d pos, float yaw, float pitch) {
    }
}
