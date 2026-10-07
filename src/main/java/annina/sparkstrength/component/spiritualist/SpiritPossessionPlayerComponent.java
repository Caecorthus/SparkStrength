package annina.sparkstrength.component.spiritualist;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.spiritualist.SpiritPossessionRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * The Spiritualist's Wraith-possession cooldown, separate from NoellesRoles' projection cooldown. Server-authoritative;
 * synced to the owner only for the HUD and the client-side send gate. It does not count down during a possession
 * (the service sets it when the possession ends).
 * 灵界行者附身冤魂的冷却，与 NoellesRoles 的灵魂出窍冷却相互独立。服务端权威；只同步给本人，供 HUD 与客户端发包前置判断使用。
 * 附身期间不会出现冷却（附身结束时由服务层设置）。
 */
public final class SpiritPossessionPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<SpiritPossessionPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("spirit_possession"),
            SpiritPossessionPlayerComponent.class
    );

    private final PlayerEntity player;
    private int cooldownTicks;

    public SpiritPossessionPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /** Exact write (possession end, round start, SparkFactionAPI forced cooldowns). / 精确写入（附身结束、开局、SparkFactionAPI 强制冷却）。 */
    public void setCooldownTicks(int ticks) {
        int next = Math.max(0, ticks);
        if (next == cooldownTicks) {
            return;
        }
        cooldownTicks = next;
        sync();
    }

    public void reset() {
        setCooldownTicks(0);
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (cooldownTicks <= 0) {
            return;
        }
        if (!SpiritPossessionRules.isSpiritualist(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            reset();
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
        cooldownTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (cooldownTicks > 0) {
            tag.putInt("CooldownTicks", cooldownTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cooldownTicks = tag.contains("CooldownTicks", NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt("CooldownTicks")) : 0;
    }
}
