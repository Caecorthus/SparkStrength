package annina.sparkstrength.component.taotie;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.taotie.TaotieHeadRules;
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
 * The Taotie's head-launch cooldown, separate from NoellesRoles' swallow cooldown. Server-authoritative; synced to the
 * owner only for the HUD and the client-side send gate.
 * 饕餮发射头颅的冷却，与 NoellesRoles 的吞噬冷却相互独立。服务端权威；只同步给本人，供 HUD 与客户端发包前置判断使用。
 */
public final class TaotieHeadPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<TaotieHeadPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("taotie_head"),
            TaotieHeadPlayerComponent.class
    );

    private final PlayerEntity player;
    private int cooldownTicks;

    public TaotieHeadPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    /** Exact write (fire, SparkFactionAPI forced cooldowns). / 精确写入（发射、SparkFactionAPI 强制冷却）。 */
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
        if (!TaotieHeadRules.isTaotie(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
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
