package annina.sparkstrength.mixin.perfumer;

import annina.sparkstrength.component.perfumer.PerfumerScentComponent;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ServerPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optional internal SparkWitch seam: Zephyr also halves the Perfumer's extra corpse-proximity drain, which
 * SparkWitch derives from {@code tasks.size() * MOOD_DRAIN} in {@code PerfumerRuntime.tickPlayer} (server only).
 * Not a public API: {@code @Pseudo} plus a name-only selector with {@code require = 0} make it a silent no-op
 * when SparkWitch is absent (the target class never loads, so the mixin is never applied) or when that method
 * is refactored (zero matches; a failed {@code @Local} drops the match instead of failing). Only the 0.1.6.0
 * bytecode (one GETSTATIC {@code MOOD_DRAIN}, one {@code ServerPlayerEntity} argument) is known to match.
 * 可选的 SparkWitch 内部接缝：晨风香水同样将调香师靠近尸体时的额外理智下降减半，该值由 SparkWitch 在
 * {@code PerfumerRuntime.tickPlayer}（仅服务端）中按 {@code tasks.size() * MOOD_DRAIN} 计算。
 * 这不是公开 API：{@code @Pseudo} 加仅按方法名的选择器与 {@code require = 0}，使 SparkWitch 缺失时
 * （目标类从不加载，mixin 不会应用）或该方法被重构时（零匹配；{@code @Local} 失败只会丢弃该匹配）静默失效。
 * 目前只确认 0.1.6.0 字节码（一次 GETSTATIC {@code MOOD_DRAIN}，一个 {@code ServerPlayerEntity} 参数）可匹配。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkwitch.roles.civilian.perfumer.PerfumerRuntime", remap = false)
public abstract class SparkWitchPerfumerCorpseDrainMixin {
    @ModifyExpressionValue(
            method = "tickPlayer",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/game/GameConstants;MOOD_DRAIN:F",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 0
    )
    private static float sparkstrength$halveCorpseDrainWithZephyr(
            float drain,
            @Local(argsOnly = true) ServerPlayerEntity player
    ) {
        return PerfumerRules.zephyrMoodDrain(drain, PerfumerScentComponent.KEY.get(player).isZephyrActive());
    }
}
