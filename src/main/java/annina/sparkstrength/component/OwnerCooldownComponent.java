package annina.sparkstrength.component;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Shared body of the per-user skill cooldowns that sync only to their owner (Phantom backpack button, Professor serum
 * feed). Each concrete component keeps its own CCA key, registration and saved id and syncs through that key; the
 * state, ticking, wire format (one VarInt) and NBT ({@code CooldownTicks}) live here once.
 * 只同步给本人的使用者技能冷却（幽灵背包按钮、教授试剂投喂）的共用实现。各具体组件保留自己的 CCA 键、注册与存档 id，
 * 并通过自己的键同步；状态、计时、同步格式（一个 VarInt）与 NBT（{@code CooldownTicks}）只在这里实现一次。
 */
public abstract class OwnerCooldownComponent implements AutoSyncedComponent, ServerTickingComponent {
    protected final PlayerEntity player;
    private int cooldownTicks;

    protected OwnerCooldownComponent(PlayerEntity player) {
        this.player = player;
    }

    /** Syncs through the concrete component's own key. / 通过具体组件自己的键同步。 */
    protected abstract void sync();

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean isOnCooldown() {
        return cooldownTicks > 0;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
        sync();
    }

    public void reset() {
        if (cooldownTicks == 0) {
            return;
        }
        cooldownTicks = 0;
        sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // 冷却只影响本人（幽灵、教授）的背包 UI，不向其它玩家暴露。
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (cooldownTicks <= 0) {
            return;
        }

        cooldownTicks--;
        if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
            sync();
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(cooldownTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownTicks = buf.readVarInt();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("CooldownTicks", cooldownTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cooldownTicks = tag.getInt("CooldownTicks");
    }
}
