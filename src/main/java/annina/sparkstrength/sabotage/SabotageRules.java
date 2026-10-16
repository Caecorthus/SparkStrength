package annina.sparkstrength.sabotage;

/**
 * Pure numbers and decisions of the sabotage system, free of Minecraft types so local tests can compile it alone.
 * 破坏系统的纯数值与判定，不依赖 Minecraft 类型，便于本地测试单独编译。
 *
 * <p>Durability is kept in hundredths of a percent (10000 = 100%) so every per-tick rate is a whole number.
 * 耐久以百分之一个百分点为单位（10000 = 100%），使每 tick 的速率都是整数。</p>
 */
public final class SabotageRules {
    public static final int DURABILITY_MAX = 10_000;
    /** Lockpick damage: 3% per second. / 开锁器破坏：每秒 3%。 */
    public static final int PRY_PER_TICK = 15;
    /** Master Saboteur lockpick damage: 5% per second. / 破坏大师的开锁器破坏：每秒 5%。 */
    public static final int PRY_PER_TICK_MASTER = 25;
    /** A damaged box starts healing after 15 s without damage. / 受损的箱子 15 秒没被破坏后开始恢复。 */
    public static final int REGEN_DELAY_TICKS = 300;
    /** Healing: 5% per second. / 恢复速度：每秒 5%。 */
    public static final int REGEN_PER_TICK = 25;
    /** Tablet hack: 15 s, restarts from zero when interrupted. / 平板入侵：15 秒，中断后从零开始。 */
    public static final int HACK_TICKS = 300;
    /** Master Saboteur tablet hack: 10 s. / 破坏大师的平板入侵：10 秒。 */
    public static final int HACK_TICKS_MASTER = 200;
    /** A freshly unlocked sabotage can be used after 90 s. / 刚解锁的破坏 90 秒后才能使用。 */
    public static final int UNLOCK_DELAY_TICKS = 1_800;
    /** Shared team cooldown, counted from the moment the lights come back: 2.5 min. / 团队共享冷却，从来电时起算：2.5 分钟。 */
    public static final int TEAM_COOLDOWN_TICKS = 3_000;
    /** Team cooldown after the Engineer's power restoration: 90 s. / 工程师电力恢复后的团队冷却：90 秒。 */
    public static final int POWER_RESTORATION_COOLDOWN_TICKS = 1_800;

    public static final double CHANNEL_RANGE = 4.5;
    /** Vanilla re-sends a held block use every 4 ticks; a longer gap means the button was released. / 原版按住使用键每 4 tick 重发一次；间隔更久说明已松手。 */
    public static final int HEARTBEAT_TIMEOUT_TICKS = 10;
    public static final int PRY_SOUND_INTERVAL_TICKS = 20;
    public static final int HACK_SOUND_INTERVAL_TICKS = 10;
    public static final int SWING_INTERVAL_TICKS = 8;
    /** Vanilla forgets block-breaking cracks after 400 ticks without an update. / 原版 400 tick 未更新就会清掉方块裂纹。 */
    public static final int CRACK_REFRESH_TICKS = 200;
    public static final int PROGRESS_RESYNC_TICKS = 20;

    /** Safety cap of a repair-mode outage; normal rounds never reach it. / 维修模式熄灯的安全上限，正常对局碰不到。 */
    public static final int OUTAGE_CAP_TICKS = 72_000;
    public static final int RESTORE_STAGGER_MIN_TICKS = 5;
    public static final int RESTORE_STAGGER_MAX_TICKS = 40;

    public static final int REPAIR_SWITCHES_MIN = 3;
    public static final int REPAIR_SWITCHES_MAX = 5;
    public static final int REPAIR_SWITCH_SLOTS = 8;
    public static final int MASTER_EXTRA_SWITCHES = 2;
    public static final int REPAIR_HOLD_TICKS = 40;
    public static final int MASTER_EXTRA_HOLD_TICKS = 20;
    /** Server-side minimum per tripped switch, so a forged completion cannot be instant. / 每个跳闸开关的服务端最短用时，防止伪造的瞬间完成。 */
    public static final int REPAIR_TICKS_PER_SWITCH = 6;
    public static final int REPAIR_SESSION_TIMEOUT_TICKS = 1_200;
    public static final double REPAIR_RANGE = 6.0;

    /**
     * Wait for the train copy this long (and for the play area to be loaded) before initializing from the world as it
     * is, for maps whose copy never succeeds.
     * 等待车厢复制这么久（且对局区已加载）仍未完成时，直接按当前世界初始化，用于复制始终不成功的地图。
     */
    public static final int MAP_READY_FALLBACK_TICKS = 200;
    public static final int LOG_LIMIT = 30;

    private SabotageRules() {
    }

    public static int pryPerTick(boolean masterSaboteur) {
        return masterSaboteur ? PRY_PER_TICK_MASTER : PRY_PER_TICK;
    }

