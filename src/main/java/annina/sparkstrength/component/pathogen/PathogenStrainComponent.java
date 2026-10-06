package annina.sparkstrength.component.pathogen;

import annina.sparkstrength.SparkStrength;
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

/**
 * A Pathogen's own per-round state: whether it was converted by a T-Virus (its shop then has no T-Virus) and how many
 * T-Viruses it bought this round (the hard cap behind {@code stock(1)}). Only {@code converted} is synced, and only to
 * the owner, because Wathe builds the shop list on both sides and buys by index.
 * 病原体本人的单局状态：是否由 T病毒转化而来（转化者商店没有 T病毒），以及本局已购买的 T病毒数量（stock(1) 之外的硬上限）。
 * 只同步 {@code converted}，且只同步给本人，因为 Wathe 在两端各自构建商店列表并按下标购买。
 */
public final class PathogenStrainComponent implements AutoSyncedComponent {
    public static final ComponentKey<PathogenStrainComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("pathogen_strain"),
            PathogenStrainComponent.class
    );

    private final PlayerEntity player;
    private boolean converted;
    private int tVirusPurchases;

    public PathogenStrainComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isConverted() {
        return converted;
    }

    public int getTVirusPurchases() {
        return tVirusPurchases;
    }

    /** Server only. / 仅服务端。 */
    public void markConverted() {
        if (!converted) {
            converted = true;
            sync();
        }
    }

    /** Server only. / 仅服务端。 */
    public void recordTVirusPurchase() {
        tVirusPurchases++;
    }

    /** Server only. / 仅服务端。 */
    public void clear() {
        tVirusPurchases = 0;
        if (converted) {
            converted = false;
            sync();
        }
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(converted);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        converted = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (converted) {
            tag.putBoolean("Converted", true);
        }
        if (tVirusPurchases > 0) {
            tag.putInt("TVirusPurchases", tVirusPurchases);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        converted = tag.getBoolean("Converted");
        tVirusPurchases = tag.contains("TVirusPurchases", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("TVirusPurchases"))
                : 0;
    }
}
