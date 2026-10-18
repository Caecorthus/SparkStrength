package annina.sparkstrength.role.veteran;

import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 老兵加强的纯规则集中处。
 *
 * <p>这里不碰世界、玩家、背包等运行态，只保存稳定 ID 和可以单元测试的判定。
 * 后续如果要调价格、次数或奖励，优先改这个类。</p>
 */
public final class VeteranRules {
    public static final Identifier VETERAN_ID = Identifier.of("wathe", "veteran");
    public static final String KNIFE_ENTRY_ID = "sparkstrength_veteran_knife";

    /** 老兵商店中每把匕首的价格。 */
    public static final int KNIFE_PRICE = 300;
    /** 你已确认：每把老兵匕首默认拥有 2 次刺杀次数。 */
    public static final int STAB_USES_PER_KNIFE = 2;
    /** 刀到非好人阵营角色时获得的金币。 */
    public static final int NON_INNOCENT_KILL_REWARD = 100;
    /** 刀到好人阵营角色时获得的金币。 */
    public static final int INNOCENT_KILL_REWARD = 0;

    private VeteranRules() {
    }

    public static boolean isVeteran(@Nullable Role role) {
        return role != null && VETERAN_ID.equals(role.identifier());
    }

    public static int killRewardForVictim(@Nullable Role victimRole) {
        // 旧签名保留给潜在外部调用：没有玩家上下文时，仍按原始角色阵营结算。
        return victimRole != null && victimRole.isInnocent()
                ? INNOCENT_KILL_REWARD
                : NON_INNOCENT_KILL_REWARD;
    }

    public static int killRewardForVictim(@Nullable Role victimRole, @Nullable PlayerEntity victim) {
        // 这里专门用于老兵击杀结算：善良词条仍按“好人”处理并不给奖励，
        // 内鬼词条则按杀手阵营处理，允许正常击杀并给出非好人奖励。
        return isEffectiveCivilian(victimRole, victim)
                ? INNOCENT_KILL_REWARD
                : NON_INNOCENT_KILL_REWARD;
    }

    public static boolean isEffectiveCivilian(@Nullable Role role, @Nullable PlayerEntity player) {
        return SparkTraitsCompat.isEffectiveCivilian(role, player);
    }

    public static boolean shouldRemoveKnifeAfterUse(int remainingUses) {
        // 总次数按“每把刀 2 次”累计，因此每消耗到 2 的倍数边界就移除一把实体刀。
        return remainingUses >= 0 && remainingUses % STAB_USES_PER_KNIFE == 0;
    }
}
