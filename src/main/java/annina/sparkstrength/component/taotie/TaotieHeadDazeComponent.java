package annina.sparkstrength.component.taotie;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.game.GameFunctions;
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
 * Taotie head "daze" lock: no items or skills while ticks remain; movement stays free. The server counts down and is
 * the authority; the owner-only sync lets the victim's own client predict the interaction callbacks (they run on both
 * sides), so a dazed client stops sending the packets the server would drop anyway.
 * 饕餮头颅造成的"眩晕"锁：剩余 tick 内无法使用物品与技能，移动不受限。服务端倒计时并作为权威；只同步给本人，
 * 使受害者客户端能预测交互回调（回调在两端都会运行），被眩晕的客户端因此不再发送服务端反正会丢弃的数据包。
 */
public final class TaotieHeadDazeComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<TaotieHeadDazeComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("taotie_head_daze"),
            TaotieHeadDazeComponent.class
    );

    private final PlayerEntity player;
    private int dazeTicks;

    public TaotieHeadDazeComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getDazeTicks() {
        return dazeTicks;
    }

    /** Never shortens a running daze. / 不缩短正在进行的眩晕。 */
    public void apply(int ticks) {
        if (ticks <= dazeTicks) {
            return;
        }
        dazeTicks = ticks;
        sync();
    }

    public void clear() {
        if (dazeTicks == 0) {
            return;
        }
        dazeTicks = 0;
        sync();
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
        if (dazeTicks <= 0) {
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator() || player.isCreative()) {
            clear();
            return;
        }
        dazeTicks--;
        if (dazeTicks == 0 || dazeTicks % 20 == 0) {
            sync();
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(dazeTicks);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        dazeTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (dazeTicks > 0) {
            tag.putInt("DazeTicks", dazeTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        dazeTicks = tag.contains("DazeTicks", NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt("DazeTicks")) : 0;
    }
}
