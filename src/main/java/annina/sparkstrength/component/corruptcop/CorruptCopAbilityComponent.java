package annina.sparkstrength.component.corruptcop;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.record.AchievementRecords;
import annina.sparkstrength.role.corruptcop.CorruptCopTaskGateRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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

/**
 * Runtime state for the Corrupt Cop ability. The toggle is synced to every tracking client; task progress toward the
 * unlock reaches only the owner, and changes to it sync to the owner alone, so no other client can tell whose
 * component moves on task completion.
 * 黑警主动技能的运行时状态。开关同步给所有追踪该玩家的客户端；解锁任务进度只发给本人，且进度变化只单独同步给本人，
 * 其他客户端无法从“完成任务时谁的组件更新了”推断出黑警。
 */
public final class CorruptCopAbilityComponent implements AutoSyncedComponent {
    public static final ComponentKey<CorruptCopAbilityComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("corrupt_cop_ability"),
            CorruptCopAbilityComponent.class
    );

    private final PlayerEntity player;
    private boolean active;
    private int completedTasks;

    public CorruptCopAbilityComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        sync();
        // Achievement record: every real flip of the server copy, reset included (the client copy changes only through
        // applySyncPacket, never here).
        // 成就记录：服务端副本的每次真实翻转（含重置）；客户端副本只经 applySyncPacket 更新，不会走到这里。
        if (player instanceof ServerPlayerEntity serverPlayer) {
            AchievementRecords.corruptCopShowOff(serverPlayer, active);
        }
    }

    public int completedTasks() {
        return completedTasks;
    }

    /**
     * Required tasks from the synced round roster, so server and owner client agree without syncing it.
     * 所需任务数由已同步的本局名单计算，服务端与本人客户端无需额外同步即可一致。
     */
    public int requiredTasks() {
        return CorruptCopTaskGateRules.requiredTasks(
                GameWorldComponent.KEY.get(player.getWorld()).getAllPlayers().size()
        );
    }

    public boolean isUnlocked() {
        return CorruptCopTaskGateRules.isUnlocked(completedTasks, requiredTasks());
    }

    /** Server only. / 仅服务端调用。 */
    public int recordCompletedTask() {
        completedTasks++;
        syncToOwner();
        return completedTasks;
    }

    public void reset() {
        if (completedTasks != 0) {
            completedTasks = 0;
            syncToOwner();
        }
        setActive(false);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Nearby clients need the toggle to conceal the cop from their own instinct; HUD and music still read
        // only the local player's copy.
        // 附近客户端需要该开关来在自己的本能中屏蔽黑警；HUD 与音乐仍只读取本人的副本。
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(active);
        boolean owner = recipient == player;
        buf.writeBoolean(owner);
        if (owner) {
            buf.writeVarInt(completedTasks);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        active = buf.readBoolean();
        if (buf.readBoolean()) {
            completedTasks = buf.readVarInt();
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only by contract: reconnecting or respawning always starts inactive.
        // 按契约仅保留运行态：重连或重生后始终从关闭状态开始。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        active = false;
        completedTasks = 0;
    }

    private void sync() {
        KEY.sync(player);
    }

    private void syncToOwner() {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            KEY.syncWith(serverPlayer, (ComponentProvider) serverPlayer);
        }
    }
}
