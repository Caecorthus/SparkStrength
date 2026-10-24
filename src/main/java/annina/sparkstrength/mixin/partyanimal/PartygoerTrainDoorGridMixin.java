package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerService;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A train door opened or closed (by a player, a lockpick, a blast or its own auto-close): Partygoers forget what their
 * grid cached there, since an open train door is a way through and a closed one a wall. {@code toggleBlocks} is where
 * every toggle writes the new block states.
 * 列车外门被打开或关上（玩家、撬锁、爆破或自动关门）：派对客忘掉网格在那里的缓存，因为开着的外门可以通过、关着的是墙。
 * 每次开关都在 toggleBlocks 里写入新的方块状态。
 */
@Mixin(value = SmallDoorBlockEntity.class, remap = false)
public abstract class PartygoerTrainDoorGridMixin {
    @Inject(method = "toggleBlocks", at = @At("TAIL"))
    private void sparkstrength$forgetTrainDoor(CallbackInfo ci) {
        SmallDoorBlockEntity door = (SmallDoorBlockEntity) (Object) this;
        if (door.getWorld() instanceof ServerWorld world && door.getCachedState().getBlock() instanceof TrainDoorBlock) {
            BlockPos pos = door.getPos();
            PartygoerService.onBlocksChanged(world, List.of(pos, pos.up()));
        }
    }
}
