package annina.sparkstrength.event;

import annina.sparkstrength.component.noisemaker.NoisemakerGlowTargetComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowUserComponent;
import annina.sparkstrength.noisemaker.NoisemakerGlowService;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;

/**
 * 统一注册 SparkStrength 的服务端事件。
 */
public final class SparkStrengthEvents {
    private SparkStrengthEvents() {
    }

    public static void register() {
        ResetPlayer.EVENT.register(player -> {
            // Wathe 在死亡、重置玩家、新一局开始等场景会触发 ResetPlayer。
            // 这里把点亮冷却和目标倒计时都清掉，避免跨局残留。
            NoisemakerGlowUserComponent.KEY.get(player).reset();
            NoisemakerGlowTargetComponent.KEY.get(player).reset();
        });

        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            // 大嗓门死亡后的“杀手发光 15 秒”是被动效果，不写入回放。
            NoisemakerGlowService.glowKillerWhenNoisemakerDies(victim, killer);
        });
    }
}
