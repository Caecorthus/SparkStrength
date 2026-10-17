package annina.sparkstrength.record;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the blackout record events (S8 blackout_state, S9 flashlight_blackout) from one shared per-world state, so
 * each real edge is recorded once by whichever caller sees it first: the shop purchase (start, with the buyer) and the
 * Engineer's power restoration (end, with the buyer) record at the moment they act; the world tick records whatever
 * is left (the blackout running out, or a start from an unknown source). Server thread only. A recording failure is
 * logged and swallowed.
 * 用同一份按世界的状态写入停电记录事件（S8 blackout_state、S9 flashlight_blackout），每个真实边沿只由最先看到它的调用方记录一次：
 * 商店购买（开始，带购买者）与工程师恢复电力（结束，带购买者）在发生的当下记录；世界 tick 记录剩下的边沿（停电时间耗尽，
 * 或来源未知的开始）。仅限服务端线程。记录失败只写日志并吞掉异常。
 */
public final class BlackoutRecorder {
    private static final BlackoutRecordState<RegistryKey<World>> STATE = new BlackoutRecordState<>();

    private BlackoutRecorder() {
    }

    /** World tick: the current Wathe blackout state, with no known cause. / 世界 tick：Wathe 当前停电状态，原因未知。 */
    public static void tick(ServerWorld world, boolean blackoutActive) {
        edge(world, blackoutActive, null, blackoutActive
                ? AchievementRecords.BLACKOUT_CAUSE_UNKNOWN
                : AchievementRecords.BLACKOUT_CAUSE_EXPIRED);
    }

    /**
     * Wathe's blackout shop entry ({@code PlayerShopComponent.useBlackout}) returned success. It also succeeds on a map
     * without lamps, where no blackout starts and nothing is recorded.
     * Wathe 商店停电（useBlackout）返回成功。没有灯的地图上它同样成功，但不会停电，也不记录。
     */
    public static void purchased(ServerPlayerEntity buyer) {
        try {
            ServerWorld world = buyer.getServerWorld();
            boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
            edge(world, active, active ? buyer : null, active
                    ? AchievementRecords.BLACKOUT_CAUSE_PURCHASE
                    : AchievementRecords.BLACKOUT_CAUSE_EXPIRED);
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the blackout purchase", error);
        }
    }

    /** {@code initiator} just triggered the lights sabotage from the tablet. / 该玩家刚在平板上触发了灯光破坏。 */
    public static void sabotaged(ServerPlayerEntity initiator) {
        try {
            ServerWorld world = initiator.getServerWorld();
            boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
            edge(world, active, active ? initiator : null, active
                    ? AchievementRecords.BLACKOUT_CAUSE_SABOTAGE
                    : AchievementRecords.BLACKOUT_CAUSE_EXPIRED);
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the lights sabotage", error);
        }
    }

    /**
     * {@code repairer} just repaired a circuit breaker; Wathe's world timer was zeroed, so the blackout reads as over.
     * 该玩家刚修好电闸；Wathe 的世界计时已清零，因此停电已视为结束。
     */
    public static void breakerRepaired(ServerPlayerEntity repairer) {
        try {
            ServerWorld world = repairer.getServerWorld();
            boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
            edge(world, active, active ? null : repairer, active
                    ? AchievementRecords.BLACKOUT_CAUSE_UNKNOWN
                    : AchievementRecords.BLACKOUT_CAUSE_BREAKER_REPAIR);
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the breaker repair", error);
        }
    }

    /** The power restoration bought by {@code buyer} just reset the blackout. / 该购买者买下的电力恢复刚刚重置了停电。 */
    public static void restored(ServerPlayerEntity buyer) {
        try {
            ServerWorld world = buyer.getServerWorld();
            boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
            edge(world, active, active ? null : buyer, active
                    ? AchievementRecords.BLACKOUT_CAUSE_UNKNOWN
                    : AchievementRecords.BLACKOUT_CAUSE_POWER_RESTORATION);
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the power restoration", error);
        }
    }

    /**
     * A lit flashlight cancelled {@code holder}'s blackout blindness ({@code remainingTicks} of the blackout left);
     * recorded once per blackout per player. Wathe asks every tick, and the first ask can come inside the purchase,
     * before the start edge.
     * 开启的手电筒挡下了持有者的停电失明（停电还剩 remainingTicks 个 tick）；每次停电每名玩家只记一次。Wathe 每 tick 都会询问，
     * 首次询问可能发生在购买过程中、开始边沿之前。
     */
    public static void flashlightCancelled(ServerPlayerEntity holder, int remainingTicks) {
        try {
            if (STATE.firstFlashlight(holder.getServerWorld().getRegistryKey(), holder.getUuid())) {
                AchievementRecords.flashlightBlackout(holder, remainingTicks);
            }
        } catch (RuntimeException error) {
            SparkStrength.LOGGER.warn("Could not record the flashlight blackout", error);
        }
    }

    /** Round end: forget the world's blackout without recording an edge. / 回合结束：忘掉该世界的停电，不记录边沿。 */
    public static void clear(ServerWorld world) {
        STATE.forget(world.getRegistryKey());
    }

    private static void edge(ServerWorld world, boolean blackoutActive, @Nullable ServerPlayerEntity actor, String cause) {
        if (STATE.observe(world.getRegistryKey(), blackoutActive)) {
            AchievementRecords.blackoutState(world, blackoutActive, actor, cause);
        }
    }
}
