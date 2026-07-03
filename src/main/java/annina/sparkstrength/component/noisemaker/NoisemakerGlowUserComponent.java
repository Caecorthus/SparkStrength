package annina.sparkstrength.component.noisemaker;

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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 大嗓门点亮技能的“使用者冷却”。
 *
 * <p>NoellesRolesspark 已经把 {@code noellesroles:noisemaker} 组件用于语音广播，
 * 并且通用能力冷却也被多个职业共用。所以这里用 SparkStrength 自己的组件单独保存
 * 80 秒点亮冷却，避免影响原本的大嗓门广播技能。</p>
 */
public class NoisemakerGlowUserComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<NoisemakerGlowUserComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("noisemaker_glow_user"),
            NoisemakerGlowUserComponent.class
    );

    private final PlayerEntity player;
    private int cooldownTicks;

    public NoisemakerGlowUserComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return this.cooldownTicks;
    }

    public boolean isOnCooldown() {
        return this.cooldownTicks > 0;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.sync();
    }

    public void reset() {
        this.cooldownTicks = 0;
        this.sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // 冷却是大嗓门本人背包 UI 需要显示的信息，其他玩家不需要知道。
        return recipient == this.player;
    }

    @Override
    public void serverTick() {
        if (this.cooldownTicks <= 0) {
            return;
        }

        this.cooldownTicks--;
        if (this.cooldownTicks == 0 || this.cooldownTicks % 20 == 0) {
            this.sync();
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(this.cooldownTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        this.cooldownTicks = buf.readVarInt();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("CooldownTicks", this.cooldownTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        this.cooldownTicks = tag.getInt("CooldownTicks");
    }
}
