package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.mixin.partyanimal.PartygoerTaotieCooldownAccessor;
import annina.sparkstrength.network.partyanimal.PartygoerSwallowC2SPacket;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import annina.sparkstrength.role.taotie.TaotieHeadDaze;
import annina.sparkstrength.role.taotie.TaotieHeadRules;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server authority for the Taotie swallowing a Partygoer (owner rule 2026-10-21): validate the request
 * ({@link PartygoerTaotieRules#swallowDenial}), charge NoellesRoles' normal swallow cooldown, take the Partygoer in
 * (whoever it carried is dropped free), give the Taotie Slowness IV, and 5 s later let the Partygoer out where the Taotie
 * stands and kick the Taotie to death ({@link PartyAnimalRules#DEATH_REASON_KICKED}, forced, credited to the summoner).
 * The Partygoer comes out at once if the Taotie dies some other way, disconnects, or the round ends, so it is never left
 * stuck inside. Refusals are silent. Server thread only; the state is a plain map, never saved.
 * 饕餮吞派对客的服务端权威逻辑（主人规则 2026-10-21）：校验请求（{@link PartygoerTaotieRules#swallowDenial}），扣除
 * NoellesRoles 的正常吞噬冷却，吞下派对客（它扛着的人被放下、不会被吞），给饕餮缓慢 IV；5 秒后在饕餮所站位置放出派对客，
 * 并把饕餮一脚踢死（{@link PartyAnimalRules#DEATH_REASON_KICKED}，强制，记在召唤者头上）。饕餮因其他原因死亡、掉线或对局
 * 结束时派对客立刻出来，绝不会卡在肚子里。拒绝一律静默。仅限服务端线程；状态是普通映射，不存档。
 */
public final class PartygoerTaotieService {
    /** Taotie UUID → the Partygoer kicking inside it. / 饕餮 UUID → 在其肚里狂踢的派对客。 */
    private static final Map<UUID, Digestion> DIGESTING = new LinkedHashMap<>();
    private static boolean registered;

    private PartygoerTaotieService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(PartygoerTaotieService::tick);
        // Any death of the Taotie (another killer, poison, a fake death...) lets the Partygoer out at once. Our own kick
        // removes the entry before it calls killPlayer, so this never sees it.
        // 饕餮以任何方式死亡（别的凶手、毒、假死……）都会立刻放出派对客。我们自己的踢死在调用 killPlayer 前已移除记录，
        // 因此这里不会再处理它。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> releaseHeldBy(victim));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> releaseHeldBy(handler.player));
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> releaseAll(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> releaseAll(world));
        ServerWorldEvents.UNLOAD.register((server, world) -> releaseAll(world));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIGESTING.clear());
    }

    // ---- Swallow / 吞下 ----

    /**
     * {@code partygoer_swallow} receiver (server thread). Validates in rule order, then charges the cooldown BEFORE the
     * swallow, so repeated or racing requests (including NoellesRoles' own player swallow in the same tick) fail on it.
     * {@code partygoer_swallow} 接收器（服务端线程）。按规则顺序校验，然后在吞下之前扣除冷却，因此重复或竞争的请求（包括同一刻
     * NoellesRoles 自己的吞人）都会因冷却失败。
     */
    public static void handleSwallow(@Nullable ServerPlayerEntity taotie, @Nullable PartygoerSwallowC2SPacket packet) {
        if (taotie == null || packet == null) {
            return;
        }
        ServerWorld world = taotie.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Entity target = world.getEntityById(packet.entityId());
        PartygoerBrain brain = PartygoerService.brainOf(target);
        if (!PartygoerTaotieRules.swallowDenial(new ServerChecks(taotie, game, target, brain)).allowed()) {
            return;
        }
        consumeSwallowCooldown(taotie);
        swallow(taotie, brain);
    }

    private static void swallow(ServerPlayerEntity taotie, PartygoerBrain brain) {
        ServerWorld world = taotie.getServerWorld();
        brain.swallowedBy(taotie);
        DIGESTING.put(taotie.getUuid(), new Digestion(taotie.getUuid(), brain, world, PartyAnimalRules.TAOTIE_DEATH_TICKS));
        // Hidden particles, visible icon. / 隐藏粒子，保留图标。
        taotie.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, PartyAnimalRules.TAOTIE_DEATH_TICKS,
                PartyAnimalRules.TAOTIE_SLOWNESS_AMPLIFIER, false, false, true));
        world.playSound(null, taotie.getX(), taotie.getEyeY(), taotie.getZ(), SoundEvents.ENTITY_GENERIC_DRINK,
                SoundCategory.PLAYERS, 1.2F, 0.5F);
        taotie.sendMessage(Text.translatable("tip.sparkstrength.partygoer.taotie_swallowed").formatted(Formatting.RED),
                true);
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", brain.ownerUuid());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.PARTYGOER_SWALLOWED, taotie, extra);
    }

    /**
     * Starts NoellesRoles' dynamic swallow cooldown, the same cost as a player swallow; {@code setSwallowCooldown} syncs
     * the Taotie's own client. / 启动 NoellesRoles 的动态吞噬冷却，与吞人代价相同；setSwallowCooldown 会同步给饕餮本人。
     */
    private static void consumeSwallowCooldown(ServerPlayerEntity taotie) {
        TaotiePlayerComponent component = TaotiePlayerComponent.KEY.get(taotie);
        int calculated = ((PartygoerTaotieCooldownAccessor) (Object) component).sparkstrength$getCalculatedSwallowCooldown();
        component.setSwallowCooldown(PartygoerTaotieRules.swallowCostTicks(calculated,
                TaotiePlayerComponent.SWALLOW_COOLDOWN));
    }

    // ---- Kicking inside / 肚里狂踢 ----

    private static void tick(MinecraftServer server) {
        if (DIGESTING.isEmpty()) {
            return;
        }
        for (Digestion digestion : new ArrayList<>(DIGESTING.values())) {
            if (DIGESTING.get(digestion.taotieUuid) != digestion) {
                continue;
            }
            if (!digestion.stillInside()) {
                // Let out or ended elsewhere (the brain's own fallback, an admin removal): nothing left to kick.
                // 已在别处放出或结束（大脑自身的兜底、管理员移除）：没有东西可踢了。
                DIGESTING.remove(digestion.taotieUuid);
                continue;
            }
            ServerPlayerEntity taotie = server.getPlayerManager().getPlayer(digestion.taotieUuid);
            if (taotie == null || !GameFunctions.isPlayerPlayingAndAlive(taotie)) {
                release(digestion, taotie);
                continue;
            }
            digestion.ticksLeft = PartygoerTaotieRules.afterTick(digestion.ticksLeft);
            if (PartygoerTaotieRules.kicksToDeath(digestion.ticksLeft)) {
                kickToDeath(digestion, taotie);
            }
        }
    }

    /**
     * Release FIRST (so the brain is already out and hunting when KillPlayer.AFTER runs), then the forced kill with the
     * KICKED reason itself (SparkTraits matches terminal reasons by identity). Credit follows the Partygoer's own rule:
     * the summoner when online, unless the Taotie counts as a killer.
     * 先放出（这样 KillPlayer.AFTER 运行时大脑已经出来并开始追人），再用 KICKED 死因常量本身强制击杀（SparkTraits 按同一对象
     * 匹配终结死因）。击杀归属沿用派对客自己的规则：召唤者在线时记给召唤者，除非饕餮算作杀手阵营。
     */
    private static void kickToDeath(Digestion digestion, ServerPlayerEntity taotie) {
        DIGESTING.remove(digestion.taotieUuid);
        Vec3d at = taotie.getPos();
        digestion.brain.releasedAt(at);
        ServerWorld world = taotie.getServerWorld();
        world.playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundCategory.HOSTILE, 1.0F, 0.8F);
        world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 1.0, at.z, 20, 0.3, 0.6, 0.3, 0.02);
        ServerPlayerEntity credit = PartygoerService.killCredit(digestion.brain, taotie);
        GameFunctions.killPlayer(taotie, true, credit, PartyAnimalRules.DEATH_REASON_KICKED, true);
    }

    // ---- Early release / 提前放出 ----

    /** The Taotie died, left or was reset: let its Partygoer out where it stood. / 饕餮死亡、离开或被重置：在其所站位置放出派对客。 */
    private static void releaseHeldBy(@Nullable ServerPlayerEntity taotie) {
        if (taotie == null) {
            return;
        }
        Digestion digestion = DIGESTING.get(taotie.getUuid());
        if (digestion != null) {
            release(digestion, taotie);
        }
    }

    private static void releaseAll(World world) {
        for (Digestion digestion : new ArrayList<>(DIGESTING.values())) {
            if (digestion.world == world) {
                release(digestion, world.getServer() == null ? null
                        : world.getServer().getPlayerManager().getPlayer(digestion.taotieUuid));
            }
        }
    }

    private static void release(Digestion digestion, @Nullable ServerPlayerEntity taotie) {
        DIGESTING.remove(digestion.taotieUuid, digestion);
        if (digestion.stillInside()) {
            digestion.brain.releasedAt(taotie != null ? taotie.getPos() : digestion.brain.body().getPos());
        }
    }

    /** Whether this Taotie has a Partygoer kicking inside right now. / 该饕餮肚里此刻是否有派对客在踢。 */
    public static boolean isDigesting(UUID taotie) {
        return DIGESTING.containsKey(taotie);
    }

    /** One Partygoer inside one Taotie. / 一只饕餮肚里的一只派对客。 */
    private static final class Digestion {
        private final UUID taotieUuid;
        private final PartygoerBrain brain;
        private final ServerWorld world;
        private int ticksLeft;

        private Digestion(UUID taotieUuid, PartygoerBrain brain, ServerWorld world, int ticksLeft) {
            this.taotieUuid = taotieUuid;
            this.brain = brain;
            this.world = world;
            this.ticksLeft = ticksLeft;
        }

        /** Still the live brain, swallowed by exactly this Taotie. / 仍是在场的大脑，且恰被这只饕餮吞下。 */
        private boolean stillInside() {
            return !brain.isFinished()
                    && brain.state() == PartygoerState.SWALLOWED
                    && taotieUuid.equals(brain.swallowerUuid())
                    && PartygoerService.brainOf(brain.body()) == brain;
        }
    }

    /** Lazily evaluated facts for one request. / 单次请求的按需求值事实。 */
    private record ServerChecks(ServerPlayerEntity taotie, GameWorldComponent game, @Nullable Entity target,
                                @Nullable PartygoerBrain brain) implements PartygoerTaotieRules.SwallowChecks {
        @Override
        public boolean running() {
            return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        }

        @Override
        public boolean exactTaotie() {
            return TaotieHeadRules.isTaotie(game.getRole(taotie));
        }

        @Override
        public boolean aliveParticipant() {
            return GameFunctions.isPlayerPlayingAndAlive(taotie)
                    && GameFunctions.isPlayerAliveAndSurvival(taotie)
                    && !SparkWitchCompat.isWraithActive(taotie);
        }

        @Override
        public boolean swallowed() {
            return SwallowedPlayerComponent.isPlayerSwallowed(taotie);
        }

        @Override
        public boolean carried() {
            return PartygoerCarry.isCarried(taotie);
        }

        @Override
        public boolean dazed() {
            return TaotieHeadDaze.isDazed(taotie);
        }

        @Override
        public boolean stunned() {
            return EngineerStunnedPlayerComponent.KEY.get(taotie).isStunned()
                    || SparkWitchCompat.isControlExpertStunned(taotie);
        }

        @Override
        public boolean roleSkillBlocked() {
            return SparkTraitsDroneCompat.isRoleSkillBlocked(taotie) || SparkTraitsDroneCompat.isLastStandPending(taotie);
        }

        @Override
        public boolean digesting() {
            return isDigesting(taotie.getUuid());
        }

        @Override
        public int swallowCooldownTicks() {
            return TaotiePlayerComponent.KEY.get(taotie).getSwallowCooldown();
        }

        @Override
        public boolean livePartygoer() {
            return target != null && brain != null && brain.isKillable() && target.getWorld() == taotie.getWorld();
        }

        @Override
        public double squaredDistanceToPartygoer() {
            return target == null ? Double.NaN : taotie.squaredDistanceTo(target);
        }

        @Override
        public int reachSquared() {
            return TaotiePlayerComponent.SWALLOW_DISTANCE_SQUARED;
        }

        @Override
        public boolean canSeePartygoer() {
            return target != null && taotie.canSee(target);
        }
    }
}
