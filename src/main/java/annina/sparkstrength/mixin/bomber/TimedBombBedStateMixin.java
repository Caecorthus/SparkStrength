package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.BomberTrapService;
import annina.sparkstrength.role.bomber.TimedBombMarkerSync;
import annina.sparkstrength.role.bomber.TimedBombTrapHolder;
import dev.doctor4t.wathe.block_entity.TrimmedBedBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 给 wathe 床方块实体补一个“预埋定时炸弹”的同步状态。
 *
 * <p>Security: the owner stays in the saved NBT but never reaches clients ({@code toInitialChunkDataNbt} strips it, and
 * a plant or trigger sends no block-entity update); trap viewers get the position from {@link TimedBombMarkerSync}.
 * 安全：埋设者保留在存档 NBT 中，但绝不发给客户端（toInitialChunkDataNbt 会去掉它，埋设或触发也不发送方块实体更新）；
 * 陷阱观察者改由 TimedBombMarkerSync 获得位置。</p>
 */
@Mixin(TrimmedBedBlockEntity.class)
public abstract class TimedBombBedStateMixin extends BlockEntity implements TimedBombTrapHolder {
    @Unique
    private UUID sparkstrength$timedBombTrapOwner;

    private TimedBombBedStateMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean sparkstrength$hasTimedBombTrap() {
        return this.sparkstrength$timedBombTrapOwner != null;
    }

    @Override
    public @Nullable UUID sparkstrength$getTimedBombTrapOwner() {
        return this.sparkstrength$timedBombTrapOwner;
    }

    @Override
    public void sparkstrength$setTimedBombTrapOwner(@Nullable UUID owner) {
        this.sparkstrength$timedBombTrapOwner = owner;
        if (owner != null) {
            BomberTrapService.rememberTrap(this.world, this.pos);
        } else {
            BomberTrapService.forgetTrap(this.world, this.pos);
        }
        this.sparkstrength$syncTimedBombTrap();
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void sparkstrength$writeTimedBombTrap(
            NbtCompound nbt,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo ci
    ) {
        if (this.sparkstrength$timedBombTrapOwner != null) {
            nbt.putUuid(BomberTrapService.TRAP_OWNER_NBT_KEY, this.sparkstrength$timedBombTrapOwner);
        } else {
            nbt.remove(BomberTrapService.TRAP_OWNER_NBT_KEY);
        }
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void sparkstrength$readTimedBombTrap(
            NbtCompound nbt,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo ci
    ) {
        this.sparkstrength$timedBombTrapOwner = nbt.containsUuid(BomberTrapService.TRAP_OWNER_NBT_KEY)
                ? nbt.getUuid(BomberTrapService.TRAP_OWNER_NBT_KEY)
                : null;
        // NBT applied to a block entity already in a server world (a structure-placed train reset, /data) changes the
        // trap without the setter; during chunk loading the world is still null and the chunk-send path covers it.
        // 写入已在服务端世界中的方块实体的 NBT（结构放置的列车重置、/data）会不经设置方法改变陷阱；区块加载期间世界仍为 null，
        // 由区块发送路径负责。
        if (this.world instanceof ServerWorld serverWorld) {
            boolean present = this.sparkstrength$timedBombTrapOwner != null;
            if (present) {
                BomberTrapService.rememberTrap(serverWorld, this.pos);
            } else {
                BomberTrapService.forgetTrap(serverWorld, this.pos);
            }
            TimedBombMarkerSync.onMarkerChanged(serverWorld, this.pos, present);
        }
    }

    /*
     * Wathe builds both the initial chunk data and the update packet from this method, and every client tracking the
     * chunk gets it, so the trap owner is removed here; writeNbt above keeps it for the server and the save. createNbt
     * returns a fresh compound on every call, so editing it does not touch persistence.
     * Wathe 的初始区块数据与更新包都由此方法生成，并会发给所有追踪该区块的客户端，因此在此去掉埋设者；上面的 writeNbt
     * 仍为服务端与存档保留它。createNbt 每次都返回新的复合标签，修改它不会影响持久化。
     */
    @Inject(method = "toInitialChunkDataNbt", at = @At("RETURN"))
    private void sparkstrength$hideTimedBombTrapFromClients(
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfoReturnable<NbtCompound> cir
    ) {
        NbtCompound nbt = cir.getReturnValue();
        if (nbt != null) {
            nbt.remove(BomberTrapService.TRAP_OWNER_NBT_KEY);
        }
    }

    /*
     * Deliberately no world.updateListeners: the client NBT no longer changes with the trap, and a block-entity update
     * for an otherwise unchanged block would tell every tracking client that a trap was just planted or sprung. The
     * change is saved (markDirty) and announced to trap viewers only.
     * 刻意不调用 world.updateListeners：客户端 NBT 不再随陷阱变化，而对外观未变的方块发送方块实体更新，会让所有追踪它的
     * 客户端得知刚有陷阱被埋设或触发。变化只会被保存（markDirty），并只通知陷阱观察者。
     */
    @Unique
    private void sparkstrength$syncTimedBombTrap() {
        if (this.world instanceof ServerWorld serverWorld) {
            this.markDirty();
            TimedBombMarkerSync.onMarkerChanged(serverWorld, this.pos, this.sparkstrength$timedBombTrapOwner != null);
        }
    }
}
