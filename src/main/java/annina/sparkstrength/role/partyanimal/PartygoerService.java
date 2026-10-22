package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.component.partyanimal.PartyAnimalSkillComponent;
import annina.sparkstrength.component.partyanimal.PartygoerOwnerComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDeathReasonCompat;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import annina.sparkstrength.role.partyanimal.nav.TrainNavGrid;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lifecycle of every Partygoer: summoning, endings (ate, gave up, killed, quiet), owner feedback, rewards and the
 * cross-mod kill seam. Server thread only.
 * 所有派对客的生命周期：召唤、结局（吃完、放弃、被打死、安静离场）、召唤者反馈、奖励与跨模组击杀接缝。仅限服务端线程。
 */
public final class PartygoerService {
    private static final List<Identifier> CLIP_IDS = List.of(
            SparkStrengthSounds.PARTYGOER_SPAWN_ID,
            SparkStrengthSounds.PARTYGOER_GRAB_ID,
            SparkStrengthSounds.PARTYGOER_DEATH_ID,
            SparkStrengthSounds.PARTYGOER_VANISH_ID,
            SparkStrengthSounds.PARTYGOER_RAGE_ID
    );
    /** Players this far from the Partygoer hear its clips anyway, plus the owner from anywhere. / 此范围内的玩家能听到其音频，召唤者在任何地方都能听到。 */
    private static final double HEARING_RANGE = 16.0 * PartyAnimalRules.SOUND_VOLUME;
    private static final float OWNER_FAR_VOLUME = 0.6F;

    private static final Map<PartygoerEntity, PartygoerBrain> LIVE = new LinkedHashMap<>();
    private static boolean registered;

    public enum Ending {
        /** Finished its meal: a burp, then gone. / 吃完了：打个嗝后消失。 */
        ATE,
        /** Lost its prey for good: the "mission failed" clip. / 彻底丢了猎物：播放“任务失败”。 */
        GAVE_UP,
        /** Killed by a weapon: the "oof" clip and a corpse. / 被武器打死：播放“Oof”并留下尸体。 */
        KILLED,
        /** Round over or removed by an admin: no sound. / 对局结束或被管理员移除：无声。 */
        QUIET
    }

