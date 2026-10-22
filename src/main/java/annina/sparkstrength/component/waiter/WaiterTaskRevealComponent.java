package annina.sparkstrength.component.waiter;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.waiter.WaiterTaskRevealService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 记录“玩家刚完成任务后，服务员可以看到该玩家”的倒计时。
 *
 * <p>组件挂在完成任务的目标玩家身上，而不是挂在服务员身上。这样每个目标只需要
 * 同步一个简单的倒计时，客户端高亮事件再判断当前观看者是否是服务员或服务员伪装。</p>
 *
 * <p>Synced only when a reveal starts or ends (the client counts down by itself), and only to players who can see
 * reveals ({@link WaiterTaskRevealService#canSeeReveals}). Someone who becomes such a viewer mid-reveal is sent every
 * player's current state at once.
 * 只在透视开始或结束时同步（客户端自行倒计时），且只发给能看到透视的玩家。中途获得资格的观察者会立即收到所有玩家的当前状态。</p>
 */
public final class WaiterTaskRevealComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<WaiterTaskRevealComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("waiter_task_reveal"), WaiterTaskRevealComponent.class
    );

    /** 任务完成后可被服务员透视的持续时间：30 秒。 */
    public static final int REVEAL_DURATION_TICKS = 30 * 20;

    private final PlayerEntity player;
    private int revealTicks;
    /** Server only: this player could see reveals last tick. / 仅服务端：上一 tick 本玩家能否看到透视。 */
    private boolean revealViewer;

    public WaiterTaskRevealComponent(PlayerEntity player) {
        this.player = player;
    }

    /** 开始或刷新透视时间；连续完成任务不会叠加，只会刷新到至少 30 秒。 */
    public void startReveal() {
        this.revealTicks = Math.max(this.revealTicks, REVEAL_DURATION_TICKS);
        this.sync();
    }

    public boolean isRevealed() {
        return this.revealTicks > 0;
    }

    public void reset() {
        if (this.revealTicks == 0) {
            return;
        }
        this.revealTicks = 0;
        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Only clients that draw the highlight; anyone else would learn who just did a task.
        // 只发给会显示高亮的客户端；其他人收到就能知道谁刚完成了任务。
        return WaiterTaskRevealService.canSeeReveals(recipient);
    }

    @Override
    public void serverTick() {
        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        this.tickViewer(serverPlayer);
        if (this.revealTicks <= 0) {
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(serverPlayer)) {
            this.reset();
            return;
        }

        this.revealTicks--;
        if (this.revealTicks == 0) {
            this.sync();
        }
    }

    /**
     * This player as a viewer: on becoming one (role, disguise, revival, new round) they missed every sync sent while
     * they were not, so each other player's current state is sent to them now, zeros included.
     * 把本玩家当作观察者：刚获得资格（职业、伪装、复活、新一局）时，之前的同步都没有发给他，因此立即补发其他每名玩家的
     * 当前状态（包括为 0 的）。
     */
    private void tickViewer(ServerPlayerEntity viewer) {
        boolean canSee = WaiterTaskRevealService.canSeeReveals(viewer);
        if (canSee && !this.revealViewer) {
            for (ServerPlayerEntity target : viewer.getServerWorld().getPlayers()) {
                if (target != viewer) {
                    KEY.syncWith(viewer, (ComponentProvider) target);
                }
            }
        }
        this.revealViewer = canSee;
    }

    @Override
    public void clientTick() {
        // 客户端本地递减，让透视结束不必等待下一次服务端同步。
        if (this.revealTicks > 0) {
            this.revealTicks--;
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(this.revealTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        this.revealTicks = buf.readVarInt();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("RevealTicks", this.revealTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        this.revealTicks = Math.max(0, tag.getInt("RevealTicks"));
    }
}
