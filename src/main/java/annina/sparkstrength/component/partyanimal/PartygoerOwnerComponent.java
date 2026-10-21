package annina.sparkstrength.component.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.partyanimal.PartygoerVisibilityRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

/**
 * Who summoned this Partygoer (派对客), for the blackout instinct rule: a killer sees another killer's Partygoer in the
 * dark only once the owner is a tablet-linked partner ({@link PartygoerVisibilityRules#blackoutMayReveal}). Set by
 * {@code PartygoerService.summon} before the entity spawns, so the CCA start-tracking sync delivers it.
 * 本派对客的召唤者，供熄灯本能规则使用：杀手只有在召唤者是平板互认对象时才能在黑暗中看到另一名杀手的派对客
 * （见 PartygoerVisibilityRules#blackoutMayReveal）。由 PartygoerService.summon 在实体生成前写入，因此 CCA 的开始追踪同步会把它送达。
 *
 * <p>Privacy: every tracking client gets a packet, but only killers and spectators/creative players
 * ({@link PartygoerVisibilityRules#receivesOwner}) get the UUID; everyone else gets "no owner", which also clears a
 * stale copy from a viewer who stopped qualifying (a role swap). Eligibility changes mid-round (deaths, Impostor,
 * swaps), so the owner is re-sent every {@link #RESYNC_INTERVAL_TICKS} ticks. Runtime only: a Partygoer never persists.
 * 隐私：每个追踪客户端都会收到数据包，但只有杀手与旁观者/创造模式玩家（receivesOwner）会收到 UUID；其他人收到「无召唤者」，
 * 这也会清除已不再符合条件的观察者（身份互换）手里的旧副本。资格会在局中变化（死亡、内鬼、互换），因此每
 * RESYNC_INTERVAL_TICKS 刻重发一次。仅运行时：派对客从不持久化。</p>
 */
public final class PartygoerOwnerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<PartygoerOwnerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("partygoer_owner"),
            PartygoerOwnerComponent.class
    );
    /** Re-send cadence (1 s). / 重发间隔（1 秒）。 */
    public static final int RESYNC_INTERVAL_TICKS = 20;

    private final PartygoerEntity partygoer;
    private @Nullable UUID owner;

    public PartygoerOwnerComponent(PartygoerEntity partygoer) {
        this.partygoer = partygoer;
    }

    /**
     * The owner: always on the server; on a client only for killers and spectators/creative players, else null.
     * 召唤者：服务端始终可得；客户端只有杀手与旁观者/创造模式玩家可得，其余为 null。
     */
    public static @Nullable UUID ownerOf(@Nullable Entity entity) {
        return entity instanceof PartygoerEntity partygoer ? KEY.get(partygoer).owner : null;
    }

    /** Server only, before the entity spawns. / 仅服务端，在实体生成前调用。 */
    public void setOwner(UUID owner) {
        this.owner = owner;
        KEY.sync(partygoer);
    }

    @Override
    public void serverTick() {
        if (owner != null && partygoer.age % RESYNC_INTERVAL_TICKS == 0) {
            KEY.sync(partygoer);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone gets a packet so a viewer who lost eligibility is cleared; the payload decides what they learn.
        // 所有人都收到数据包，以便清除已失去资格的观察者；实际内容由负载决定。
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean send = owner != null && PartygoerVisibilityRules.receivesOwner(
                SparkTraitsCompat.isEffectiveKiller(GameWorldComponent.KEY.get(recipient.getWorld()).getRole(recipient),
                        recipient),
                GameFunctions.isPlayerSpectatingOrCreative(recipient));
        buf.writeBoolean(send);
        if (send) {
            buf.writeUuid(owner);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        owner = buf.readBoolean() ? buf.readUuid() : null;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime only. / 仅运行时。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime only; a Partygoer read from disk has no brain and discards itself. / 仅运行时；从存档读出的派对客没有大脑，会自行移除。
    }
}
