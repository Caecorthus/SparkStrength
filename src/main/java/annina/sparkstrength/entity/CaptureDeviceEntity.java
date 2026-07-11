package annina.sparkstrength.entity;

import annina.sparkstrength.role.engineer.EngineerCaptureDeviceService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Minecraft state/tick Adapter for a placed capture device.
 * 放置后捕捉装置的 Minecraft 状态与 tick Adapter。
 *
 * <p>DataTracker and NBT stay here because they are entity persistence concerns. Candidate selection,
 * lifecycle decisions, effects, reports, sounds, and replay events belong to the Engineer service.
 * DataTracker 与 NBT 属于实体持久化；候选筛选、生命周期决定、效果、报告、声音与回放归工程师服务所有。</p>
 */
public final class CaptureDeviceEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER_UUID =
            DataTracker.registerData(CaptureDeviceEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Boolean> CEILING_MOUNTED =
            DataTracker.registerData(CaptureDeviceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private int lifetimeTicks;

    public CaptureDeviceEntity(EntityType<? extends CaptureDeviceEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(OWNER_UUID, Optional.empty());
        builder.add(CEILING_MOUNTED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld serverWorld) {
            EngineerCaptureDeviceService.tick(this, serverWorld, ++lifetimeTicks);
        }
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    public @Nullable UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER_UUID).orElse(null);
    }

    public void setCeilingMounted(boolean ceilingMounted) {
        this.dataTracker.set(CEILING_MOUNTED, ceilingMounted);
    }

    public boolean isCeilingMounted() {
        return this.dataTracker.get(CEILING_MOUNTED);
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) {
            setOwnerUuid(nbt.getUuid("Owner"));
        }
        if (nbt.contains("CeilingMounted")) {
            setCeilingMounted(nbt.getBoolean("CeilingMounted"));
        }
        lifetimeTicks = nbt.getInt("LifetimeTicks");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        nbt.putBoolean("CeilingMounted", isCeilingMounted());
        nbt.putInt("LifetimeTicks", lifetimeTicks);
    }
}
