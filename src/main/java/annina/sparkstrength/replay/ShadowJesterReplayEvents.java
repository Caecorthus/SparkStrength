package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Replay line for the Shadow Jesters' Double-Shadow Finale.
 * 影子小丑双影谢幕的回放文本。
 */
public final class ShadowJesterReplayEvents {
    /**
     * 新版“双影谢幕”开始时写入的全局回放事件。
     *
     * <p>该事件与 NoellesRoles 原有的 {@code shadow_showdown_start} 分开，
     * 因为两者分别代表“影子小丑对抗杀手”和“杀手全部死亡后影子小丑清场”
     * 两种不同的谢幕机制。</p>
     */
    public static final Identifier SHADOW_JESTER_SHOWDOWN_STARTED =
            SparkStrength.id("shadow_jester_showdown_started");

    private ShadowJesterReplayEvents() {
    }

    static void register() {
        // 新版双影谢幕是无特定触发者的全局事件，因此直接返回固定回放文案。
        // 文案中的 §d 和 §r 保留与 NoellesRoles 原版谢幕回放相同的紫色格式。
        ReplayRegistry.registerGlobalEventFormatter(
                SHADOW_JESTER_SHOWDOWN_STARTED,
                (event, match, world) -> Text.translatable(
                        "replay.global.sparkstrength.shadow_jester_showdown_started"
                )
        );
    }
}
