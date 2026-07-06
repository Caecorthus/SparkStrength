package annina.sparkstrength.client.ui.common;

/**
 * 客户端分页状态。
 *
 * <p>背包每次重新打开都会重新创建 Screen 实例；把页码放在一个小的静态状态类里，
 * 可以让玩家关闭再打开背包时保持在刚才那一页。</p>
 */
public final class PlayerSelectionPageState {
    private static int noisemakerPage;
    private static int professorPage;

    private PlayerSelectionPageState() {
    }

    public static int getNoisemakerPage() {
        return noisemakerPage;
    }

    public static void setNoisemakerPage(int page) {
        noisemakerPage = Math.max(0, page);
    }

    public static int getProfessorPage() {
        return professorPage;
    }

    public static void setProfessorPage(int page) {
        professorPage = Math.max(0, page);
    }
}
