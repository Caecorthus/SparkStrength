package annina.sparkstrength.role;

import annina.sparkstrength.util.SparkPlayers;
import dev.doctor4t.wathe.api.event.PlayerPoisoned;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One detector for "a player was newly poisoned", shared by every role that reacts to it. Hooked on Wathe's
 * {@link PlayerPoisoned} event rather than on each poison item, so every source that ends in {@code setPoisonTicks(...)}
 * counts, including ones added later.
 * 统一的“玩家新中毒”检测器，供所有对此作出反应的职业共用。挂在 Wathe 的 PlayerPoisoned 事件上，而不是分别改各个毒药道具，
 * 因此所有最终走 setPoisonTicks(...) 的投毒来源都能生效，也不会漏掉后续新增的入口。
 *
 * <p>A new poisoning: the target is a server player alive in the round with no active poison before this call, and
 * Wathe accepted the poison (AFTER fired in the same world tick as BEFORE). Refreshing or speeding up an existing
 * poison does not count.
 * 新中毒：目标是在对局中存活的服务端玩家，本次调用前没有生效中的毒，且 Wathe 接受了这次投毒（AFTER 与 BEFORE 在同一世界刻触发）。
 * 刷新或加速已有的毒不算。</p>
 */
public final class PoisonOnset {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static boolean registered;

    private PoisonOnset() {
    }

    @FunctionalInterface
    public interface Listener {
        /**
         * @param poisoner this call's poisoner; null when it has none, or when it is not the poisoner BEFORE recorded
         *                 / 本次投毒者；没有投毒者，或与 BEFORE 记录的投毒者不同时为 null
         */
        void onNewPoisoning(ServerPlayerEntity target, @Nullable UUID poisoner);
    }

    /** Listeners run in subscription order. / 监听器按订阅顺序执行。 */
    public static synchronized void subscribe(Listener listener) {
        if (!registered) {
            registered = true;
            PlayerPoisoned.BEFORE.register(PoisonOnset::beforePlayerPoisoned);
            PlayerPoisoned.AFTER.register(PoisonOnset::afterPlayerPoisoned);
        }
        LISTENERS.add(listener);
    }

    private static @Nullable PlayerPoisoned.PoisonResult beforePlayerPoisoned(
            PlayerEntity target,
            int ticks,
            UUID poisoner
    ) {
        // Every attempt drops the target's old record, so a cancelled attempt never leaks into the next one.
        // 每次进入投毒流程都先移除目标的旧记录，避免“上一次被取消的投毒”污染下一次判断。
        PENDING.remove(target.getUuid());

        if (!(target instanceof ServerPlayerEntity) || ticks <= 0 || !SparkPlayers.isLivingParticipant(target)) {
            return null;
        }

        if (PlayerPoisonComponent.KEY.get(target).poisonTicks > 0) {
            return null;
        }

        // BEFORE and AFTER fire back to back inside one setPoisonTicks call; worldTime and poisoner let AFTER confirm
        // that this record belongs to the same call. / BEFORE 与 AFTER 在同一次 setPoisonTicks 调用中紧挨着触发；
        // 记录 worldTime 与 poisoner，用于 AFTER 阶段确认这条记录确实属于同一次投毒。
        PENDING.put(target.getUuid(), new Pending(poisoner, target.getWorld().getTime()));
        return null;
    }

    private static void afterPlayerPoisoned(PlayerEntity target, int ticks, UUID poisoner) {
        if (!(target instanceof ServerPlayerEntity serverTarget) || ticks <= 0) {
            return;
        }

        Pending pending = PENDING.remove(target.getUuid());
        if (pending == null || pending.worldTime() != target.getWorld().getTime()) {
            return;
        }

        // AFTER means the poison was written; the target must still be alive in the round.
        // AFTER 已经代表毒状态成功写入；这里再检查一次目标仍处于有效游戏存活状态，防止边界事件误发。
        if (!SparkPlayers.isLivingParticipant(target)) {
            return;
        }

        UUID confirmedPoisoner = Objects.equals(pending.poisoner(), poisoner) ? poisoner : null;
        for (Listener listener : LISTENERS) {
            listener.onNewPoisoning(serverTarget, confirmedPoisoner);
        }
    }

    private record Pending(@Nullable UUID poisoner, long worldTime) {
    }
}
