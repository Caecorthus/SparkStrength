package annina.sparkstrength.role.bodyguard;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shield blocks of the current server tick for the kill reasons that SparkWitch settles with same-tick retries
 * ({@link BodyguardProtectionRules#settlesOncePerTick}). The first block of a hit pays stamina, and a later call with
 * the same key in the same tick is blocked again for free. A call on any other tick clears the memo first, so it never
 * holds more than one tick's blocks ({@link #MAX_ENTRIES} is a guard cap). Server thread only. Modelled on
 * SparkWitch's Rys-T {@code RicochetMemo} as it was before 2026-11-13 (per tick; that one is now keyed by attack source).
 * 当前服务端刻内、SparkWitch 以同刻重试结算的死因（{@link BodyguardProtectionRules#settlesOncePerTick}）的盾格挡记录。
 * 一击的第一次格挡消耗体力，同一刻内相同键的后续调用再次免费挡下。遇到其他刻先清空，因此不会超过一刻的格挡数
 * （{@link #MAX_ENTRIES} 为上限保护）。仅限服务端线程。仿照 2026-11-13 之前的 SparkWitch Rys-T
 * {@code RicochetMemo}（按刻清空；后者现已改为按攻击来源记忆）。
 */
final class BodyguardSettlementMemo {
    static final int MAX_ENTRIES = 512;

    /** One hit: victim, killer (null when none), death reason and server tick. / 一击：受害者、击杀者（无则 null）、死因与服务端刻。 */
    record Key(UUID victim, @Nullable UUID killer, Identifier reason, long tick) {
    }

    private final Set<Key> blocked = new HashSet<>();
    private long tick = Long.MIN_VALUE;

    /** Whether this hit was already blocked this tick. / 这一击本刻是否已被挡下。 */
    boolean wasBlocked(Key key) {
        advance(key.tick());
        return blocked.contains(key);
    }

    void rememberBlock(Key key) {
        advance(key.tick());
        if (blocked.size() < MAX_ENTRIES) {
            blocked.add(key);
        }
    }

    int size() {
        return blocked.size();
    }

    private void advance(long now) {
        if (now != tick) {
            blocked.clear();
            tick = now;
        }
    }
}
