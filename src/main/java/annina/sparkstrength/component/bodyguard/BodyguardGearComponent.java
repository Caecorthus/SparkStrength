package annina.sparkstrength.component.bodyguard;

import annina.sparkstrength.SparkStrength;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Bodyguard's round gear. Only the worn vest syncs, and only to its owner: the vest is invisible to everyone else
 * (owner rule). The rest is server bookkeeping: purchase caps, the shield's authoritative cooldown (so a Timekeeper
 * refresh cannot clear it), the held item for switch detection and the targets already penalised.
 * 保镖的本局装备。只同步“已穿防弹衣”，且只同步给本人：防弹衣对其他人不可见（所有者规则）。其余是服务端记账：
 * 购买上限、盾的权威冷却（计时员刷新无法清掉）、用于检测切换的手持物品，以及已扣过罚款的目标。
 */
public final class BodyguardGearComponent implements AutoSyncedComponent {
    public static final ComponentKey<BodyguardGearComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("bodyguard_gear"),
            BodyguardGearComponent.class
    );

    private final PlayerEntity player;
    private boolean vestWorn;
    private boolean vestBought;
    private boolean shieldBought;
    /** World time the shield's cooldown ends; 0 when ready. / 盾冷却结束的世界时间；就绪时为 0。 */
    private long shieldCooldownUntil;
    private @Nullable Item lastMainHandItem;
    /** Server: world time the open raise started; 0 when the shield is down. / 服务端：当前举盾开始的世界时间；放下时为 0。 */
    private long raiseStartTick;
    private final Set<UUID> penalizedTargets = new HashSet<>();

    public BodyguardGearComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isVestWorn() {
        return vestWorn;
    }

    /** Puts the vest on once per round; false when it was already bought. / 每局穿上一次防弹衣；已买过时返回 false。 */
    public boolean wearVest() {
        if (vestBought) {
            return false;
        }
        vestBought = true;
        vestWorn = true;
        sync();
        return true;
    }

    public void breakVest() {
        if (!vestWorn) {
            return;
        }
        vestWorn = false;
        sync();
    }

    public boolean isShieldBought() {
        return shieldBought;
    }

    public void markShieldBought() {
        shieldBought = true;
    }

    public long shieldCooldownUntil() {
        return shieldCooldownUntil;
    }

    public void setShieldCooldownUntil(long worldTime) {
        shieldCooldownUntil = worldTime;
    }

    public @Nullable Item lastMainHandItem() {
        return lastMainHandItem;
    }

    public void setLastMainHandItem(@Nullable Item item) {
        lastMainHandItem = item;
    }

    public boolean isRaising() {
        return raiseStartTick != 0L;
    }

    public long raiseStartTick() {
        return raiseStartTick;
    }

    public void startRaise(long worldTime) {
        raiseStartTick = Math.max(1L, worldTime);
    }

    public void endRaise() {
        raiseStartTick = 0L;
    }

    /** Records a penalty for this target; false when it was already charged. / 记录对该目标的罚款；已扣过时返回 false。 */
    public boolean markPenalized(UUID target) {
        return penalizedTargets.add(target);
    }

    public boolean wasPenalized(UUID target) {
        return penalizedTargets.contains(target);
    }

    /** Clears every per-round field; called at round start and end and on reset. / 清空所有单局字段；开局、结束与重置时调用。 */
    public void reset() {
        boolean wasWorn = vestWorn;
        vestWorn = false;
        vestBought = false;
        shieldBought = false;
        shieldCooldownUntil = 0L;
        lastMainHandItem = null;
        raiseStartTick = 0L;
        penalizedTargets.clear();
        if (wasWorn) {
            sync();
        }
    }

    private void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // The vest is invisible to others (owner rule). / 防弹衣对他人不可见（所有者规则）。
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(vestWorn);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        vestWorn = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only round gear: a reload never resumes it. / 仅运行态的单局装备：重载后不恢复。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        vestWorn = false;
    }
}
