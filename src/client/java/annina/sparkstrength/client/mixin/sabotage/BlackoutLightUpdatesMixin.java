package annina.sparkstrength.client.mixin.sabotage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Blackouts must darken every client: a lamp switched off near the player only changes the light the client computes
 * itself (the server sends light packets to players at the edge of view distance only). Sodium Extra's "Light Updates:
 * Off" cancels {@code LightingProvider#checkBlock} at HEAD, so the lamps went out but the train stayed lit. On the
 * client world this call goes straight to the block and sky providers instead, which is all that method does; the
 * integrated server keeps its own queued {@code ServerLightingProvider} path.
 * 熄灯必须让每个客户端都变暗：玩家附近的灯熄灭后，只有客户端自己计算的光照会变化（服务端只给视距边缘的玩家发送光照包）。
 * Sodium Extra 的「光照更新：关」在 HEAD 处取消 LightingProvider#checkBlock，于是灯灭了车厢却依旧明亮。
 * 在客户端世界里，这次调用直接交给方块光与天空光提供者，这正是该方法本身做的全部事情；内置服务端仍走它自己排队的
 * ServerLightingProvider 路径。
 */
@Mixin(WorldChunk.class)
public abstract class BlackoutLightUpdatesMixin {
    @Shadow
    @Final
    World world;

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/light/LightingProvider;checkBlock(Lnet/minecraft/util/math/BlockPos;)V"))
    private void sparkstrength$alwaysRelight(LightingProvider lighting, BlockPos pos, Operation<Void> original) {
        if (!world.isClient()) {
            original.call(lighting, pos);
            return;
        }
        lighting.get(LightType.BLOCK).checkBlock(pos);
        lighting.get(LightType.SKY).checkBlock(pos);
    }
}
