package annina.sparkstrength.component.vulture;

import annina.sparkstrength.SparkStrength;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.sync.PlayerSyncPredicate;

/**
 * The Vulture's skateboard ride, synced to every tracking client. The server's VultureSkateboardService owns it; the
 * client only reads it (board under the feet, frozen legs, rolling sound, HUD). Speed itself comes from a temporary
 * movement-speed modifier, which vanilla syncs on its own.
 * 秃鹫的滑板滑行状态，同步给所有追踪该玩家的客户端。由服务端 VultureSkateboardService 维护；客户端只读取
 * （脚下滑板、固定腿部、滚轮声、HUD）。速度本身来自临时移速修饰符，由原版自行同步。
 *
 * <p>It also carries the board's current lock (round-start lock, or ride + cooldown) as a world-time window, sent to
 * the owner only. The owner's HUD line and slot sweep are drawn from that window, so they follow the server's clock;
 * a vanilla item cooldown alone counts down on the client's own clock and drifts whenever the server runs slow.
 * 它还以世界时间区间携带滑板当前的锁（开局锁，或滑行 + 冷却），仅发送给本人。本人的 HUD 行与栏位冷却条都按该区间绘制，
 * 因此跟随服务端时钟；单靠原版物品冷却只会按客户端自己的时钟倒数，服务端一变慢就会跑偏。</p>
 */
public final class SkateboardRideComponent implements AutoSyncedComponent {
    public static final ComponentKey<SkateboardRideComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("skateboard_ride"),
            SkateboardRideComponent.class
    );

    private final PlayerEntity player;
    /** World time the ride ends at; 0 when not riding. / 滑行结束时的世界时间；未滑行时为 0。 */
    private long rideEndTick;
    /** World time the current lock began at; 0 with no lock. / 当前锁开始的世界时间；无锁时为 0。 */
    private long lockStartTick;
    /** World time the current lock ends at; 0 with no lock. / 当前锁结束的世界时间；无锁时为 0。 */
    private long lockEndTick;

    public SkateboardRideComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isRiding() {
        if (rideEndTick == 0L) {
            return false;
        }
        // Client: the server clears and syncs this on the tick it removes the speed, so the flag alone is authoritative;
        // the client's world time drifts between time packets and would end (or flicker) the visuals early.
        // 客户端：服务端在移除速度的同一刻清除并同步该值，因此仅凭该标记即可；客户端世界时间在两次时间包之间会漂移，
        // 用它判断会让视觉效果提前结束或闪烁。
        return player.getWorld().isClient() || player.getWorld().getTime() < rideEndTick;
    }

    /** Ticks left in the ride (0 when not riding); a display estimate on the client. / 剩余滑行刻数（未滑行为 0）；客户端仅作显示估算。 */
    public int remainingTicks() {
        return rideEndTick == 0L ? 0 : (int) Math.max(0L, rideEndTick - player.getWorld().getTime());
    }

    /** True while a ride is recorded, even one whose time has run out but is not yet closed by the server. / 记录中存在滑行（含已到时但服务端尚未收尾的）时为 true。 */
    public boolean hasRide() {
        return rideEndTick != 0L;
    }

    public void start(long endTick) {
        rideEndTick = endTick;
        sync();
    }

    public void clear() {
        if (rideEndTick == 0L) {
            return;
        }
        rideEndTick = 0L;
        sync();
    }

    /** Whether a lock window is recorded (owner's client and server only). / 是否记录了锁区间（仅本人客户端与服务端）。 */
    public boolean hasLock() {
        return lockEndTick != 0L;
    }

    public long lockStartTick() {
        return lockStartTick;
    }

    public long lockEndTick() {
        return lockEndTick;
    }

    /**
     * Server: records the board's lock window, or clears it with {@code endTick <= 0}. Synced to the owner, and only
     * when the window changed, so calling it every tick is free.
     * 服务端：记录滑板的锁区间，{@code endTick <= 0} 时清除。仅在区间变化时同步给本人，因此可以每刻调用。
     */
    public void setLock(long startTick, long endTick) {
        long start = endTick <= 0L ? 0L : startTick;
        long end = Math.max(0L, endTick);
        if (start == lockStartTick && end == lockEndTick) {
            return;
        }
        lockStartTick = start;
        lockEndTick = end;
        KEY.sync(player, this, PlayerSyncPredicate.only(player));
    }

    private void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone who can see the rider draws the board; the ride is as visible as the speed it grants.
        // 能看到骑手的人都会绘制滑板；滑行和它带来的速度一样肉眼可见。
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarLong(rideEndTick);
        // The lock only drives the owner's own HUD and slot; nobody else learns when the board is ready.
        // 锁只用于本人的 HUD 与栏位，其他人不会得知滑板何时可用。
        boolean owner = recipient == player;
        buf.writeVarLong(owner ? lockStartTick : 0L);
        buf.writeVarLong(owner ? lockEndTick : 0L);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        rideEndTick = buf.readVarLong();
        lockStartTick = buf.readVarLong();
        lockEndTick = buf.readVarLong();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only by contract: the speed modifier is temporary too, so a reload never resumes a ride.
        // 按契约仅保留运行态：移速修饰符同样是临时的，重载后不会恢复滑行。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        rideEndTick = 0L;
        lockStartTick = 0L;
        lockEndTick = 0L;
    }
}
