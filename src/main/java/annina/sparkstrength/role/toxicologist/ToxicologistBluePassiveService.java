package annina.sparkstrength.role.toxicologist;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkTraitsBluePoisonCompat;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Toxicologist blue-state passive: inside SparkTraits' blue sanity-drain window a Toxicologist (or a Coroner disguised
 * as one) skips the drain, regains sanity instead and has Speed II. Server-authoritative; mood syncs through Wathe and
 * the effect through vanilla status-effect packets.
 * 毒理学家蓝毒被动：处于 SparkTraits 蓝毒扣理智窗口时，毒理学家（或伪装成毒理学家的验尸官）不再扣理智，
 * 改为回复理智并获得速度 II。服务端权威；理智经 Wathe 同步，效果经原版状态效果包同步。
 *
 * <p>Tick order: CCA ticks the player's SparkTraits component (decrement, then drain or skip) inside the world's
 * entity loop, before END_WORLD_TICK where {@link #tick} runs. A food, bed or Blue Belladonna window of N ticks is
 * opened outside that loop, so SparkTraits consumes it on the next N ticks while we read N-1..0 remaining; the last
 * tick is only visible through the exemption heartbeat, giving exactly N regen ticks (100 ticks = +50 sanity), or
 * N-1 if SparkTraits never consults our exemption. Conscience blue gas renews a 1-tick window from the gas entity's
 * tick, so depending on entity order we read 1 or 0; the heartbeat covers the 0 case, and the 1 case can add one
 * extra regen tick (+0.5 sanity) on the tick a player steps into gas.
 * Tick 顺序：CCA 在世界实体循环内先跑玩家的 SparkTraits 组件（自减，再扣理智或跳过），之后才是本类所在的 END_WORLD_TICK。
 * 食物、床或蓝颠茄开出的 N tick 窗口在该循环之外写入，SparkTraits 在之后 N 个 tick 消耗它，我们读到的剩余为 N-1..0；
 * 最后一个 tick 只能靠豁免心跳看到，因此正好回复 N 个 tick（100 tick = +50 理智）；若 SparkTraits 从不询问豁免则为 N-1。
 * 良心蓝毒气在毒气实体 tick 中续 1 tick 窗口，按实体顺序我们会读到 1 或 0；读到 0 时靠心跳，读到 1 时踏入毒气那一刻
 * 可能多回复 1 个 tick（+0.5 理智）。</p>
 */
public final class ToxicologistBluePassiveService {
    private static final String HIDDEN_EFFECT_NBT_KEY = "hidden_effect";
    private static final Map<UUID, OwnedSpeed> OWNED_SPEED = new HashMap<>();
    private static final Set<UUID> DRAIN_SKIPPED_THIS_TICK = new HashSet<>();
    private static boolean registered;
    private static boolean supported;

    private ToxicologistBluePassiveService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        supported = SparkTraitsBluePoisonCompat.registerBlueSanityDrainExemption(
                ToxicologistBluePassiveService::exemptFromBlueSanityDrain);
        if (!supported) {
            SparkStrength.LOGGER.info("Toxicologist blue-state passive disabled: SparkTraits blue sanity-drain exemption API is unavailable.");
        }
    }

    public static void tick(ServerWorld world) {
        if (!supported) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            boolean drainSkipped = DRAIN_SKIPPED_THIS_TICK.remove(player.getUuid());
            boolean active = isEligible(player) && ToxicologistBlueRules.isBlueStateActive(
                    SparkTraitsBluePoisonCompat.getBlueSanityDrainTicks(player), drainSkipped);
            if (active) {
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
                mood.setMood(ToxicologistBlueRules.regeneratedMood(mood.getMood()));
                refreshOwnedSpeed(player);
            } else {
                releaseOwnedSpeed(player);
            }
        }
        // Heartbeats are recorded during this world's entity loop and consumed above; never carry them to another tick.
        // 心跳在本世界实体循环中记录、在上面消费；不得带入其它 tick。
        DRAIN_SKIPPED_THIS_TICK.clear();
    }

    /** Round end, death or reset: drop only our own Speed and forget the player.
     *  对局结束、死亡或重置：只移除自己给的速度并清除该玩家记录。 */
    public static void clearPlayer(ServerPlayerEntity player) {
        DRAIN_SKIPPED_THIS_TICK.remove(player.getUuid());
        releaseOwnedSpeed(player);
    }

    /**
     * SparkTraits exemption predicate. SparkTraits consults it, on the server thread inside the player's component
     * tick, only when it is about to apply a blue drain tick, so returning true also records a skipped-drain heartbeat
     * that {@link #tick} turns into regen. Exempt players keep their window ticking down in SparkTraits.
     * SparkTraits 豁免谓词：仅在服务端线程、玩家组件 tick 中即将扣一次蓝毒理智时询问，因此返回 true 的同时记录一次
     * “已跳过扣理智”心跳，由 {@link #tick} 转成回复。被豁免玩家的窗口在 SparkTraits 中照常倒计时。
     */
    private static boolean exemptFromBlueSanityDrain(ServerPlayerEntity player) {
        if (!isEligible(player)) {
            return false;
        }
        DRAIN_SKIPPED_THIS_TICK.add(player.getUuid());
        return true;
    }

    private static boolean isEligible(ServerPlayerEntity player) {
        return ToxicologistBlueRules.isPassiveEligible(
                ToxicologistBlueRules.isToxicologistLike(player),
                GameFunctions.isPlayerPlayingAndAlive(player),
                GameFunctions.isPlayerSpectatingOrCreative(player));
    }

    private static void refreshOwnedSpeed(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        long now = player.getServerWorld().getTime();
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        OwnedSpeed remembered = OWNED_SPEED.get(uuid);
        boolean owned = isOwned(speed, remembered, now);
        ToxicologistBlueRules.PassiveSpeedAction action = ToxicologistBlueRules.passiveSpeedAction(
                speed != null, speed == null ? 0 : speed.getAmplifier(), speed == null ? 0 : speed.getDuration(), owned);
        switch (action) {
            case KEEP -> {
            }
            case YIELD -> OWNED_SPEED.remove(uuid);
            case APPLY -> {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED,
                        ToxicologistBlueRules.PASSIVE_SPEED_TICKS,
                        ToxicologistBlueRules.SPEED_AMPLIFIER,
                        false,
                        false,
                        true
                ));
                OWNED_SPEED.put(uuid, new OwnedSpeed(ToxicologistBlueRules.PASSIVE_SPEED_TICKS, now));
            }
        }
    }

    private static void releaseOwnedSpeed(ServerPlayerEntity player) {
        OwnedSpeed remembered = OWNED_SPEED.remove(player.getUuid());
        if (remembered == null) {
            return;
        }
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        boolean owned = isOwned(speed, remembered, player.getServerWorld().getTime());
        if (ToxicologistBlueRules.shouldRemoveOwnedPassiveSpeed(owned, owned && hidesAnotherEffect(speed))) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    /**
     * Vanilla stores a weaker SPEED from another source as our instance's private hidden effect; its public NBT form
     * exposes it under "hidden_effect", which is the only accessor-free way to see it.
     * 原版把其它来源的更弱速度存成我们实例的私有隐藏效果；公开 NBT 形式以 "hidden_effect" 暴露它，这是无需 accessor 的唯一读取方式。
     */
    private static boolean hidesAnotherEffect(StatusEffectInstance effect) {
        return effect.writeNbt() instanceof NbtCompound nbt && nbt.contains(HIDDEN_EFFECT_NBT_KEY);
    }

    private static boolean isOwned(StatusEffectInstance speed, OwnedSpeed remembered, long worldTime) {
        return speed != null && remembered != null && ToxicologistBlueRules.isOwnedPassiveSpeed(
                speed.getAmplifier(), speed.getDuration(), remembered.durationTicks(), worldTime - remembered.worldTime());
    }

    private record OwnedSpeed(int durationTicks, long worldTime) {
    }
}
