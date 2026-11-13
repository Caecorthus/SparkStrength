package annina.sparkstrength.client.role.bomber;

import annina.sparkstrength.network.bomber.TimedBombMarkersPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client copy of the planted timed-bomb trap positions this trap viewer was sent ({@link TimedBombMarkersPayload}). The
 * warning-smoke mixins read it instead of the block entity, whose client NBT no longer carries the owner. It belongs to
 * one client world: a marker never matches in another world, and the first update for a new world starts from empty.
 * Client thread only; cleared on JOIN and DISCONNECT ({@code SparkStrengthClient#resetSessionState}).
 * 本陷阱观察者收到的已埋设定时炸弹陷阱位置的客户端副本（TimedBombMarkersPayload）。警示烟雾 mixin 读取它，而不再读取方块实体
 * （其客户端 NBT 已不携带埋设者）。它只属于一个客户端世界：标记不会在其他世界命中，新世界的第一次更新从空开始。
 * 仅在客户端线程使用；JOIN 与 DISCONNECT 时清空（SparkStrengthClient#resetSessionState）。
 */
public final class TimedBombMarkerClientStore {
    /** Hard cap so a server cannot grow the store without bound. / 硬上限，防止服务端无限撑大存储。 */
    static final int MAX_MARKERS = 4_096;
    private static final Set<Long> MARKERS = new HashSet<>();
    // Identity of the client world the markers belong to; weak, so the title screen does not keep the world alive.
    // 标记所属客户端世界的身份；使用弱引用，避免在标题界面保留该世界。
    private static WeakReference<Object> markerWorld = new WeakReference<>(null);
    private static boolean registered;

    private TimedBombMarkerClientStore() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayNetworking.registerGlobalReceiver(TimedBombMarkersPayload.ID,
                (payload, context) -> context.client().execute(() -> accept(context.client().world, payload)));
    }

    public static void accept(@Nullable World clientWorld, TimedBombMarkersPayload payload) {
        acceptFor(clientWorld, payload.reset(), payload.added(), payload.removed());
    }

    /** Whether a planted trap was sent for {@code pos} in this client world. / 本客户端世界中 pos 处是否收到过已埋设的陷阱。 */
    public static boolean contains(@Nullable World clientWorld, BlockPos pos) {
        return containsFor(clientWorld, pos);
    }

    /** World-identity form of {@link #accept}; the world is only compared, never read. / 按世界身份处理；只比较世界引用，不读取其内容。 */
    static void acceptFor(@Nullable Object clientWorld, boolean reset, List<BlockPos> added, List<BlockPos> removed) {
        if (clientWorld == null) {
            clear();
            return;
        }
        if (markerWorld.get() != clientWorld) {
            MARKERS.clear();
            markerWorld = new WeakReference<>(clientWorld);
        }
        if (reset) {
            MARKERS.clear();
        }
        for (BlockPos pos : removed) {
            MARKERS.remove(pos.asLong());
        }
        for (BlockPos pos : added) {
            if (MARKERS.size() >= MAX_MARKERS) {
                break;
            }
            MARKERS.add(pos.asLong());
        }
    }

    static boolean containsFor(@Nullable Object clientWorld, BlockPos pos) {
        return !MARKERS.isEmpty() && clientWorld != null && markerWorld.get() == clientWorld
                && MARKERS.contains(pos.asLong());
    }

    public static void clear() {
        MARKERS.clear();
        markerWorld = new WeakReference<>(null);
    }
}
