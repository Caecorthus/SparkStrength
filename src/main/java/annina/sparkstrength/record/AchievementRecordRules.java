package annina.sparkstrength.record;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure decisions behind the achievement record events (no Minecraft types, so local tests compile this class alone).
 * 成就记录事件背后的纯判断（不含 Minecraft 类型，本地测试可单独编译）。
 */
public final class AchievementRecordRules {
    /** {@code speed_amplifier} written when the rider has no Speed effect. 骑手没有速度效果时写入的 speed_amplifier。 */
    public static final int NO_SPEED_AMPLIFIER = -1;

    private AchievementRecordRules() {
    }

    public static int speedAmplifier(boolean hasSpeed, int amplifier) {
        return hasSpeed ? amplifier : NO_SPEED_AMPLIFIER;
    }

    /**
     * The killer credited with the victim's most recent recorded death: the killer of the last death event for that
     * victim, scanning from the end. Null when the victim has no recorded death, or that death credits nobody
     * (e.g. a poison death, where Wathe passes no killer).
     * 受害者最近一次被记录的死亡所记的凶手：从末尾向前找该受害者的最后一条死亡事件并取其凶手。受害者没有死亡记录，
     * 或该次死亡未记凶手（如中毒死亡，Wathe 不传凶手）时返回 null。
     */
    public static <E> UUID creditedKiller(List<E> events, UUID victim, Predicate<E> isDeath,
                                          Function<E, UUID> victimOf, Function<E, UUID> killerOf) {
        if (events == null || victim == null) {
            return null;
        }
        for (int index = events.size() - 1; index >= 0; index--) {
            E event = events.get(index);
            if (event != null && isDeath.test(event) && victim.equals(victimOf.apply(event))) {
                return killerOf.apply(event);
            }
        }
        return null;
    }

    public static boolean isCreditedKiller(UUID suspect, UUID creditedKiller) {
        return suspect != null && suspect.equals(creditedKiller);
    }

    /**
     * A Coroner disguise (body owner + role id) really changed; re-selecting the same body is not a change, and
     * clearing changes only when something was set (the same no-op rule as CoronerPlayerComponent.clearDisguise).
     * 验尸官伪装（尸体主人 + 职业 id）确实发生了变化；重新选择同一具尸体不算变化，清除仅在之前有伪装时才算变化
     * （与 CoronerPlayerComponent.clearDisguise 的空操作规则一致）。
     */
    public static boolean disguiseChanged(UUID previousUuid, String previousRoleId, UUID nextUuid, String nextRoleId) {
        return !Objects.equals(previousUuid, nextUuid) || !Objects.equals(previousRoleId, nextRoleId);
    }
}
