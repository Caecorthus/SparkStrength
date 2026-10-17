package annina.sparkstrength.client.mixin.sabotage;

import annina.sparkstrength.client.sabotage.EngineTrainMotion;
import dev.doctor4t.ratatouille.client.util.ambience.BackgroundAmbientLoop;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe's train loops (inside and outside) get quieter and lower as the train slows, half way on emergency power.
 * The loop recomputes its fade volume every tick, so scaling it at the tail never compounds. Ship ambience is left
 * alone.
 * Wathe 的列车循环音（车内与车外）随列车减速变轻、变低，应急动力时为一半。循环音每 tick 都会重算淡入淡出音量，所以在末尾
 * 缩放不会累积。船的环境音不受影响。
 */
@Mixin(BackgroundAmbientLoop.class)
public abstract class EngineTrainAmbienceMixin extends MovingSoundInstance {
    protected EngineTrainAmbienceMixin(SoundEvent sound, SoundCategory category, Random random) {
        super(sound, category, random);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sparkstrength$followEngineMotion(CallbackInfo ci) {
        if (EngineTrainMotion.isTrainAmbience(getId())) {
            volume *= EngineTrainMotion.ambienceGain();
            pitch = EngineTrainMotion.ambiencePitch();
        }
    }
}