    public static int hackTicks(boolean masterSaboteur) {
        return masterSaboteur ? HACK_TICKS_MASTER : HACK_TICKS;
    }

    public static int pry(int durability, int perTick) {
        return Math.max(0, durability - Math.max(0, perTick));
    }

    /**
     * One tick of healing: only a closed, damaged box that has not been hit for {@link #REGEN_DELAY_TICKS} heals; a
     * box at 0% is pried open and stays that way until an Engineer repairs it.
     * 一 tick 的恢复：只有未打开、已受损且 REGEN_DELAY_TICKS 内没被破坏的箱子会恢复；耐久归零的箱子已被撬开，
     * 在工程师修好之前保持原样。
     */
    public static int regen(int durability, long ticksSinceDamage, boolean open) {
        if (open || durability <= 0 || durability >= DURABILITY_MAX || ticksSinceDamage < REGEN_DELAY_TICKS) {
            return durability;
        }
        return Math.min(DURABILITY_MAX, durability + REGEN_PER_TICK);
    }

    /** Vanilla crack stage 0-9, or -1 for none (intact or already open). / 原版裂纹阶段 0-9，-1 表示无裂纹（完好或已打开）。 */
    public static int crackStage(int durability, boolean open) {
        if (open || durability >= DURABILITY_MAX) {
            return -1;
        }
        int damage = DURABILITY_MAX - Math.max(0, durability);
        return Math.max(0, Math.min(9, damage * 10 / DURABILITY_MAX));
    }

    /** Whole percent for display, rounded up so 0% only shows once the box is really open. / 显示用的整数百分比，向上取整，只有真正打开时才显示 0%。 */
    public static int percent(int durability) {
        if (durability <= 0) {
            return 0;
        }
        return Math.min(100, (durability + 99) / 100);
    }

    public enum TriggerCheck {
        OK,
        /** No junction box of this type has been hacked yet. / 还没有入侵过这一类接线箱。 */
        LOCKED,
        /** Unlocked less than 90 s ago. / 解锁还不到 90 秒。 */
        ARMING,
        /** The shared team cooldown is running. / 团队共享冷却中。 */
        COOLDOWN,
        /** Another sabotage is in progress. / 另一个破坏正在进行。 */
        ACTIVE
    }

    public static TriggerCheck canTrigger(boolean unlocked, long readyTick, long cooldownEndTick, boolean outageActive,
                                          long now) {
        if (!unlocked) {
            return TriggerCheck.LOCKED;
        }
        if (outageActive) {
            return TriggerCheck.ACTIVE;
        }
        if (now < readyTick) {
            return TriggerCheck.ARMING;
        }
        if (now < cooldownEndTick) {
            return TriggerCheck.COOLDOWN;
        }
        return TriggerCheck.OK;
    }

    /** Repair needs a breaker; without one the lights come back on Wathe's own timer. / 维修需要电闸；没有电闸时按 Wathe 原有计时来电。 */
    public static OutageMode outageMode(boolean mapHasBreakers) {
        return mapHasBreakers ? OutageMode.REPAIR : OutageMode.TIMED;
    }

    /** Maps without junction boxes (made before this system) start with lights unlocked. / 没有接线箱的地图（此系统之前做的）开局就解锁灯光。 */
    public static boolean startsWithLightsUnlocked(boolean mapHasJunctionBoxes) {
        return !mapHasJunctionBoxes;
    }

    public static int repairSwitches(int roll, boolean masterSaboteur) {
        int spread = REPAIR_SWITCHES_MAX - REPAIR_SWITCHES_MIN + 1;
        int base = REPAIR_SWITCHES_MIN + Math.floorMod(roll, spread);
        return Math.min(REPAIR_SWITCH_SLOTS, base + (masterSaboteur ? MASTER_EXTRA_SWITCHES : 0));
    }

    public static int repairHoldTicks(boolean masterSaboteur) {
        return REPAIR_HOLD_TICKS + (masterSaboteur ? MASTER_EXTRA_HOLD_TICKS : 0);
    }

    public static int repairMinTicks(int switches, int holdTicks) {
        return Math.max(0, holdTicks) + Math.max(0, switches) * REPAIR_TICKS_PER_SWITCH;
    }

    /** Lamps nearer the repaired breaker come back first. / 离修好的电闸越近的灯越先亮。 */
    public static int restoreDelay(double distance, double farthest) {
        if (farthest <= 0.0 || distance <= 0.0) {
            return RESTORE_STAGGER_MIN_TICKS;
        }
        double t = Math.min(1.0, distance / farthest);
        return RESTORE_STAGGER_MIN_TICKS
                + (int) Math.round(t * (RESTORE_STAGGER_MAX_TICKS - RESTORE_STAGGER_MIN_TICKS));
    }
}
