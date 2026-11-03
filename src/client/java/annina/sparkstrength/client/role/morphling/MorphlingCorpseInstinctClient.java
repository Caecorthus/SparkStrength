package annina.sparkstrength.client.role.morphling;

import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.role.morphling.MorphlingCorpseRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;

import java.util.function.IntSupplier;

/**
 * Client side of {@link MorphlingCorpseRules}. For a corpse-mode Morphling the highlight is evaluated as if the
 * instinct key were released, like blackout sight: every key-gated answer (Wathe's killer path, SparkTraits' Impostor
 * and effective-killer colours, faction policies, add-on listeners) drops out and keyless ability outlines survive.
 * Render thread only.
 * {@link MorphlingCorpseRules} 的客户端部分。对尸体模式的变形者，按“未按本能键”计算描边，与熄灯视野相同：所有需要按键的
 * 结果（Wathe 杀手路径、SparkTraits 内鬼与有效杀手颜色、阵营策略、附属模组监听器）都会消失，无需按键的技能描边保留。
 * 仅在渲染线程使用。
 */
public final class MorphlingCorpseInstinctClient {
    /** Key-less evaluations in progress (Magician stand-ins nest). / 正在进行的“松开按键”计算层数（魔术师替身会嵌套）。 */
    private static int keyReleasedDepth;

    private MorphlingCorpseInstinctClient() {
    }

    public static int highlight(Entity target, IntSupplier original) {
        if (keyReleasedDepth > 0 || !cloaks(target)) {
            return original.getAsInt();
        }
        keyReleasedDepth++;
        try {
            return original.getAsInt();
        } finally {
            keyReleasedDepth--;
        }
    }

    /** Whether Wathe's instinct gates must read false right now. / 此刻 Wathe 的本能入口是否必须返回 false。 */
    public static boolean releasesKey() {
        return keyReleasedDepth > 0;
    }

    /** Cheapest test first: nearly no target is in corpse mode. / 最便宜的判断在前：几乎没有目标处于尸体模式。 */
    private static boolean cloaks(Entity target) {
        if (!(target instanceof PlayerEntity player)) {
            return false;
        }
        MorphlingPlayerComponent morphling = MorphlingPlayerComponent.KEY.getNullable(player);
        if (morphling == null || !morphling.corpseMode) {
            return false;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }
        return MorphlingCorpseRules.hidesFromKeyedInstinct(
                true,
                viewer.getUuid().equals(player.getUuid()),
                MorphlingCorpseRules.exemptViewer(
                        GameFunctions.isPlayerPlayingAndAlive(viewer),
                        GameFunctions.isPlayerSpectatingOrCreative(viewer),
                        SparkWitchCompat.isWraithActive(viewer)
                )
        );
    }
}
