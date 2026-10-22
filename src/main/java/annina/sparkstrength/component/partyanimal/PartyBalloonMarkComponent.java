package annina.sparkstrength.component.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.partyanimal.PartyAnimalRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
 * "Given a Party Balloon" on one player, for the rest of the round. The marked player is never told; only Party Animals
 * get the flag (it drives their yellow outline). Being marked makes the player a Partygoer target. No game logic here.
 * 单名玩家本局“被塞了派对气球”的状态。被标记者本人不会得知；只同步给派对狂（用于其黄色透视）。被标记者会成为派对客的目标。这里不含游戏逻辑。
 */
public final class PartyBalloonMarkComponent implements AutoSyncedComponent {
    public static final ComponentKey<PartyBalloonMarkComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("party_balloon_mark"),
            PartyBalloonMarkComponent.class
    );

    private final PlayerEntity player;
    private boolean marked;
    private @Nullable UUID marker;

    public PartyBalloonMarkComponent(PlayerEntity player) {
        this.player = player;
    }

    public static boolean isMarked(@Nullable PlayerEntity player) {
        return player != null && KEY.get(player).isMarked();
    }

    public boolean isMarked() {
        return marked;
    }

    public @Nullable UUID getMarker() {
        return marker;
    }

    /** Server only. / 仅服务端。 */
    public void mark(@Nullable UUID partyAnimal) {
        boolean changed = !marked;
        marked = true;
        marker = partyAnimal;
        if (changed) {
            sync();
        }
    }

    /** Server only. / 仅服务端。 */
    public void clear() {
        boolean changed = marked;
        marked = false;
        marker = null;
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
        return PartyAnimalRules.isPartyAnimal(GameWorldComponent.KEY.get(recipient.getWorld()).getRole(recipient));
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(marked);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        marked = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (!marked) {
            return;
        }
        tag.putBoolean("Marked", true);
        if (marker != null) {
            tag.putUuid("Marker", marker);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        marked = tag.getBoolean("Marked");
        marker = marked && tag.containsUuid("Marker") ? tag.getUuid("Marker") : null;
    }
}
