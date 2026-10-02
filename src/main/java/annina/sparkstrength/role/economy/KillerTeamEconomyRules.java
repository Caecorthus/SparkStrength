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

    // A team needs two genuine killer-role players at the start; Impostors join an existing team but never form one.
    // 开局至少 2 名真杀手（不含内鬼、良心杀手）才成队；内鬼只加入已有团队，不能凑成团队。
    public static boolean formsTeam(int openingKillers) {
        return openingKillers >= 2;
    }

    // Team share is income / 2 / opening N; each receipt is rounded independently and no remainder carries over.
    // 团队份额为实收 / 2 / 开局 N；每笔实收独立向下取整，舍弃的余数不会累计到下一笔。
    public static int contribution(long actualIncome, int openingMembers) {
        return contribution(actualIncome, openingMembers, false);
    }

    public static int contribution(long actualIncome, int openingMembers, boolean teamFirst) {
        if (actualIncome <= 0 || openingMembers < 2) {
            return 0;
        }
        if (!teamFirst) {
            return (int) Math.min(Integer.MAX_VALUE, actualIncome / ((long) openingMembers * 2));
        }
        // Team First is +20% of the base share (income * 6 / 10N); quotient/remainder keeps one final floor without overflow.
        // 团队至上为基础份额 +20%（实收 * 6 / 10N）；商余分解避免溢出并保持只向下取整一次，分母至少为 20。
        long denominator = (long) openingMembers * 10;
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
