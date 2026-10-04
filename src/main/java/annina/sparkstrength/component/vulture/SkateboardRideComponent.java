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

/**
 * The Vulture's skateboard ride, synced to every tracking client. The server's VultureSkateboardService owns it; the
 * client only reads it (board under the feet, frozen legs, rolling sound, HUD). Speed itself comes from a temporary
 * movement-speed modifier, which vanilla syncs on its own.
 * 秃鹫的滑板滑行状态，同步给所有追踪该玩家的客户端。由服务端 VultureSkateboardService 维护；客户端只读取
 * （脚下滑板、固定腿部、滚轮声、HUD）。速度本身来自临时移速修饰符，由原版自行同步。
 */
public final class SkateboardRideComponent implements AutoSyncedComponent {
    public static final ComponentKey<SkateboardRideComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("skateboard_ride"),
            SkateboardRideComponent.class
    );

    private final PlayerEntity player;
    /** World time the ride ends at; 0 when not riding. / 滑行结束时的世界时间；未滑行时为 0。 */
    private long rideEndTick;

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
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        rideEndTick = buf.readVarLong();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only by contract: the speed modifier is temporary too, so a reload never resumes a ride.
        // 按契约仅保留运行态：移速修饰符同样是临时的，重载后不会恢复滑行。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        rideEndTick = 0L;
    }
}
