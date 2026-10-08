package annina.sparkstrength.component.bodyguard;

import annina.sparkstrength.SparkStrength;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Bodyguard's round bookkeeping, server side only: purchase caps, the shield's authoritative cooldown (so a
 * Timekeeper refresh cannot clear it), the held item for switch detection and the targets already penalised. The vest
 * itself is chest armour (BodyguardVestService), so vanilla equipment sync already shows it to everyone.
 * 保镖的本局记账，仅服务端使用：购买上限、盾的权威冷却（计时员刷新无法清掉）、用于检测切换的手持物品，以及已扣过罚款的目标。
 * 防弹衣本身是胸甲（BodyguardVestService），原版装备同步已让所有人看到。
 */
public final class BodyguardGearComponent implements Component {
    public static final ComponentKey<BodyguardGearComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("bodyguard_gear"),
            BodyguardGearComponent.class
    );

    private boolean vestBought;
    private boolean shieldBought;
    /** World time the shield's cooldown ends; 0 when ready. / 盾冷却结束的世界时间；就绪时为 0。 */
    private long shieldCooldownUntil;
    private @Nullable Item lastMainHandItem;
    /** Server: world time the open raise started; 0 when the shield is down. / 服务端：当前举盾开始的世界时间；放下时为 0。 */
    private long raiseStartTick;
    private final Set<UUID> penalizedTargets = new HashSet<>();

    public BodyguardGearComponent(PlayerEntity player) {
    }

    public boolean isVestBought() {
        return vestBought;
    }

    public void markVestBought() {
        vestBought = true;
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
        vestBought = false;
        shieldBought = false;
        shieldCooldownUntil = 0L;
        lastMainHandItem = null;
        raiseStartTick = 0L;
        penalizedTargets.clear();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Runtime-only round gear: a reload never resumes it. / 仅运行态的单局装备：重载后不恢复。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Nothing persisted. / 不持久化任何内容。
    }
}
