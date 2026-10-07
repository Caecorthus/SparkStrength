package annina.sparkstrength.role.veteran;

/**
 * Pure knife "小脑" (death reason {@code wathe:shot_innocent}) rule for the real Veteran's knife. A Coroner's
 * borrowed knife (any corpse disguise) is never punished.
 * 真老兵匕首的“小脑”（死因 wathe:shot_innocent）纯规则；验尸官借刀（任何尸体伪装）都不会小脑。
 *
 * <p>It mirrors the revolver: the victim side follows SparkTraits {@code EffectiveAlignment.isEffectiveCivilian}
 * (Conscience counts as innocent, Impostor does not, otherwise the raw Wathe role decides), and an Impostor attacker
 * is exempt as in {@code EffectiveGunRules} / {@code EffectiveDeathConsequenceRules}. Without SparkTraits every trait
 * flag is false, so the result equals the raw {@code game.isInnocent(target)} check.
 * 与左轮一致：目标按 SparkTraits 有效阵营判定（善良算好人、内鬼不算好人，其余沿用 Wathe 原始身份），内鬼攻击者免罚。
 * 未安装 SparkTraits 时所有天赋标记为 false，结果等同原先的 game.isInnocent(target)。</p>
 *
 * <p>Only {@code java.*} may be used here so the standalone test compiles this file with plain javac.
 * 这里只能依赖 java.*，独立测试可以直接用 javac 编译，不需要 Minecraft/Wathe/Gradle。</p>
 */
public final class InnocentKnifeKillRules {
    private InnocentKnifeKillRules() {
    }

    /**
     * Alignment facts that must be read BEFORE {@code GameFunctions.killPlayer}: SparkTraits clears the victim's
     * active traits inside its KillPlayer.AFTER listener, so a read after the kill misjudges Impostor/Conscience.
     * 必须在 killPlayer 之前读取的阵营事实：SparkTraits 会在 KillPlayer.AFTER 中清空死者天赋，事后读取会误判内鬼/善良。
     */
    public record PreKill(boolean victimEffectiveInnocent, boolean attackerImpostor) {
    }

    public static PreKill beforeKill(
            boolean victimRoleInnocent,
            boolean victimImpostor,
            boolean victimConscience,
            boolean attackerImpostor
    ) {
        return new PreKill(
                isEffectiveInnocentVictim(victimRoleInnocent, victimImpostor, victimConscience),
                attackerImpostor
        );
    }

    public static boolean isEffectiveInnocentVictim(
            boolean victimRoleInnocent,
            boolean victimImpostor,
            boolean victimConscience
    ) {
        if (victimConscience) {
            return true;
        }
        if (victimImpostor) {
            return false;
        }
        return victimRoleInnocent;
    }

    /**
     * {@code victimDead} and {@code attackerAlive} are read after the stab's kill returns, so a death absorbed by
     * psycho armour or cancelled in KillPlayer.BEFORE never punishes, and an attacker already punished elsewhere is
     * never killed twice. A null snapshot means the rule does not apply.
     * victimDead / attackerAlive 在刺杀击杀返回后读取：死亡被疯魔盾吸收或被 BEFORE 取消时不罚，
     * 攻击者已被其它逻辑处死时不会重复击杀；快照为 null 表示本规则不适用。
     */
    public static boolean shouldPunish(PreKill preKill, boolean victimDead, boolean attackerAlive) {
        return preKill != null
                && victimDead
                && attackerAlive
                && !preKill.attackerImpostor()
                && preKill.victimEffectiveInnocent();
    }
}
