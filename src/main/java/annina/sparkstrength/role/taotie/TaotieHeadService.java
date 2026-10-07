package annina.sparkstrength.role.taotie;

import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.component.taotie.TaotieHeadPlayerComponent;
import annina.sparkstrength.entity.TaotieHeadEntity;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import annina.sparkstrength.role.taotie.TaotieHeadRules.Heading;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server authority for the Taotie head: the fire gate (every condition re-checked from the empty request), the flight
 * (homing, swept collision) and the hit. NoellesRoles is read through named members only.
 * 饕餮头颅的服务端权威逻辑：发射校验（空请求到达后重新检查全部条件）、飞行（追踪、扫掠碰撞）与命中。
 * NoellesRoles 只通过具名成员读取。
 */
public final class TaotieHeadService {
    /** Candidates farther than this from the head can neither be tracked nor hit this tick. / 超出此距离的候选本 tick 既不能被追踪也不能被命中。 */
    private static final double CANDIDATE_RANGE_SQUARED = Math.pow(TaotieHeadRules.HOMING_RANGE + 3.0D, 2.0D);

    private TaotieHeadService() {
    }

    // ---- Fire / 发射 ----

    public static void tryFire(ServerPlayerEntity taotie) {
        ServerWorld world = taotie.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !TaotieHeadRules.isTaotie(game.getRole(taotie))
                || !GameFunctions.isPlayerPlayingAndAlive(taotie)
                || !GameFunctions.isPlayerAliveAndSurvival(taotie)
                || SwallowedPlayerComponent.isPlayerSwallowed(taotie)
                || TaotieHeadDaze.isDazed(taotie)
                || EngineerStunnedPlayerComponent.KEY.get(taotie).isStunned()
                || SparkTraitsDroneCompat.isRoleSkillBlocked(taotie)
                || SparkTraitsDroneCompat.isLastStandPending(taotie)) {
            return;
        }
        TaotieHeadPlayerComponent cooldown = TaotieHeadPlayerComponent.KEY.get(taotie);
        if (cooldown.getCooldownTicks() > 0) {
            return;
        }
        List<ServerPlayerEntity> stomach = livingSwallowed(taotie);
        if (stomach.isEmpty()) {
            taotie.sendMessage(Text.translatable("message.sparkstrength.taotie_head.no_head").withColor(taotieColor()),
                    true);
            return;
        }
        UUID roundId = M67RoundService.currentRoundId(world);
        if (roundId == null) {
            return;
        }

