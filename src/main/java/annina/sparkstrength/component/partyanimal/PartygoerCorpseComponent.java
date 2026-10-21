package annina.sparkstrength.component.partyanimal;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.entity.Entity;
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
 * Marks a Wathe player body as a Partygoer corpse (派对客尸体). {@code PartygoerCorpseService.spawn} sets it before
 * {@code World.spawnEntity}, so ENTITY_LOAD listeners already see it. The flag is synced to every client (the corpse is
 * drawn and labelled as the Partygoer for everyone); the summoner (the Party Animal, credited as the poisoner when a
 * Vulture eats the corpse) stays on the server.
 * 把一具 Wathe 玩家尸体标记为派对客尸体。{@code PartygoerCorpseService.spawn} 在 {@code World.spawnEntity} 之前写入，
 * 因此 ENTITY_LOAD 监听器已能看到它。标记同步给所有客户端（所有人看到的都是派对客外观与名字）；召唤者（派对狂，秃鹫吃掉
 * 这具尸体时记为下毒者）只留在服务端。
 *
 * <p>Frozen cross-mod seam: SparkWitch may reach {@link #isPartygoerCorpse(Entity)} by reflection, so the class
 * {@code annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent}, the name {@code isPartygoerCorpse}, the
 * single {@code Entity} parameter and the {@code boolean} result are frozen. It works on both sides.
 * 冻结的跨模组接缝：SparkWitch 可能通过反射调用 {@link #isPartygoerCorpse(Entity)}，因此类名、方法名
 * {@code isPartygoerCorpse}、唯一的 {@code Entity} 参数与 {@code boolean} 返回值均已冻结。双端可用。</p>
 */
public final class PartygoerCorpseComponent implements AutoSyncedComponent {
    public static final ComponentKey<PartygoerCorpseComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("partygoer_corpse"),
            PartygoerCorpseComponent.class
    );

    private final PlayerBodyEntity body;
    private boolean partygoer;
    private @Nullable UUID summoner;

    public PartygoerCorpseComponent(PlayerBodyEntity body) {
        this.body = body;
    }

    /**
     * Frozen seam (see the class doc): true iff {@code entity} is a Wathe body left by a killed Partygoer.
     * 冻结接缝（见类注释）：仅当 {@code entity} 是被打死的派对客留下的 Wathe 尸体时返回 true。
     */
    public static boolean isPartygoerCorpse(@Nullable Entity entity) {
        return entity instanceof PlayerBodyEntity corpse && KEY.get(corpse).isPartygoer();
    }

    /**
     * Server only: the Party Animal who summoned it, or null for an unmarked body.
     * 仅服务端：召唤它的派对狂；未标记的尸体返回 null。
     */
    public static @Nullable UUID summonerOf(@Nullable Entity entity) {
        return entity instanceof PlayerBodyEntity corpse ? KEY.get(corpse).summoner() : null;
    }

    public boolean isPartygoer() {
        return partygoer;
    }

    public @Nullable UUID summoner() {
        return partygoer ? summoner : null;
    }

    /**
     * Server only, before the body is spawned. The CCA start-tracking sync then delivers the flag with the body.
     * 仅服务端，在尸体生成之前调用。随后 CCA 的开始追踪同步会把标记连同尸体一起送达客户端。
     */
    public void mark(@Nullable UUID summoner) {
        this.partygoer = true;
        this.summoner = summoner;
        KEY.sync(body);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        // The summoner never leaves the server. / 召唤者绝不离开服务端。
        buf.writeBoolean(partygoer);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        partygoer = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (!partygoer) {
            return;
        }
        tag.putBoolean("Partygoer", true);
        if (summoner != null) {
            tag.putUuid("Summoner", summoner);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        partygoer = tag.getBoolean("Partygoer");
        summoner = partygoer && tag.containsUuid("Summoner") ? tag.getUuid("Summoner") : null;
    }
}
