package annina.sparkstrength.component.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.partyanimal.PartyAnimalRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * The Party Animal's Partygoer skill state: tasks done towards the unlock, the cooldown, and a live summary of its
 * Partygoer for the HUD. The server owns everything; it is synced only to the Party Animal.
 * 派对狂「派对客」技能状态：解锁所需任务进度、冷却，以及派对客的实时概况（供 HUD 显示）。全部由服务端持有，只同步给派对狂本人。
 */
public final class PartyAnimalSkillComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<PartyAnimalSkillComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("party_animal_skill"),
            PartyAnimalSkillComponent.class
    );

    private final PlayerEntity player;
    private int completedTasks;
    private int cooldownTicks;
    /** {@code PartygoerState} wire of the live Partygoer, 0 when there is none. / 场上派对客的阶段 wire，没有时为 0。 */
    private int activeState;
    private String activeTarget = "";
    private int activeSeconds;

    public PartyAnimalSkillComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public boolean isUnlocked() {
        return PartyAnimalRules.isUnlocked(completedTasks);
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean hasActive() {
        return activeState != 0;
    }

    public int getActiveState() {
        return activeState;
    }

    public String getActiveTarget() {
        return activeTarget;
    }

    public int getActiveSeconds() {
        return activeSeconds;
    }

    /** A task done as the Party Animal; counts up to the unlock. / 当派对狂期间完成一个任务，累计到解锁为止。 */
    public void recordTask() {
        if (completedTasks < PartyAnimalRules.UNLOCK_TASKS) {
            completedTasks++;
            sync();
        }
    }

    public void startCooldown() {
        cooldownTicks = PartyAnimalRules.COOLDOWN_TICKS;
        sync();
    }

    /** Timekeeper refresh. / 计时者刷新。 */
    public void clearCooldown() {
        if (cooldownTicks != 0) {
            cooldownTicks = 0;
            sync();
        }
    }

    public void setActive(int stateWire, String target, int seconds) {
        if (activeState == stateWire && activeTarget.equals(target) && activeSeconds == seconds) {
            return;
        }
        activeState = stateWire;
        activeTarget = target;
        activeSeconds = seconds;
        sync();
    }

    public void clearActive() {
        setActive(0, "", 0);
    }

    /** New role or new round. / 新职业或新对局。 */
    public void reset() {
        if (completedTasks == 0 && cooldownTicks == 0 && activeState == 0 && activeTarget.isEmpty()
                && activeSeconds == 0) {
            return;
        }
        completedTasks = 0;
        cooldownTicks = 0;
        activeState = 0;
        activeTarget = "";
        activeSeconds = 0;
        sync();
    }

    public void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void serverTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (PartyAnimalRules.shouldSyncCooldown(cooldownTicks)) {
                sync();
            }
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(completedTasks);
        buf.writeVarInt(cooldownTicks);
        buf.writeVarInt(activeState);
        buf.writeString(activeTarget);
        buf.writeVarInt(activeSeconds);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        completedTasks = buf.readVarInt();
        cooldownTicks = buf.readVarInt();
        activeState = buf.readVarInt();
        activeTarget = buf.readString();
        activeSeconds = buf.readVarInt();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (completedTasks > 0) {
            tag.putInt("Tasks", completedTasks);
        }
        if (cooldownTicks > 0) {
            tag.putInt("Cooldown", cooldownTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        completedTasks = Math.max(0, Math.min(PartyAnimalRules.UNLOCK_TASKS, tag.getInt("Tasks")));
        cooldownTicks = Math.max(0, tag.getInt("Cooldown"));
        activeState = 0;
        activeTarget = "";
        activeSeconds = 0;
    }
}
