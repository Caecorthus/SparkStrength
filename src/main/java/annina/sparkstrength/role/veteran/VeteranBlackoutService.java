package annina.sparkstrength.role.veteran;

import annina.sparkstrength.record.BlackoutRecorder;
import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.api.event.BlackoutEffect;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 停电期间的老兵相关服务端逻辑：给伪装成老兵的验尸官补发夜视，并为成就记录观察停电边沿。
 *
 * <p>停电状态与本能规则的客户端同步见 {@link annina.sparkstrength.sabotage.BlackoutSightService}。</p>
 */
public final class VeteranBlackoutService {
    private static final int CORONER_BLACKOUT_NIGHT_VISION_REFRESH_TICKS = 40;
    private static final int BLACKOUT_EFFECT_DURATION_TOLERANCE_TICKS = 5;
    private static final Map<UUID, RememberedCoronerVeteranNightVision> CORONER_VETERAN_NIGHT_VISION = new HashMap<>();
    private static boolean registered;

    private VeteranBlackoutService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlackoutEffect.BEFORE.register(VeteranBlackoutService::beforeBlackoutEffect);
        ServerWorldEvents.UNLOAD.register((server, world) -> BlackoutRecorder.clear(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BlackoutRecorder.clearAll());
    }

    public static void tick(ServerWorld world) {
        boolean active = WorldBlackoutComponent.KEY.get(world).isBlackoutActive();
        // Achievement record: edges the purchase or the power restoration did not already record (the blackout running out).
        // 成就记录：购买或电力恢复没有记下的边沿（停电时间耗尽）。
        BlackoutRecorder.tick(world, active);
        if (active) {
            refreshCoronerVeteranBlackoutSight(world);
        } else {
            clearCoronerVeteranBlackoutSight(world);
        }
    }

    public static void clear(ServerWorld world) {
        BlackoutRecorder.clear(world);
        clearCoronerVeteranBlackoutSight(world);
    }

    private static BlackoutEffect.BlackoutResult beforeBlackoutEffect(ServerPlayerEntity player, int durationTicks) {
        if (!shouldGiveCoronerVeteranBlackoutSight(player)) {
            return null;
        }

        /*
         * Wathe 只会把真实杀手和真实老兵放进夜视分支；验尸官真实职业仍会走“即将失明”事件。
         * 当验尸官当前借用老兵尸体身份时，这里取消本次 blackout 失明并补发同款夜视，
         * 让他在停电期间表现得像真正的老兵。切走伪装后，下面记录的夜视会被 tick 清掉。
         */
        removeCurrentBlackoutBlindness(player, durationTicks);
        applyCoronerVeteranNightVision(player, durationTicks);
        return BlackoutEffect.BlackoutResult.cancel();
    }

    private static void refreshCoronerVeteranBlackoutSight(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (shouldGiveCoronerVeteranBlackoutSight(player)) {
                /*
                 * 兜底刷新：如果金酒、手电筒等更早的监听器先取消了失明，Wathe 不会继续调用这里的
                 * BlackoutEffect 监听器；因此在世界 tick 里补一个短夜视，保证老兵伪装仍能看清停电。
                 * 已经由 beforeBlackoutEffect 发过更长停电夜视时，apply 方法会保留那份更精确的时长记录。
                 */
                applyCoronerVeteranNightVision(player, CORONER_BLACKOUT_NIGHT_VISION_REFRESH_TICKS);
            } else {
                clearRememberedCoronerVeteranNightVision(player);
            }
        }
    }

    private static boolean shouldGiveCoronerVeteranBlackoutSight(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && CoronerService.hasVeteranDisguise(player);
    }

    private static void applyCoronerVeteranNightVision(ServerPlayerEntity player, int durationTicks) {
        StatusEffectInstance currentNightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (currentNightVision != null
                && currentNightVision.getDuration() > durationTicks + BLACKOUT_EFFECT_DURATION_TOLERANCE_TICKS) {
            /*
             * 玩家可能有金酒、鲛人等更长的夜视来源；短时兜底刷新不应该把那类效果认成验尸官停电夜视，
             * 否则切走伪装时可能误删其它职业或物品给的效果。
             */
            return;
        }

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                durationTicks,
                0,
                false,
                false,
                true
        ));
        CORONER_VETERAN_NIGHT_VISION.put(
                player.getUuid(),
                new RememberedCoronerVeteranNightVision(durationTicks, player.getServerWorld().getTime())
        );
    }

    private static void removeCurrentBlackoutBlindness(ServerPlayerEntity player, int durationTicks) {
        StatusEffectInstance blindness = player.getStatusEffect(StatusEffects.BLINDNESS);
        if (blindness != null && matchesDuration(blindness.getDuration(), durationTicks)) {
            player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }

    private static void clearCoronerVeteranBlackoutSight(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            clearRememberedCoronerVeteranNightVision(player);
        }
    }

    private static void clearRememberedCoronerVeteranNightVision(ServerPlayerEntity player) {
        RememberedCoronerVeteranNightVision remembered = CORONER_VETERAN_NIGHT_VISION.remove(player.getUuid());
        if (remembered == null) {
            return;
        }

        StatusEffectInstance nightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (nightVision != null && matchesRememberedNightVision(nightVision.getDuration(), remembered, player.getServerWorld().getTime())) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    private static boolean matchesRememberedNightVision(
            int currentDurationTicks,
            RememberedCoronerVeteranNightVision remembered,
            long worldTime
    ) {
        long elapsedTicks = Math.max(0, worldTime - remembered.worldTime());
        int expectedDurationTicks = Math.max(0, remembered.durationTicks() - (int) Math.min(Integer.MAX_VALUE, elapsedTicks));
        return matchesDuration(currentDurationTicks, expectedDurationTicks);
    }

    private static boolean matchesDuration(int currentDurationTicks, int expectedDurationTicks) {
        return Math.abs(currentDurationTicks - expectedDurationTicks) <= BLACKOUT_EFFECT_DURATION_TOLERANCE_TICKS;
    }

    private record RememberedCoronerVeteranNightVision(int durationTicks, long worldTime) {
    }
}
