package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.SabotageSkyLight;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lights sabotage: the client world's sky light reads as 0 inside the train. Covers per-position reads (entities,
 * block entities, particles, smooth lighting) and whole-section reads (Sodium copies these when meshing). Neither
 * method is overwritten by Sodium. {@link SabotageSkyLight} only answers for the client world's sky provider.
 * 灯光破坏：车厢内客户端世界的天空光照读数为 0。覆盖按位置读取（实体、方块实体、粒子、平滑光照）与整段读取
 * （Sodium 生成网格时复制）。这两个方法都没有被 Sodium 覆写。SabotageSkyLight 只对客户端世界的天空光照提供者生效。
 */
@Mixin(ChunkLightProvider.class)
public abstract class SabotageSkyLightMixin {
    @Inject(method = "getLightLevel(Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$sabotageSkyLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (SabotageSkyLight.isDark(this, pos)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "getLightSection", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$sabotageSkySection(ChunkSectionPos pos, CallbackInfoReturnable<ChunkNibbleArray> cir) {
        ChunkNibbleArray dark = SabotageSkyLight.darkSection(this, pos);
        if (dark != null) {
            cir.setReturnValue(dark);
        }
    }
}
