package annina.sparkstrength.event;

/**
 * Who may keep creative mode when joining. Pure, so the local tests run it without Minecraft.
 * 加入服务器时谁可以保留创造模式。纯逻辑，本地测试无需 Minecraft 即可运行。
 */
public final class CreativeJoinRules {
    /** Vanilla /gamemode's level (GameModeCommand.REQUIRED_PERMISSION_LEVEL). / 原版 /gamemode 所需的权限等级。 */
    public static final int GAME_MODE_PERMISSION_LEVEL = 2;

    private CreativeJoinRules() {
    }

    /**
     * True for a player who joins a dedicated server in creative but could not switch to it with /gamemode. A
     * singleplayer or LAN world keeps the host's and guests' creative, since it was chosen when the world was made.
     * 在专用服务器上以创造模式加入、却无权用 /gamemode 切换过去的玩家返回 true。单人或局域网世界保留房主与访客的
     * 创造模式，因为那是建世界时选好的。
     */
    public static boolean shouldLeaveCreative(boolean dedicatedServer, boolean creative, boolean canSwitchGameMode) {
        return dedicatedServer && creative && !canSwitchGameMode;
    }
}
