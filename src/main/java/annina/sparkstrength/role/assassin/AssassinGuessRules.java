package annina.sparkstrength.role.assassin;

/**
 * Assassin buff (owner 2026-11-12): the first wrong guess of a round no longer kills the Assassin, and a correct guess
 * cools down for 3 s instead of NoellesRoles' 60 s. The forgiven miss still spends a charge and keeps the 60 s
 * cooldown, and nobody but the Assassin hears it (no gunshot). Everything else is NoellesRoles' own flow.
 * 刺客加强（负责人 2026-11-12）：每局第一次猜错不再反噬身亡，猜对后的冷却由 NoellesRoles 的 60 秒降为 3 秒。
 * 被宽恕的那次猜错照常消耗一次次数并进入 60 秒冷却，且除刺客本人外无人听见（没有枪声）。其余流程仍是 NoellesRoles 原逻辑。
 */
public final class AssassinGuessRules {
    /** Wrong guesses a round forgives before the misfire kills again. / 每局在反噬恢复致命前可被宽恕的猜错次数。 */
    public static final int FREE_MISSES_PER_ROUND = 1;
    /** Cooldown after a correct guess: 3 s. / 猜对后的冷却：3 秒。 */
    public static final int CORRECT_GUESS_COOLDOWN_TICKS = 3 * 20;

    private AssassinGuessRules() {
    }

    /** Whether a wrong guess is still forgiven after {@code forgivenMisses} forgiven ones this round.
     *  本局已宽恕 forgivenMisses 次后，再猜错是否仍被宽恕。 */
    public static boolean missIsForgiven(int forgivenMisses) {
        return forgivenMisses < FREE_MISSES_PER_ROUND;
    }

    /**
     * The cooldown to apply right after NoellesRoles' {@code useGuess()} set its own: a correct guess shortens it to
     * {@link #CORRECT_GUESS_COOLDOWN_TICKS}, never lengthening a shorter one; a wrong guess keeps what is there.
     * NoellesRoles 的 useGuess() 设好冷却后应改成的值：猜对时缩短为 3 秒（不会把更短的冷却拉长）；猜错保持原值。
     */
    public static int cooldownAfterGuess(boolean guessedCorrectly, int currentCooldownTicks) {
        return guessedCorrectly ? Math.min(currentCooldownTicks, CORRECT_GUESS_COOLDOWN_TICKS) : currentCooldownTicks;
    }
}
