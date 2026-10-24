package annina.sparkstrength.event;

import annina.sparkstrength.SparkStrength;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;

/**
 * Takes creative mode away from a player who joins a dedicated server in it without operator rights.
 * 玩家无管理员权限却以创造模式进入专用服务器时，收回其创造模式。
 *
 * <p>Wathe treats creative players as staff: the lobby reset, the spectator box and the switch to spectator on death
 * all skip them, so a creative non-op roams the train freely and sees everything. Vanilla gives the server's default
 * game mode to anyone without a saved one (a new player, a new offline name after reinstalling, lost player data),
 * and the default can be creative on a map world. Reported 2026-10-24 after a player reinstalled the DLC. The player
 * is set to adventure, Wathe's own lobby mode; in a running round Wathe then makes a non-participant a spectator as
 * usual. Fabric's JOIN fires after vanilla has applied the saved or default mode. An operator who gives a non-op
 * creative later is not overridden until that player reconnects.</p>
 * <p>Wathe 把创造模式玩家当作管理员：大厅重置、旁观者范围限制与死亡转旁观都会跳过他们，因此非管理员的创造模式玩家可以
 * 在列车上随意行动、看到一切。原版会把服务器默认游戏模式给没有存档模式的玩家（新玩家、重装后换了离线名、玩家数据丢失），
 * 而地图存档的默认模式可能就是创造。2026-10-24 有玩家重装 DLC 后遇到此问题。玩家会被设为冒险模式（Wathe 自己的大厅模式）；
 * 对局进行中，Wathe 会照常把非参与者转为旁观。Fabric 的 JOIN 在原版应用存档或默认模式之后触发。管理员之后给非管理员的
 * 创造模式不受影响，直到该玩家重新连接。</p>
 */
public final class CreativeJoinGuard {
    private CreativeJoinGuard() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.player));
    }

    private static void onJoin(MinecraftServer server, ServerPlayerEntity player) {
        boolean creative = player.interactionManager.getGameMode() == GameMode.CREATIVE;
        boolean canSwitch = player.hasPermissionLevel(CreativeJoinRules.GAME_MODE_PERMISSION_LEVEL);
        if (!CreativeJoinRules.shouldLeaveCreative(server.isDedicated(), creative, canSwitch)) {
            return;
        }
        SparkStrength.LOGGER.warn("{} joined in creative without operator rights; switching to adventure "
                        + "(server default game mode: {}).",
                player.getGameProfile().getName(), server.getDefaultGameMode().getName());
        player.changeGameMode(GameMode.ADVENTURE);
    }
}
