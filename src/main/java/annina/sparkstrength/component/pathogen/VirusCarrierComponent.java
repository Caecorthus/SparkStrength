package annina.sparkstrength.component.pathogen;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.pathogen.PathogenRules;
import annina.sparkstrength.role.pathogen.PathogenVisibility;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.UUID;

/**
 * "Has the virus" (拥有病毒) on one player: the carrier flag, the Pathogen whose Virus started it (used as NoellesRoles'
 * {@code infectedBy} for the people it infects) and the 30 s spread countdown. Only the flag is synced, and only to the
 * players who may see carriers: Pathogens, Toxicologists (Black Raven acting overlay included) and a Coroner disguised
 * as a Toxicologist ({@link PathogenVisibility#seesCarriers}). The carrier itself is never told. No game logic here.
 * 单名玩家的“拥有病毒”状态：带毒标记、使用病毒的病原体（作为其传染对象在 NoellesRoles 中的 {@code infectedBy}）以及
 * 30 秒传播倒计时。只同步标记，且只同步给可以看到带毒者的玩家：病原体、毒理学家（含黑羽鸦扮演覆盖层）以及伪装成毒理学家
 * 的验尸官（{@link PathogenVisibility#seesCarriers}）。带毒者本人不会得知。这里不含游戏逻辑。
 */
public final class VirusCarrierComponent implements AutoSyncedComponent {
    public static final ComponentKey<VirusCarrierComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("virus_carrier"),
            VirusCarrierComponent.class
    );

    private final PlayerEntity player;
    private boolean carrier;
    private @Nullable UUID source;
    private int spreadCountdown;

    public VirusCarrierComponent(PlayerEntity player) {
        this.player = player;
    }

    public static boolean isCarrier(@Nullable PlayerEntity player) {
        return player != null && KEY.get(player).isCarrier();
    }

    public boolean isCarrier() {
        return carrier;
    }

    public @Nullable UUID getSource() {
        return source;
    }

    public int getSpreadCountdown() {
        return spreadCountdown;
    }

    /** Server only: starts carrying with a full 30 s countdown. / 仅服务端：开始带毒，倒计时为完整 30 秒。 */
    public void startCarrying(@Nullable UUID pathogen) {
        boolean changed = !carrier;
        carrier = true;
        source = pathogen;
        spreadCountdown = PathogenRules.SPREAD_INTERVAL_TICKS;
        if (changed) {
            sync();
        }
    }

    /** Server only. / 仅服务端。 */
    public void setSpreadCountdown(int ticks) {
        spreadCountdown = ticks;
    }

    /** Server only. / 仅服务端。 */
    public void clear() {
        boolean changed = carrier;
        carrier = false;
        source = null;
        spreadCountdown = 0;
        if (changed) {
            sync();
        }
    }

    public void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return PathogenVisibility.seesCarriers(recipient);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(carrier);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        carrier = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (!carrier) {
            return;
        }
        tag.putBoolean("Carrier", true);
        if (source != null) {
            tag.putUuid("Source", source);
        }
        tag.putInt("SpreadCountdown", spreadCountdown);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        carrier = tag.getBoolean("Carrier");
        source = carrier && tag.containsUuid("Source") ? tag.getUuid("Source") : null;
        spreadCountdown = carrier ? Math.max(1, tag.getInt("SpreadCountdown")) : 0;
    }
}
