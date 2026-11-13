package annina.sparkstrength.client.mixin.bomber;

import annina.sparkstrength.client.role.bomber.TimedBombMarkerClientStore;
import annina.sparkstrength.role.bomber.BomberTrapService;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 托盘中预埋定时炸弹的客户端提示粒子。
 *
 * <p>粒子只在本客户端玩家能使用杀手阵营特性，或验尸官当前伪装成炸弹客时显示；
 * 陷阱位置由服务端只发给这些观察者（TimedBombMarkerClientStore），客户端只负责“能不能看见”。</p>
 *
 * <p>Trap positions come from {@link TimedBombMarkerClientStore}, filled only for trap viewers; the tray's client NBT
 * no longer carries the owner. 方块实体的客户端 NBT 不再携带埋设者。</p>
 */
@Mixin(BeveragePlateBlockEntity.class)
public abstract class TimedBombTrayViewMixin {
    @Inject(method = "clientTick", at = @At("HEAD"), remap = false)
    private static void sparkstrength$renderTimedBombTray(
            World world,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            CallbackInfo ci
    ) {
        if (!TimedBombMarkerClientStore.contains(world, pos)) {
            return;
        }
        if (!sparkstrength$canSeeTimedBombTrap(world)) {
            return;
        }
        if (world.getRandom().nextBetween(0, 20) < 17) {
            return;
        }

        world.addParticle(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5F,
                pos.getY() + 0.5F,
                pos.getZ() + 0.5F,
                0.0F,
                0.04F,
                0.0F
        );
    }

    private static boolean sparkstrength$canSeeTimedBombTrap(World world) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        }

        return BomberTrapService.seesTimedBombTraps(client.player);
    }
}