    private PartygoerService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            for (PartygoerBrain brain : new ArrayList<>(LIVE.values())) {
                brain.onPlayerKilled(victim, killer);
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                finishAll(serverWorld);
                PartyRoomIndex.clear(serverWorld);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof PartygoerEntity partygoer) {
                onRemoved(partygoer);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(PartygoerService::sweep);
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            finishAll(world);
            PartyRoomIndex.clear(world);
        });
        // Being eaten or kicked to death from inside is final: no Last Stand or Depression save.
        // 被吃掉或在肚里被一脚踢死都是最终死亡：背水一战与抑郁不会救人。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SparkTraitsDeathReasonCompat.registerTerminal(PartyAnimalRules.DEATH_REASON_EATEN);
            SparkTraitsDeathReasonCompat.registerTerminal(PartyAnimalRules.DEATH_REASON_KICKED);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LIVE.clear();
            PartyRoomIndex.clearAll();
        });
    }

    // ---- Queries / 查询 ----

    public static @Nullable PartygoerBrain brainOf(@Nullable Entity entity) {
        return entity instanceof PartygoerEntity partygoer ? LIVE.get(partygoer) : null;
    }

    /** Whether this owner still has a Partygoer out. / 该召唤者是否还有派对客在场。 */
    public static boolean hasActive(UUID owner) {
        for (PartygoerBrain brain : LIVE.values()) {
            if (!brain.isFinished() && brain.ownerUuid().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    /** Live Partygoers in this world (a snapshot). / 本世界的在场派对客（快照）。 */
    public static List<PartygoerEntity> live(ServerWorld world) {
        List<PartygoerEntity> out = new ArrayList<>();
        for (Map.Entry<PartygoerEntity, PartygoerBrain> entry : LIVE.entrySet()) {
            if (entry.getKey().getWorld() == world && !entry.getValue().isFinished()) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /**
     * Alive, in the world and not inside a Taotie. Frozen cross-mod seam like {@link #killByWeapon}: SparkWitch's
     * {@code compat/SparkStrengthPartygoerCompat} reaches {@code isKillable(Entity)} by reflection to pick targets.
     * 在场且不在饕餮肚里。与 killByWeapon 一样是冻结的跨模组接缝：SparkWitch 的 compat/SparkStrengthPartygoerCompat 通过反射调用
     * isKillable(Entity) 来选目标。
     */
    public static boolean isKillable(@Nullable Entity entity) {
        PartygoerBrain brain = brainOf(entity);
        return brain != null && brain.isKillable();
    }

    // ---- Summon / 召唤 ----

    public static PartygoerEntity summon(ServerPlayerEntity owner, UUID roundId, TrainNavGrid grid,
                                         PartyRoomIndex.PartyRoom spawnRoom, PartyRoomIndex.PartyRoom destination,
                                         ServerPlayerEntity prey) {
        ServerWorld world = owner.getServerWorld();
        PartygoerEntity body = new PartygoerEntity(SparkStrengthEntities.partygoer(), world);
        Vec3d at = spawnRoom.anchor();
        float yaw = (float) (Math.atan2(prey.getZ() - at.z, prey.getX() - at.x) * (180.0 / Math.PI)) - 90.0F;
        body.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
        body.setHeadYaw(yaw);
        body.setBodyYaw(yaw);
        PartygoerBrain brain = new PartygoerBrain(body, world, owner.getUuid(), roundId, grid, destination, prey.getUuid());
        body.attachBrain(brain);
        PartygoerOwnerComponent.KEY.get(body).setOwner(owner.getUuid());
        LIVE.put(body, brain);
        world.spawnEntity(body);
        world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 1.0, at.z, 20, 0.3, 0.6, 0.3, 0.02);
        playClip(brain, SparkStrengthSounds.PARTYGOER_SPAWN);
        NbtCompound extra = new NbtCompound();
        extra.putString("from", spawnRoom.name());
        extra.putString("to", destination.name());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.PARTYGOER_SUMMONED, owner, extra);
        publishStatus(brain);
        return body;
    }

    // ---- Endings / 结局 ----

    /** Ends a Partygoer once; every ending path comes through here. / 结束一只派对客（只结束一次）；所有结局都经过这里。 */
    public static void finish(PartygoerBrain brain, Ending ending) {
        if (brain.isFinished()) {
            return;
        }
        brain.markFinished();
        PartygoerEntity body = brain.body();
        ServerWorld world = brain.world();
        releasePassenger(brain);
        switch (ending) {
            case ATE -> world.playSound(null, body.getX(), body.getEyeY(), body.getZ(), SoundEvents.ENTITY_PLAYER_BURP,
                    SoundCategory.HOSTILE, 1.2F, 0.8F);
            // The body is gone this tick: an entity-bound clip would stop at once, so these stay where it was.
            // 实体本刻即消失：绑定实体的音频会立刻停止，因此这两段留在原地播放。
            case GAVE_UP -> playClip(brain, SparkStrengthSounds.PARTYGOER_VANISH, false);
            case KILLED -> playClip(brain, SparkStrengthSounds.PARTYGOER_DEATH, false);
            case QUIET -> {
            }
        }
        if (ending != Ending.QUIET && ending != Ending.KILLED) {
            world.spawnParticles(ParticleTypes.POOF, body.getX(), body.getY() + 1.0, body.getZ(),
                    20, 0.3, 0.6, 0.3, 0.02);
        }
        ServerPlayerEntity owner = player(world, brain.ownerUuid());
        if (owner != null) {
            PartyAnimalSkillComponent skill = PartyAnimalSkillComponent.KEY.get(owner);
            skill.clearActive();
            if (ending != Ending.QUIET) {
                skill.startCooldown();
            }
        }
        LIVE.remove(body);
        if (!body.isRemoved()) {
            body.discard();
        }
    }

    /**
     * The entity left the world some other way (chunk unload, a rider logging out, another mod): during its round that
     * counts as giving up (the cooldown starts), otherwise it ends quietly.
     * 实体以其他方式离开世界（区块卸载、骑乘者下线、其他模组）：仍在其对局中时算作放弃（开始冷却），否则安静结束。
     */
    public static void onRemoved(PartygoerEntity body) {
        PartygoerBrain brain = LIVE.get(body);
        if (brain != null && !brain.isFinished()) {
            finish(brain, brain.isCurrentRound() ? Ending.GAVE_UP : Ending.QUIET);
        }
        LIVE.remove(body);
    }

    /**
     * Every second: a Partygoer whose body is gone or stopped ticking (left the simulation distance) gives up, so its
     * summoner is never locked out.
     * 每秒一次：身体已消失或停止 tick（离开模拟距离）的派对客放弃，召唤者因此不会被一直锁住。
     */
    private static void sweep(MinecraftServer server) {
        if (LIVE.isEmpty() || server.getTicks() % 20 != 0) {
            return;
        }
        for (Map.Entry<PartygoerEntity, PartygoerBrain> entry : new ArrayList<>(LIVE.entrySet())) {
            PartygoerBrain brain = entry.getValue();
            if (entry.getKey().isRemoved()) {
                onRemoved(entry.getKey());
            } else if (brain.isStalled()) {
                finish(brain, brain.isCurrentRound() ? Ending.GAVE_UP : Ending.QUIET);
            }
        }
    }

    public static void vanishQuietly(PartygoerEntity body) {
        PartygoerBrain brain = LIVE.get(body);
        if (brain != null) {
            finish(brain, Ending.QUIET);
        } else {
            body.discard();
        }
    }

    private static void finishAll(ServerWorld world) {
        for (Map.Entry<PartygoerEntity, PartygoerBrain> entry : new ArrayList<>(LIVE.entrySet())) {
            if (entry.getKey().getWorld() == world) {
                finish(entry.getValue(), Ending.QUIET);
            }
        }
    }

    // ---- Kill seam / 击杀接缝 ----

    /**
     * Frozen cross-mod seam: anything that can kill a player kills a Partygoer in one hit (owner rule, 2026-10-21).
     * SparkWitch may reach it by reflection, so the class {@code annina.sparkstrength.role.partyanimal.PartygoerService},
     * the name {@code killByWeapon} and the parameter types {@code (ServerPlayerEntity, Entity, Identifier)} are frozen.
     * The caller has already validated its own weapon; this only checks the target. The attacker (may be null for an
     * offline thrower) gets 25 coins and 20% sanity; the Partygoer drops whoever it holds and leaves a corpse.
     * 冻结的跨模组接缝：任何能杀死玩家的东西都能一击打死派对客（主人规则，2026-10-21）。SparkWitch 可能通过反射调用，因此类名、方法名
     * killByWeapon 与参数类型 (ServerPlayerEntity, Entity, Identifier) 均已冻结。调用方已校验其武器；这里只检查目标。
     * 攻击者（离线投掷者可为 null）获得 25 金币与 20% 理智；派对客放下手中的人并留下尸体。
     *
     * @return true iff the target was a killable Partygoer and is now dead / 仅当目标是可被打死的派对客且已死亡时返回 true
     */
    public static boolean killByWeapon(@Nullable ServerPlayerEntity attacker, @Nullable Entity target,
                                       @Nullable Identifier deathReason) {
        PartygoerBrain brain = brainOf(target);
        if (brain == null || !brain.isKillable() || brain.world().isClient()) {
            return false;
        }
        PartygoerEntity body = brain.body();
        ServerWorld world = brain.world();
        Identifier reason = deathReason != null ? deathReason : GameConstants.DeathReasons.GENERIC;
        finish(brain, Ending.KILLED);
        PartygoerCorpseService.spawn(world, body, reason);
        // The summoner shooting its own Partygoer gets no refund. / 召唤者打死自己的派对客不算回本。
        if (attacker != null && GameFunctions.isPlayerPlayingAndAlive(attacker)
                && !attacker.getUuid().equals(brain.ownerUuid())) {
            reward(attacker);
        }
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", brain.ownerUuid());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.PARTYGOER_KILLED, attacker, extra);
        return true;
    }

    private static void reward(ServerPlayerEntity attacker) {
        PlayerShopComponent.KEY.get(attacker).addToBalance(PartyAnimalRules.KILL_REWARD_COINS);
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(attacker);
        mood.setMood(Math.min(1.0F, mood.getMood() + PartyAnimalRules.KILL_REWARD_SANITY));
        attacker.sendMessage(Text.translatable("tip.sparkstrength.partygoer.killed_reward",
                PartyAnimalRules.KILL_REWARD_COINS).formatted(Formatting.GOLD), true);
    }

    // ---- Helpers used by the brain / 大脑使用的辅助方法 ----

    /**
     * Whom an eaten player's death is credited to: the summoner when online, unless the victim is the summoner or on
     * the killer side (a rage meal of a teammate is not a team kill).
     * 被吃者的死亡记在谁头上：召唤者在线时记给召唤者，除非死者就是召唤者或属于杀手阵营（狂怒时吃掉队友不算队友击杀）。
     */
    static @Nullable ServerPlayerEntity killCredit(PartygoerBrain brain, ServerPlayerEntity victim) {
        ServerPlayerEntity owner = player(brain.world(), brain.ownerUuid());
        if (owner == null || owner == victim) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getWorld());
        return SparkTraitsCompat.isEffectiveKiller(game.getRole(victim), victim) ? null : owner;
    }

    /** Drops whoever it carries onto the nearest standable spot. / 把手中的人放到最近的可站立位置。 */
    static void releasePassenger(PartygoerBrain brain) {
        PartygoerEntity body = brain.body();
        for (Entity passenger : List.copyOf(body.getPassengerList())) {
            passenger.stopRiding();
            if (passenger instanceof ServerPlayerEntity player && GameFunctions.isPlayerPlayingAndAlive(player)) {
                TrainNavGrid grid = brain.grid();
                long cell = grid.snap(body.getX(), body.getY(), body.getZ());
                Vec3d at = cell == Long.MIN_VALUE ? body.getPos() : PartyRoomIndex.cellCentre(grid, cell);
                player.requestTeleport(at.x, at.y, at.z);
            }
        }
    }

    /**
     * Plays one of its clips from the Partygoer (cutting the previous clip for listeners in range); the owner hears it
     * from anywhere as skill feedback.
     * 从派对客身上播放一段音频（为范围内的听众切断上一段）；召唤者在任何地方都能听到，作为技能反馈。
     */
    static void playClip(PartygoerBrain brain, SoundEvent clip) {
        playClip(brain, clip, true);
    }

    /** {@code follow}: bound to the entity (moves with it) or left at its current spot. / 跟随实体移动，或留在当前位置。 */
    static void playClip(PartygoerBrain brain, SoundEvent clip, boolean follow) {
        PartygoerEntity body = brain.body();
        ServerWorld world = brain.world();
        for (ServerPlayerEntity listener : world.getPlayers()) {
            boolean inRange = listener.squaredDistanceTo(body) <= HEARING_RANGE * HEARING_RANGE;
            if (inRange || listener.getUuid().equals(brain.ownerUuid())) {
                for (Identifier id : CLIP_IDS) {
                    listener.networkHandler.sendPacket(new StopSoundS2CPacket(id, SoundCategory.HOSTILE));
                }
            }
        }
        long seed = world.getRandom().nextLong();
        if (follow) {
            world.playSoundFromEntity(null, body, Registries.SOUND_EVENT.getEntry(clip), SoundCategory.HOSTILE,
                    PartyAnimalRules.SOUND_VOLUME, 1.0F, seed);
        } else {
            world.playSound(null, body.getX(), body.getEyeY(), body.getZ(), Registries.SOUND_EVENT.getEntry(clip),
                    SoundCategory.HOSTILE, PartyAnimalRules.SOUND_VOLUME, 1.0F, seed);
        }
        ServerPlayerEntity owner = player(world, brain.ownerUuid());
        if (owner != null && owner.squaredDistanceTo(body) > HEARING_RANGE * HEARING_RANGE) {
            owner.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(clip),
                    SoundCategory.HOSTILE, owner.getX(), owner.getY(), owner.getZ(), OWNER_FAR_VOLUME, 1.0F, seed));
        }
    }

    /** Pushes the live summary to the owner's HUD. / 把实时概况推送到召唤者 HUD。 */
    static void publishStatus(PartygoerBrain brain) {
        if (brain.isFinished()) {
            return;
        }
        ServerPlayerEntity owner = player(brain.world(), brain.ownerUuid());
        if (owner == null) {
            return;
        }
        UUID focus = brain.focusUuid();
        ServerPlayerEntity focused = focus == null ? null : player(brain.world(), focus);
        String name = focused != null ? focused.getName().getString() : "";
        PartyAnimalSkillComponent.KEY.get(owner).setActive(brain.state().wire(), name, brain.phaseSecondsLeft());
    }

    static void recordGrab(PartygoerBrain brain, ServerPlayerEntity target) {
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", target.getUuid());
        extra.putUuid("actor", brain.ownerUuid());
        GameRecordManager.recordGlobalEvent(brain.world(), SparkStrengthReplayFormatters.PARTYGOER_GRABBED, null, extra);
    }

    static void recordRage(PartygoerBrain brain, ServerPlayerEntity killer) {
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", killer.getUuid());
        extra.putUuid("actor", brain.ownerUuid());
        GameRecordManager.recordGlobalEvent(brain.world(), SparkStrengthReplayFormatters.PARTYGOER_RAGED, null, extra);
    }

    private static @Nullable ServerPlayerEntity player(ServerWorld world, UUID uuid) {
        if (world.getPlayerByUuid(uuid) instanceof ServerPlayerEntity player) {
            return player;
        }
        return world.getServer().getPlayerManager().getPlayer(uuid);
    }
}
