package annina.sparkstrength.component.corruptcop;

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
 * Runtime state for the Corrupt Cop ability, synced to every tracking client.
 * 黑警主动技能的运行时状态，同步给所有追踪该玩家的客户端。
 */
public final class CorruptCopAbilityComponent implements AutoSyncedComponent {
    public static final ComponentKey<CorruptCopAbilityComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("corrupt_cop_ability"),
            CorruptCopAbilityComponent.class
    );

    private final PlayerEntity player;
    private boolean active;

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
    }

    public void reset() {
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
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        active = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only by contract: reconnecting or respawning always starts inactive.
        // 按契约仅保留运行态：重连或重生后始终从关闭状态开始。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        active = false;
    }

    private void sync() {
        KEY.sync(player);
    }
}
