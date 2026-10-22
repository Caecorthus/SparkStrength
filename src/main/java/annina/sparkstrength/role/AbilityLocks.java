package annina.sparkstrength.role;

import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.role.taotie.TaotieHeadDaze;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Named "this player can't act right now" locks. Each ability passes the exact set it honours; the sets differ on
 * purpose (which ability ignores which lock is a gameplay rule), so there is no default set.
 * 具名的“此刻不能行动”锁。每个能力传入它遵守的确切集合；各集合有意不同（哪个能力无视哪种锁属于玩法规则），因此没有默认集合。
 */
public final class AbilityLocks {
    /**
     * Engineer stun, SparkTraits role-skill block and a pending Last Stand: the Vulture's skateboard (refuses a ride
     * and ends one in progress) and Super Curse, and the Party Animal's summon.
     * 工程师定身、SparkTraits 职业技能封锁与待决的背水一战：秃鹫的滑板（拒绝上板并结束进行中的滑行）与超级骂，以及派对狂的召唤。
     */
    public static final Set<Lock> ROLE_SKILL = of(Lock.ENGINEER_STUN, Lock.ROLE_SKILL_BLOCK, Lock.LAST_STAND_PENDING);

    private static final Lock[] LOCKS = Lock.values();

    private AbilityLocks() {
    }

    /** Unmodifiable set of {@code locks}, for a caller's constant. / 由 {@code locks} 组成的不可变集合，供调用方作常量。 */
    public static Set<Lock> of(Lock first, Lock... rest) {
        return Collections.unmodifiableSet(EnumSet.of(first, rest));
    }

    /**
     * True when any lock in {@code locks} holds for {@code player}. Only the locks in the set are read.
     * {@code locks} 中任一锁对该玩家成立时为 true。只读取集合内的锁。
     */
    public static boolean isLocked(ServerPlayerEntity player, Set<Lock> locks) {
        for (Lock lock : LOCKS) {
            if (locks.contains(lock) && lock.holds.test(player)) {
                return true;
            }
        }
        return false;
    }

    public enum Lock {
        /** Engineer capture stun. / 工程师定身。 */
        ENGINEER_STUN(player -> EngineerStunnedPlayerComponent.KEY.get(player).isStunned()),
        /** SparkWitch Control Expert stun. / SparkWitch 控场专家眩晕。 */
        CONTROL_EXPERT_STUN(SparkWitchCompat::isControlExpertStunned),
        /** Taotie head daze. / 饕餮眩晕。 */
        TAOTIE_DAZE(TaotieHeadDaze::isDazed),
        /** Swallowed by the NoellesRoles Taotie. / 被 NoellesRoles 饕餮吞下。 */
        SWALLOWED(SwallowedPlayerComponent::isPlayerSwallowed),
        /** SparkTraits role-skill block (Last Escape, silenced killer ...). / SparkTraits 职业技能封锁（脱险、被沉默的杀手等）。 */
        ROLE_SKILL_BLOCK(SparkTraitsDroneCompat::isRoleSkillBlocked),
        /** SparkTraits killer-interaction block. / SparkTraits 杀手交互封锁。 */
        KILLER_INTERACTION_BLOCK(SparkTraitsCompat::isKillerInteractionBlocked),
        /** A pending SparkTraits Last Stand. / 待决的 SparkTraits 背水一战。 */
        LAST_STAND_PENDING(SparkTraitsDroneCompat::isLastStandPending);

        private final Predicate<ServerPlayerEntity> holds;

        Lock(Predicate<ServerPlayerEntity> holds) {
            this.holds = holds;
        }
    }
}