        // Skin only: firing never touches the swallowed player. / 仅取皮肤：发射不会影响被吞玩家。
        ServerPlayerEntity skinOwner = stomach.get(world.getRandom().nextInt(stomach.size()));
        Vec3d eye = taotie.getEyePos();
        Vec3d look = taotie.getRotationVector().normalize();
        Vec3d centre = eye.add(look.multiply(TaotieHeadRules.SPAWN_FORWARD_OFFSET));
        TaotieHeadEntity head = new TaotieHeadEntity(world, taotie, skinOwner, roundId, centre, look, eye);
        if (!world.spawnEntity(head)) {
            return;
        }
        cooldown.setCooldownTicks(TaotieHeadRules.cooldownTicksFor(stomach.size()));
        world.playSound(null, taotie.getX(), taotie.getEyeY(), taotie.getZ(), SoundEvents.ENTITY_LLAMA_SPIT,
                SoundCategory.PLAYERS, TaotieHeadRules.LAUNCH_SPIT_VOLUME, TaotieHeadRules.LAUNCH_SPIT_PITCH);
        world.playSound(null, taotie.getX(), taotie.getEyeY(), taotie.getZ(), SoundEvents.ENTITY_GHAST_SHOOT,
                SoundCategory.PLAYERS, TaotieHeadRules.LAUNCH_GHAST_VOLUME, TaotieHeadRules.LAUNCH_GHAST_PITCH);
    }

    /**
     * Living swallowed players of this Taotie: NoellesRoles' server-only stomach list resolved to online players that are
     * still swallowed by this exact Taotie and still playing and alive.
     * 该饕餮体内的存活玩家：NoellesRoles 仅服务端的体内名单，解析为在线、仍被这只饕餮吞下、且仍在局内存活的玩家。
     */
    public static List<ServerPlayerEntity> livingSwallowed(ServerPlayerEntity taotie) {
        List<ServerPlayerEntity> living = new ArrayList<>();
        UUID taotieUuid = taotie.getUuid();
        for (UUID uuid : TaotiePlayerComponent.KEY.get(taotie).getSwallowedPlayers()) {
            ServerPlayerEntity swallowed = taotie.server.getPlayerManager().getPlayer(uuid);
            if (swallowed == null) {
                continue;
            }
            SwallowedPlayerComponent state = SwallowedPlayerComponent.KEY.get(swallowed);
            if (state.isSwallowed() && taotieUuid.equals(state.getSwallowedBy())
                    && GameFunctions.isPlayerPlayingAndAlive(swallowed)) {
                living.add(swallowed);
            }
        }
        return living;
    }

    /** Exact (raw) Taotie identity, for the forced-cooldown store. / 精确（原始）饕餮身份，供强制冷却存储使用。 */
    public static boolean isTaotie(ServerPlayerEntity player) {
        return TaotieHeadRules.isTaotie(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    // ---- Flight / 飞行 ----

    /** Server tick of one head; called from {@link TaotieHeadEntity#tick}. / 单个头颅的服务端 tick。 */
    public static void tickHead(TaotieHeadEntity head) {
        if (!(head.getWorld() instanceof ServerWorld world)) {
            return;
        }
        UUID roundId = head.getRoundId();
        if (roundId == null
                || GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !M67RoundService.isCurrentRound(world, roundId)) {
            head.discard();
            return;
        }

        // The shooter may be dead (the head keeps flying); offline means SparkFactionAPI cannot be asked, so no player
        // is a valid target (fail closed) and the head only flies on to a wall.
        // 发射者可能已死亡（头颅继续飞行）；若已离线则无法询问 SparkFactionAPI，因此没有合法目标（失败即关闭），头颅只会飞向墙壁。
        ServerPlayerEntity shooter = head.getShooterUuid() == null
                ? null : world.getServer().getPlayerManager().getPlayer(head.getShooterUuid());
        Vec3d centre = head.getHeadCenter();
        List<ServerPlayerEntity> candidates = validTargets(world, head, shooter, centre);

        Vec3d velocity = head.getVelocity();
        ServerPlayerEntity target = resolveTarget(head, candidates, centre, velocity);
        if (target != null) {
            Heading steered = TaotieHeadRules.steer(heading(velocity), heading(bodyCentre(target).subtract(centre)),
                    TaotieHeadRules.MAX_TURN_DEGREES_PER_TICK);
            velocity = new Vec3d(steered.x(), steered.y(), steered.z()).multiply(TaotieHeadRules.SPEED);
            head.setVelocity(velocity);
        }
        head.alignRotationToVelocity(false);

        Vec3d pendingStart = head.takePendingSegmentStart();
        Vec3d start = pendingStart != null ? pendingStart : centre;
        Vec3d end = centre.add(velocity);

        // Swept collision: the centre segment against COLLIDER shapes (closed doors stop it), and against each valid
        // player's box grown by the head's half size (the 0.5 box sweep), so 1 block/tick never tunnels.
        // 扫掠碰撞：中心线段对 COLLIDER 形状（关闭的门会挡住），以及对每名合法玩家按头颅半边长扩大的碰撞箱（即 0.5 方块扫掠），
        // 因此每 tick 1 格的速度也不会穿透。
        BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, head));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY : start.squaredDistanceTo(blockHit.getPos());

        ServerPlayerEntity victim = null;
        double victimDistance = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity candidate : candidates) {
            Box swept = candidate.getBoundingBox().expand(TaotieHeadRules.HEAD_HALF_SIZE);
            double distance;
            if (swept.contains(start)) {
                distance = 0.0D;
            } else {
                Optional<Vec3d> entry = swept.raycast(start, end);
                if (entry.isEmpty()) {
                    continue;
                }
                distance = start.squaredDistanceTo(entry.get());
            }
            if (distance < victimDistance) {
                victim = candidate;
                victimDistance = distance;
            }
        }

        if (victim != null && victimDistance <= blockDistance) {
            hitPlayer(world, head, shooter, victim);
            return;
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            Vec3d at = blockHit.getPos();
            world.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 10, 0.15D, 0.15D, 0.15D, 0.02D);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ARMOR_STAND_BREAK, SoundCategory.PLAYERS,
                    TaotieHeadRules.BREAK_VOLUME, 1.0F);
            head.discard();
            return;
        }

        head.setHeadCenter(end);
        head.addTravelled(TaotieHeadRules.SPEED);
        if (TaotieHeadRules.pathExhausted(head.getTravelled())) {
            world.spawnParticles(ParticleTypes.POOF, end.x, end.y, end.z, 4, 0.1D, 0.1D, 0.1D, 0.01D);
            head.discard();
        }
    }

    /**
     * Valid targets near the head: any other player that is playing, alive, in survival, not swallowed and allowed by
     * SparkFactionAPI for this shooter. Everyone else is passed through, never collided with.
     * 头颅附近的合法目标：除发射者外仍在局内存活、生存模式、未被吞下且 SparkFactionAPI 允许该发射者影响的玩家。
     * 其他玩家一律穿过，不会碰撞。
     */
    private static List<ServerPlayerEntity> validTargets(ServerWorld world, TaotieHeadEntity head,
                                                         @Nullable ServerPlayerEntity shooter, Vec3d centre) {
        List<ServerPlayerEntity> valid = new ArrayList<>();
        if (shooter == null) {
            return valid;
        }
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate == shooter
                    || candidate.getUuid().equals(head.getShooterUuid())
                    || candidate.squaredDistanceTo(centre) > CANDIDATE_RANGE_SQUARED
                    || candidate.isSpectator()
                    || candidate.isCreative()
                    || !GameFunctions.isPlayerPlayingAndAlive(candidate)
                    || !GameFunctions.isPlayerAliveAndSurvival(candidate)
                    || SwallowedPlayerComponent.isPlayerSwallowed(candidate)
                    || !SparkFactionCompat.canAffectPlayer(shooter, candidate, TaotieHeadRules.HEAD_ACTION_ID)) {
                continue;
            }
            valid.add(candidate);
        }
        return valid;
    }

    /**
     * Keeps the current target while it stays trackable; otherwise reacquires the nearest trackable candidate.
     * 当前目标仍可追踪时保持；否则重新锁定最近的可追踪候选。
     */
    private static @Nullable ServerPlayerEntity resolveTarget(TaotieHeadEntity head, List<ServerPlayerEntity> candidates,
                                                              Vec3d centre, Vec3d velocity) {
        UUID currentUuid = head.getTargetUuid();
        if (currentUuid != null) {
            for (ServerPlayerEntity candidate : candidates) {
                if (currentUuid.equals(candidate.getUuid()) && isTrackable(head, centre, velocity, candidate)) {
                    return candidate;
                }
            }
        }
        ServerPlayerEntity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity candidate : candidates) {
            double distance = bodyCentre(candidate).squaredDistanceTo(centre);
            if (distance < bestDistance && isTrackable(head, centre, velocity, candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        head.setTargetUuid(best == null ? null : best.getUuid());
        return best;
    }

    /** Within 8 blocks, inside the 90° cone around the heading, and in line of sight (COLLIDER). / 8 格内、朝向 90° 锥内且视线无遮挡。 */
    private static boolean isTrackable(TaotieHeadEntity head, Vec3d centre, Vec3d velocity, ServerPlayerEntity target) {
        Vec3d body = bodyCentre(target);
        Vec3d offset = body.subtract(centre);
        if (offset.lengthSquared() > TaotieHeadRules.HOMING_RANGE_SQUARED
                || !TaotieHeadRules.withinCone(heading(velocity), heading(offset),
                TaotieHeadRules.HOMING_HALF_ANGLE_DEGREES)) {
            return false;
        }
        return head.getWorld().raycast(new RaycastContext(centre, body, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, head)).getType() == HitResult.Type.MISS;
    }

    // ---- Hit / 命中 ----

    private static void hitPlayer(ServerWorld world, TaotieHeadEntity head, @Nullable ServerPlayerEntity shooter,
                                  ServerPlayerEntity victim) {
        // No knockback (train falls) and nothing lethal: effects, the daze lock, feedback only.
        // 无击退（避免坠车）且不致命：只有状态效果、眩晕锁与反馈。
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, TaotieHeadRules.BLINDNESS_TICKS, 0),
                shooter);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, TaotieHeadRules.SLOWNESS_TICKS,
                TaotieHeadRules.SLOWNESS_AMPLIFIER), shooter);
        TaotieHeadDaze.apply(victim, TaotieHeadRules.DAZE_TICKS);

        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SparkStrengthSounds.TAOTIE_HEAD_BONK,
                SoundCategory.PLAYERS, TaotieHeadRules.BONK_VOLUME, TaotieHeadRules.BONK_PITCH);
        Vec3d at = head.getHeadCenter();
        world.spawnParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.2D, 0.2D, 0.2D, 0.02D);

        int color = taotieColor();
        victim.sendMessage(Text.translatable("message.sparkstrength.taotie_head.dazed").withColor(color), true);
        if (shooter != null) {
            shooter.sendMessage(Text.translatable("message.sparkstrength.taotie_head.hit", victim.getDisplayName())
                    .withColor(color), true);
        }

        NbtCompound extra = new NbtCompound();
        if (head.getShooterUuid() != null) {
            extra.putUuid("actor", head.getShooterUuid());
        }
        extra.putUuid("target", victim.getUuid());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.TAOTIE_HEAD_HIT, shooter, extra);
        head.discard();
    }

    // ---- Helpers / 工具 ----

    private static Vec3d bodyCentre(ServerPlayerEntity player) {
        return player.getPos().add(0.0D, player.getHeight() * 0.5D, 0.0D);
    }

    private static Heading heading(Vec3d vector) {
        return new Heading(vector.x, vector.y, vector.z);
    }

    private static int taotieColor() {
        return Noellesroles.TAOTIE.color();
    }
}
