package annina.sparkstrength.client.sabotage;

import annina.sparkstrength.network.sabotage.ChannelProgressS2CPacket;
import annina.sparkstrength.network.sabotage.SabotageStatusS2CPacket;
import annina.sparkstrength.sabotage.OutageMode;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client-thread view of the sabotage system: the server status, this player's prying/hacking bar and the junction
 * boxes / circuit breakers found in loaded chunks.
 * 客户端线程上的破坏系统视图：服务端状态、本人的撬开/入侵进度条，以及在已加载区块中找到的接线箱与电闸。
 */
public final class SabotageClientState {
    /** A junction box or breaker seen in a loaded chunk. / 在已加载区块中看到的接线箱或电闸。 */
    public record Fixture(BlockPos pos, BlockState state) {
    }

    /** The local prying/hacking bar. / 本地的撬开/入侵进度条。 */
    public static final class Channel {
        public final int id;
        public final int kind;
        public final BlockPos pos;
        public final int total;
        public final int rate;
        public int progress;
        public long receivedTick;
        public boolean cancelSent;
        public int finishedFlashTicks;

        Channel(ChannelProgressS2CPacket packet, long now) {
            this.id = packet.channelId();
            this.kind = packet.kind();
            this.pos = packet.pos();
            this.total = Math.max(1, packet.total());
            this.rate = packet.rate();
            this.progress = packet.progress();
            this.receivedTick = now;
        }

        /** Extrapolated fraction 0-1. / 外推得到的 0-1 进度。 */
        public float fraction(long now, float tickDelta) {
            if (finishedFlashTicks > 0) {
                return 1.0F;
            }
            float done = progress + rate * Math.max(0.0F, (now - receivedTick) + tickDelta);
            return Math.max(0.0F, Math.min(1.0F, done / total));
        }
    }

    private static SabotageStatusS2CPacket status = SabotageStatusS2CPacket.NONE;
    private static @Nullable Channel channel;
    /** The last channel this client cancelled; its late packets are ignored. / 本客户端最近取消的读条；其迟到的包会被忽略。 */
    private static int cancelledChannelId;
    private static List<Fixture> fixtures = List.of();

    private SabotageClientState() {
    }

    public static boolean lightsOut() {
        return status.lightsOut();
    }

    public static OutageMode outageMode() {
        return OutageMode.byWire(status.modeWire());
    }

    public static boolean canSabotage() {
        return status.canSabotage();
    }

    /** World tick the engine sabotage was triggered, 0 when none is on. / 动力破坏触发时的世界 tick，没有时为 0。 */
    public static long engineTriggeredAt() {
        return status.engineTriggeredAt();
    }

    /** Every registered engine unit while an engine sabotage is on, else empty. / 动力破坏进行中时为全部已登记的动力装置，否则为空。 */
    public static List<BlockPos> engineUnits() {
        return status.engineUnits();
    }

    static void setStatus(SabotageStatusS2CPacket next) {
        status = next == null ? SabotageStatusS2CPacket.NONE : next;
    }

    public static @Nullable Channel channel() {
        return channel;
    }

    static void onProgress(ChannelProgressS2CPacket packet, long now) {
        if (packet.channelId() == cancelledChannelId) {
            return;
        }
        if (packet.state() == ChannelProgressS2CPacket.STATE_RUNNING) {
            Channel current = channel;
            if (current != null && current.id == packet.channelId() && current.finishedFlashTicks == 0) {
                current.progress = packet.progress();
                current.receivedTick = now;
                return;
            }
            channel = new Channel(packet, now);
        } else if (packet.state() == ChannelProgressS2CPacket.STATE_FINISHED) {
            Channel finished = new Channel(packet, now);
            finished.finishedFlashTicks = 10;
            channel = finished;
        } else if (channel != null && channel.id == packet.channelId()) {
            channel = null;
        }
    }

    static void clearChannel() {
        channel = null;
    }

    /** Remembers the cancelled channel and drops it locally. / 记住被取消的读条并在本地移除。 */
    static void cancelChannel(Channel cancelled) {
        cancelledChannelId = cancelled.id;
        if (channel == cancelled) {
            channel = null;
        }
    }

    public static List<Fixture> fixtures() {
        return fixtures;
    }

    static void setFixtures(List<Fixture> found) {
        fixtures = found == null ? List.of() : List.copyOf(found);
    }

    static void reset() {
        status = SabotageStatusS2CPacket.NONE;
        channel = null;
        cancelledChannelId = 0;
        fixtures = List.of();
    }
}
