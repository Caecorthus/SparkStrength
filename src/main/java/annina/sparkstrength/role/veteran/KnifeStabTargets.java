package annina.sparkstrength.role.veteran;

import annina.sparkstrength.compat.SparkWitchCompat;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server victim of a Wathe knife stab payload that a SparkStrength HEAD takeover of {@code KnifeStabPayload$Receiver}
 * handles itself (the Veteran knife, the Coroner's instant silent knife, the Silencer knife). Those takeovers cancel
 * Wathe's receiver, so SparkWitch's own crow lookup never runs for them: a SparkWitch Raven Form crow id is resolved here
 * to its owner through {@link SparkWitchCompat#crowMeleeStandIn} (owner decision 2026-10-21: every weapon that kills a
 * player kills the crow, and that kills the Black Raven), and the takeover then runs its own player path on that owner,
 * keeping its own knife rules (the Veteran's uses, no sound, no cooldown and innocent-kill 小脑; the Silencer's quiet
 * stab and cooldown). Without SparkWitch, or with an older one, a non-player id stays a dead stab as before.
 * 由 SparkStrength 在 {@code KnifeStabPayload$Receiver} HEAD 自行接管的 Wathe 刀刺数据包（老兵匕首、验尸官无声瞬刺、沉默者
 * 匕首）在服务端的受害者。这些接管会取消 Wathe 接收器，因此 SparkWitch 自身的乌鸦查找不会为它们执行：SparkWitch 化鸦乌鸦 id
 * 在此经 {@link SparkWitchCompat#crowMeleeStandIn} 解析为其主人（所有者 2026-10-21 决定：能杀死玩家的武器都能杀死乌鸦，且由此
 * 杀死黑羽鸦），接管随后对该主人执行自身的玩家路径，保留各自的刀规则（老兵的次数、无声、无冷却与误杀小脑；沉默者的静音刺击与
 * 冷却）。未安装或版本较旧的 SparkWitch 下，非玩家 id 与以往一样整刀无效。
 */
public final class KnifeStabTargets {
    /**
     * Wathe knife receiver's own server reach on a player ({@code distanceTo} above 3 refuses); on a crow SparkWitch adds
     * its 0.5 latency tolerance from the eye, exactly Wathe's knife on the crow.
     * Wathe 刀接收器对玩家的服务端距离（{@code distanceTo} 大于 3 即拒绝）；对乌鸦时 SparkWitch 自眼睛起追加 0.5 延迟容差，
     * 与 Wathe 刀刺乌鸦完全一致。
     */
    public static final double STAB_REACH = 3.0;

    private KnifeStabTargets() {
    }

    /**
     * The stabbed player: a non-spectator player within {@link #STAB_REACH}, or the Black Raven behind a crow id
     * SparkWitch accepts (owner substitution armed; the caller skips its body-distance check because the crow was
     * measured). Null when nobody can be stabbed.
     * 被刺的玩家：{@link #STAB_REACH} 内的非旁观玩家，或 SparkWitch 接受的乌鸦 id 背后的黑羽鸦（已装入主人替身；乌鸦已被量过
     * 距离，调用方不再检查本体距离）。无人可刺时为 null。
     */
    public static @Nullable ServerPlayerEntity resolve(ServerPlayerEntity attacker, int targetId) {
        Entity entity = attacker.getServerWorld().getEntityById(targetId);
        if (entity instanceof ServerPlayerEntity target) {
            return target.isSpectator() || target.distanceTo(attacker) > STAB_REACH ? null : target;
        }
        return SparkWitchCompat.crowMeleeStandIn(attacker, entity, STAB_REACH);
    }
}
