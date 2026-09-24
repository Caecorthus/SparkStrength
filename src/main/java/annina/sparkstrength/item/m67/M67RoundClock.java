package annina.sparkstrength.item.m67;

import java.util.UUID;

/** Server elapsed-time clock, independent of the mutable round timer. / 服务端计时不受回合剩余时间修改影响。 */
final class M67RoundClock {
    private final UUID id = UUID.randomUUID();
    private final long startTick;

    M67RoundClock(long startTick) {
        this.startTick = startTick;
    }

    UUID id() {
        return id;
    }

    int openingRemaining(long now) {
        long elapsed = Math.max(0L, now - startTick);
        return (int) Math.max(0L, M67Rules.OPENING_TICKS - elapsed);
    }
}
