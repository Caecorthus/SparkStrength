package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletLayout.Rect;

/**
 * One tablet section: its widgets, scrolling, header chip, rail badge and body. {@link TabletScreen} creates every page
 * once and keeps the frame, rail, header title and channel switcher; init() re-runs on every refresh and resize, so a
 * page only rebuilds its own widgets there. Everything is painted inside the screen's single frame batch.
 * 平板的一个分区：控件、滚动、页眉胶囊、侧栏徽标与主体。TabletScreen 只创建一次全部页面，自己负责外框、侧栏、页眉标题与
 * 频道切换浮层；每次刷新与调整窗口都会重跑 init()，因此页面只在其中重建自己的控件。所有内容都在界面唯一的整帧批次内绘制。
 */
interface TabletPage {
    /**
     * Every page, open or not, at the start of each init(): clamp this page's scroll to the snapshot and remember what
     * its widgets are built from.
     * 每次 init() 开始时对所有页面（无论是否打开）调用：按快照限制滚动范围，并记下控件所依据的数据。
     */
    default void prepare(TabletSnapshot snapshot) {
    }

    /**
     * Every page, open or not, when a snapshot arrives; {@code laidOut} is false before the first init().
     * 收到快照时对所有页面（无论是否打开）调用；首次 init() 之前 laidOut 为 false。
     */
    default void onSnapshot(TabletSnapshot snapshot, boolean laidOut) {
    }

    /**
     * Open page only: the snapshot changed something this page's widgets show, so the screen must rebuild.
     * 仅打开的页面：快照改变了本页控件显示的内容，界面须重建。
     */
    default boolean needsRebuild(TabletSnapshot snapshot) {
        return false;
    }

    /** Every page, each client tick; {@code open} when it is the open page. 每个客户端 tick 对所有页面调用。 */
    default void tick(boolean open) {
    }

    /** Open page only: adds this page's widgets (keyed for focus restore where they can take focus). 仅打开的页面：添加本页控件。 */
    default void init(TabletSnapshot snapshot) {
    }

    /** Open page only, logical coordinates; true when the wheel was used here. 仅打开的页面，逻辑坐标；滚轮在此被使用时返回 true。 */
    default boolean mouseScrolled(TabletSnapshot snapshot, double x, double y, double verticalAmount) {
        return false;
    }

    /**
     * Open page only: the right-aligned header chip; returns its left edge ({@code right} when none).
     * 仅打开的页面：右对齐的页眉胶囊；返回其左边缘（无胶囊时为 right）。
     */
    int paintHeaderChip(TabletSnapshot snapshot, int right, int centerY, int maxWidth, long now);

    /**
     * Attention badge on this page's rail tab while another page is open, drawn after the widgets.
     * 其它页面打开时本页侧栏标签上的提醒徽标，在控件之后绘制。
     */
    default void paintBadge(TabletSnapshot snapshot, Rect icon, long now) {
    }

    /** Open page only: the body under the header, before the widgets paint. 仅打开的页面：页眉下方的主体，在控件之前绘制。 */
    void paintBody(TabletSnapshot snapshot, int hoverX, int hoverY, long now);
}
