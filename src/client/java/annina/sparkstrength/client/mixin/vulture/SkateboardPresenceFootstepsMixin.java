package annina.sparkstrength.client.mixin.vulture;

import annina.sparkstrength.client.role.vulture.SkateboardClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Optional PresenceFootsteps seam (the players' modpack ships it): PF plays its own client-side footsteps from movement,
 * so the vanilla step-sound mute does not reach it. A skating player generates no PF footsteps; the rolling sound
 * replaces them. Not a public API: {@code @Pseudo}, {@code require = 0} and a reflective, fail-open read of the
 * generator's {@code entity} field (no {@code @Shadow}, which would crash if PF renamed it) make this a silent no-op
 * without PF or if the generator changes.
 * 可选的 PresenceFootsteps 接缝（玩家整合包自带）：PF 根据移动在客户端自行播放脚步声，原版脚步静音管不到它。
 * 滑行中的玩家不产生 PF 脚步声，改由滚轮声代替。并非公开 API：@Pseudo、require = 0，以及对生成器 entity 字段的反射读取
 * （不用 @Shadow，PF 改名时不会崩溃；读取失败即不拦截）使 PF 缺失或生成器变更时静默失效。
 */
@Pseudo
@Mixin(targets = "eu.ha3.presencefootsteps.sound.generator.TerrestrialStepSoundGenerator", remap = false)
public abstract class SkateboardPresenceFootstepsMixin {
    @Unique
    private static Field sparkstrength$entityField;
    @Unique
    private static boolean sparkstrength$entityFieldResolved;

    @Inject(method = "generateFootsteps", at = @At("HEAD"), cancellable = true, require = 0)
    private void sparkstrength$skipSkaterFootsteps(CallbackInfo ci) {
        if (sparkstrength$entity(this) instanceof PlayerEntity player && SkateboardClient.isRiding(player)) {
            ci.cancel();
        }
    }

    @Unique
    private static Object sparkstrength$entity(Object generator) {
        if (!sparkstrength$entityFieldResolved) {
            sparkstrength$entityFieldResolved = true;
            // Declared on TerrestrialStepSoundGenerator; walk up in case a subclass (e.g. the winged one) runs first.
            // 声明于 TerrestrialStepSoundGenerator；若先遇到子类（如有翼生成器）则向上查找。
            for (Class<?> type = generator.getClass(); type != null && sparkstrength$entityField == null;
                 type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField("entity");
                    field.setAccessible(true);
                    sparkstrength$entityField = field;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Keep looking; stays null (fail open) if no class declares it.
                    // 继续查找；若没有类声明该字段则保持为 null（不拦截）。
                }
            }
        }
        if (sparkstrength$entityField == null) {
            return null;
        }
        try {
            return sparkstrength$entityField.get(generator);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
