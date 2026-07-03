package annina.sparkstrength.client.ui.common;

/**
 * 玩家头像分页布局工具。
 *
 * <p>大嗓门头像栏和翻页按钮的所有坐标都从这里算，后续要调整每页数量、
 * 间距或整体位置时只改这一个类即可。</p>
 */
public final class PlayerPageLayout {
    /** 每页最多显示 10 个玩家头像。 */
    public static final int PLAYERS_PER_PAGE = 10;
    /** 头像按钮之间的固定间距，沿用 NoellesRoles 选人界面的视觉节奏。 */
    public static final int SLOT_APART = 36;
    /** 保留旧界面的半格视觉修正，让头像排布更贴合背包背景。 */
    public static final int SLOT_X_OFFSET = 9;

    private PlayerPageLayout() {
    }

    public static int getPlayerRowY(int screenHeight) {
        return (screenHeight - 32) / 2 + 80;
    }

    public static int getCenteredGroupStartX(int screenWidth, int visiblePlayerCount, boolean showPrevious, boolean showNext) {
        int buttonCount = (showPrevious ? 1 : 0) + (showNext ? 1 : 0);
        int totalSlots = visiblePlayerCount + buttonCount;
        return screenWidth / 2 - totalSlots * SLOT_APART / 2 + SLOT_X_OFFSET;
    }

    public static int getTotalPageCount(int totalPlayers) {
        return Math.max(1, (totalPlayers + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
    }
}
