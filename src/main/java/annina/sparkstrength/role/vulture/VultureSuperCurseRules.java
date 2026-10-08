package annina.sparkstrength.role.vulture;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Stable tuning and pure checks for the Vulture's Super Curse (owner spec 2026-10-07). No runtime state lives here.
 * 秃鹫“超级骂”的稳定数值与纯判定（所有者 2026-10-07 规格），此处不保存任何运行态。
 */
public final class VultureSuperCurseRules {
    /**
     * SparkTraits' Childish trait. Read only through the public facade bridges; a missing SparkTraits means "not Childish".
     * SparkTraits 的“幼稚”词条。只经公开门面桥接读取；未安装 SparkTraits 时一律视为非幼稚。
     */
    public static final Identifier CHILDISH_TRAIT_ID = Identifier.of("sparktraits", "childish");

    /** First use 60 s after becoming the Vulture. / 成为秃鹫 60 秒后才能首次使用。 */
    public static final int INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    /** 90 s after each use, counted from the press. / 每次使用后冷却 90 秒，从按下时开始计。 */
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 30);
    /**
     * How long a curse may still be audible: the longest clip (29.8 s) plus slack for stream start-up. Past this the
     * clip has ended on every client, so death and round end need not send a stop packet.
     * 一次超级骂可能仍在播放的时长：最长音频（29.8 秒）加上流式加载的余量。超过后各客户端都已播完，死亡与回合结束无需再发停止包。
     */
    public static final int CURSE_AUDIBLE_TICKS = GameConstants.getInTicks(0, 31);
    /**
     * Volume 2.0 doubles the 16-block attenuation to ~32 blocks for both the curse and the death scream.
     * 音量 2.0 把 16 格衰减距离翻倍到约 32 格，超级骂与惨叫共用。
     */
    public static final float SOUND_VOLUME = 2.0F;
    /** Owner-only cooldown sync cadence while counting down. / 倒计时期间仅向本人同步的节奏。 */
    public static final int COOLDOWN_SYNC_INTERVAL_TICKS = 20;

    private VultureSuperCurseRules() {
    }

    public static boolean isVulture(@Nullable Role role) {
        return VultureSkateboardRules.isVulture(role);
    }

    /** True when {@code traitIds} (live or round-end ids) contain Childish; null-safe. / 词条列表含“幼稚”时为 true，容忍 null。 */
    public static boolean isChildish(@Nullable Collection<Identifier> traitIds) {
        return traitIds != null && traitIds.contains(CHILDISH_TRAIT_ID);
    }

    /** One server tick of countdown, never below zero. / 一个服务端刻的倒计时，不会低于 0。 */
    public static int tickCooldown(int cooldownTicks) {
        return Math.max(0, cooldownTicks - 1);
    }

    /** Sync when the countdown lands on a whole second or reaches zero. / 倒计时落在整秒或归零时同步。 */
    public static boolean shouldSyncCooldown(int cooldownTicksAfterTick) {
        return cooldownTicksAfterTick == 0 || cooldownTicksAfterTick % COOLDOWN_SYNC_INTERVAL_TICKS == 0;
    }

    /** World time after which a curse started at {@code startTick} is certainly over. / 在该时间开始的超级骂必定结束的世界时间。 */
    public static long curseEndTick(long startTick) {
        return startTick + CURSE_AUDIBLE_TICKS;
    }

    /** Whether a curse ending at {@code curseEndTick} may still be playing at {@code now}. / 该时刻超级骂是否可能仍在播放。 */
    public static boolean isCurseAudible(long curseEndTick, long now) {
        return now < curseEndTick;
    }
}
