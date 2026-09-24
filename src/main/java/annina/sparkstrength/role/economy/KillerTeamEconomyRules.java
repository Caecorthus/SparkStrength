package annina.sparkstrength.role.economy;

public final class KillerTeamEconomyRules {
    private KillerTeamEconomyRules() {
    }

    public static boolean isEnabled(boolean initialized, int openingMembers) {
        return initialized && openingMembers >= 2;
    }

    public static boolean isGenuineKiller(boolean killerRole, boolean impostor, boolean conscience) {
        return !conscience && (killerRole || impostor);
    }

    // Each receipt is rounded independently; no discarded remainder carries into another receipt.
    // 每笔实收独立向下取整，舍弃的余数不会累计到下一笔。
    public static int contribution(long actualIncome, int openingMembers) {
        return contribution(actualIncome, openingMembers, false);
    }

    public static int contribution(long actualIncome, int openingMembers, boolean teamFirst) {
        if (actualIncome <= 0 || openingMembers < 2) {
            return 0;
        }
        if (!teamFirst) {
            return (int) Math.min(Integer.MAX_VALUE, actualIncome / openingMembers);
        }
        // Quotient/remainder preserves one final floor without overflowing income * 6.
        // 商余分解避免实收乘 6 溢出，保持整笔贡献只向下取整一次；分母至少为 10。
        long denominator = (long) openingMembers * 5;
        long contribution = (actualIncome / denominator) * 6
                + ((actualIncome % denominator) * 6) / denominator;
        return (int) Math.min(Integer.MAX_VALUE, contribution);
    }

    public static int availableBalance(int personalBalance, int sharedBalance) {
        return (int) Math.min(Integer.MAX_VALUE, (long) personalBalance + sharedBalance);
    }

    public static int sharedShortfall(int personalBalance, int price) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) price - personalBalance));
    }
}
