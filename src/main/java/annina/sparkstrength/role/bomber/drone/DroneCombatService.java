package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.SparkStrengthSounds;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.entity.M67GrenadeEntity;
import annina.sparkstrength.item.grenade.GrenadeBlastService;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import annina.sparkstrength.role.bodyguard.AttackOriginScope;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.item.KnifeItem;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Drone firing, destruction and blasts. Every break, whatever the weapon, ends in {@link #destroy}; weapon entries
 * live in {@link DroneWeaponHits}.
 * 无人机开火、损毁与爆炸。无论何种武器，所有击毁都汇入 {@link #destroy}；各武器入口位于 {@link DroneWeaponHits}。
 *
 * <ul>
 *   <li>{@link #fire}: grenade drone drops its bound M67 straight down, inheriting the drone's velocity (normal M67
 *   fuse/blast/death reason, owner = Bomber, but the thrower never reaches clients); bomb drone detonates.
 *   投弹无人机竖直投下挂载的 M67，继承无人机速度（普通 M67 引信/爆炸/死因，归属炸弹客，但投掷者不会下发给客户端）；炸弹无人机直接引爆。</li>
 *   <li>{@link #destroy}: grenade drone drops its M67 as a pickable item (never explodes) and returns to the owner on a
 *   45 s cooldown; bomb drone detonates immediately.
 *   投弹无人机掉落可拾取的 M67（不会爆炸）并以 45 秒冷却返还主人；炸弹无人机立即引爆。</li>
 *   <li>{@link #detonate}: bomb drone blast, radius {@link DroneRules#BOMB_BLAST_RADIUS}, M67 path judgement, kills
 *   credited to the owner with the NoellesRoles bomb death reason; chains into other drones in the blast.
 *   炸弹无人机爆炸，沿用 M67 路径判定，击杀归属主人并使用 NoellesRoles 炸弹死因；会连锁击毁范围内的其他无人机。</li>
 * </ul>
 *
 * <p>Re-entrancy: a drone being removed is held in {@link #BUSY}; every entry point ignores it, so a blast that breaks
 * another bomb drone chains safely and can never recurse into the drone that started it (nor into a drone whose
 * owner the blast just killed).
 * 重入：正在移除的无人机记录在 BUSY 中，所有入口都会忽略它；因此爆炸击毁另一架炸弹无人机时可安全连锁，
 * 永远不会递归回发起爆炸的无人机（也不会处理刚被炸死主人的同一架无人机）。</p>
 *
 * <p>Own blasts: Wathe grenades and M67s break drones through their own hooks, after their kills. SparkWitch also
 * reports those blasts to {@code SeekerDeviceHits.onBlast} (at the Wathe grenade's HEAD, before its kills), so its
 * drone hook is muted while {@link #runOwnBlast} is on the stack; otherwise chained bomb-drone kills would land before
 * the grenade's own.
 * 自有爆炸：Wathe 手雷与 M67 在各自击杀之后通过自身钩子击毁无人机。SparkWitch 也会把这些爆炸报给 SeekerDeviceHits.onBlast
 * （Wathe 手雷在 HEAD、即其击杀之前），因此在 runOwnBlast 执行期间其无人机钩子保持静默；否则连锁的炸弹无人机击杀会早于手雷自身的击杀。</p>
 */
public final class DroneCombatService {
    /** The M67 leaves the hull with this small extra downward speed. / M67 离开机身时额外的少量向下速度。 */
    private static final double DROP_NUDGE = 0.04;
    private static final double DROP_CLEARANCE = 0.02;

    // Server thread only. / 仅服务器线程。
    private static final Set<DroneEntity> BUSY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int ownBlastDepth;

    private DroneCombatService() {
    }

    public static void register() {
        DroneWeaponHits.register();
    }

    /**
     * Pilot's left-click (already validated by DronePilotService). / 驾驶者左键（已由 DronePilotService 校验）。
     */
    public static void fire(DroneEntity drone, ServerPlayerEntity pilot) {
        if (!(drone.getWorld() instanceof ServerWorld world) || !usable(drone) || pilot == null) {
            return;
        }
        if (drone.kind() == DroneKind.BOMB) {
            detonate(drone);
            return;
        }
        if (!drone.hasPayload()) {
            clickToPilot(world, pilot, drone);
            return;
        }
        UUID roundId = drone.roundId();
        if (roundId == null || !roundId.equals(DroneService.currentRoundId(world))
                || !pilot.getUuid().equals(drone.ownerUuid())) {
            return;
        }
        Vec3d inherited = drone.getVelocity();
        if (!Double.isFinite(inherited.lengthSquared())) {
            inherited = Vec3d.ZERO;
        } else {
            double scale = DroneFlight.horizontalScale(inherited.x, inherited.z,
                    DroneRules.maxHorizontalStep(drone.kind()));
            inherited = new Vec3d(inherited.x * scale,
                    DroneFlight.clampVertical(inherited.y, DroneRules.maxVerticalStep()), inherited.z * scale);
        }
        Vec3d velocity = inherited.add(0.0, -DROP_NUDGE, 0.0);
        Box hull = drone.getBoundingBox();
        Vec3d underHull = new Vec3d(drone.getX(), hull.minY - SparkStrengthEntities.m67().getHeight() - DROP_CLEARANCE,
                drone.getZ());
        M67GrenadeEntity grenade = armedM67(world, pilot, roundId, drone, underHull, velocity);
        if (!world.isSpaceEmpty(grenade)) {
            // Resting on a floor: release from inside the hull instead of inside the block. / 停在地面时从机身内释放。
            grenade.setPosition(drone.getX(), hull.minY + DROP_CLEARANCE, drone.getZ());
        }
        if (!world.spawnEntity(grenade)) {
            grenade.discard();
            return;
        }
        M67RoundService.registerThrown(grenade);
        if (grenade.isRemoved()) {
            return;
        }
        drone.setPayload(false);
        world.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SparkStrengthSounds.DRONE_RELEASE,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.DRONE_GRENADE_DROPPED, pilot, null);
    }

    /**
     * An ordinary M67 (same thrower/round/fuse as a thrown one, so the same blast, death reason and attribution) placed
     * under the hull, facing like the drone and inheriting its velocity; the caller spawns and registers it. Privacy:
     * the thrower stays server-only (no tracked thrower, no owner id in the spawn packet), so a drone drop never reveals
     * the Bomber.
     * 普通 M67（投掷者/回合/引信与手投相同，因此爆炸、死因与归属一致），放在机腹下、朝向与无人机一致并继承其速度；由调用方生成并登记。
     * 隐私：投掷者只留在服务器（不写追踪数据，生成包不带主人 id），无人机投弹绝不暴露炸弹客。
     */
    private static M67GrenadeEntity armedM67(ServerWorld world, ServerPlayerEntity owner, UUID roundId,
                                             DroneEntity drone, Vec3d pos, Vec3d velocity) {
        M67GrenadeEntity grenade = M67GrenadeEntity.droppedConcealed(world, owner, roundId, pos, drone.getYaw(),
                drone.getPitch());
        grenade.setVelocity(velocity);
        // Armed (spoon released) model, like a thrown M67. / 与手投一样使用已拉环（释放握片）的模型。
        ItemStack stack = new ItemStack(SparkStrengthItems.m67());
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(1));
        grenade.setItem(stack);
        return grenade;
    }

    /** @param attacker who broke it, if a player / 击毁者（若为玩家） */
    public static void destroy(DroneEntity drone, @Nullable ServerPlayerEntity attacker) {
        if (!(drone.getWorld() instanceof ServerWorld world) || !usable(drone)) {
            return;
        }
        recordDestroyed(world, drone, attacker);
        if (drone.kind() == DroneKind.BOMB) {
            detonate(drone);
            return;
        }
        BUSY.add(drone);
        try {
            DronePilotService.endFor(drone, DronePilotEndReason.DESTROYED);
            dropPayload(world, drone);
            world.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SparkStrengthSounds.DRONE_BREAK,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
            Vec3d center = drone.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 12, 0.2, 0.1, 0.2, 0.02);
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM,
                            new ItemStack(SparkStrengthItems.grenadeDrone())),
                    center.x, center.y, center.z, 16, 0.15, 0.1, 0.15, 0.12);
            UUID ownerUuid = drone.ownerUuid();
            int charge = drone.charge();
            drone.discard();
            if (ownerUuid != null) {
                // Core makes this a no-op for a dead/offline/non-Bomber owner. / 主人死亡/离线/非炸弹客时核心不会返还。
                DroneService.returnGrenadeDrone(world, ownerUuid, charge, DroneRules.GRENADE_LOST_COOLDOWN_TICKS);
            }
        } finally {
            BUSY.remove(drone);
        }
    }

    public static void detonate(DroneEntity drone) {
        if (!(drone.getWorld() instanceof ServerWorld world) || !usable(drone)) {
            return;
        }
        if (drone.kind() != DroneKind.BOMB) {
            destroy(drone, null);
            return;
        }
        BUSY.add(drone);
        try {
            DronePilotService.endFor(drone, DronePilotEndReason.DETONATED);
            UUID roundId = drone.roundId();
            if (roundId == null || !roundId.equals(DroneService.currentRoundId(world))) {
                // Stale round: fail closed, no blast. / 旧回合：失效移除，不爆炸。
                drone.discard();
                return;
            }
            Vec3d center = drone.getBoundingBox().getCenter();
            world.playSound(null, drone.getBlockPos(), SparkStrengthSounds.M67_EXPLODE, SoundCategory.PLAYERS,
                    5.0F, 1.0F);
            world.spawnParticles(WatheParticles.BIG_EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 100, 0, 0, 0, 0.2);
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM,
                            new ItemStack(SparkStrengthItems.bombDrone())),
                    center.x, center.y, center.z, 100, 0, 0, 0, 1.0);

            UUID ownerUuid = drone.ownerUuid();
            // Connected owners keep credit even when dead (like M67). / 在线主人即使已死亡也保留击杀归属（同 M67）。
            ServerPlayerEntity owner = ownerUuid == null ? null
                    : world.getServer().getPlayerManager().getPlayer(ownerUuid);
            recordDetonated(world, ownerUuid);
            List<ServerPlayerEntity> candidates = List.copyOf(
                    world.getPlayers(GameFunctions::isPlayerAliveAndSurvival));
            for (ServerPlayerEntity victim : GrenadeBlastService.filterVictims(world, drone, candidates,
                    DroneRules.BOMB_BLAST_RADIUS)) {
                if (!roundId.equals(DroneService.currentRoundId(world))) {
                    break;
                }
                // Exactly one ordinary kill attempt; protection/rewards belong to Wathe and NoellesRoles.
                // 仅调用一次普通击杀流程，保护与奖励交给 Wathe 与 NoellesRoles。
                if (GameFunctions.isPlayerAliveAndSurvival(victim)) {
                    // NoellesRoles pays the Bomber's bomb bounty only for its own DEATH_REASON_BOMB instance (reference
                    // compare), so pass that field itself, read at call time. / NoellesRoles 按引用比较其 DEATH_REASON_BOMB
                    // 才发放炸弹赏金，因此直接传入该字段（调用时读取）。
                    // The blast scope tells this drone blast from the Bomber's carried bomb (same reason) and lets a
                    // Bodyguard's shield face it. / 爆炸作用域把无人机爆炸与炸弹客随身炸弹（同一死因）区分开，并让保镖的盾按爆炸点判定方向。
                    AttackOriginScope.runBlast(center, () ->
                            GameFunctions.killPlayer(victim, true, owner, Noellesroles.DEATH_REASON_BOMB));
                }
            }
            if (roundId.equals(DroneService.currentRoundId(world))) {
                breakDronesInBlast(world, center, DroneRules.BOMB_BLAST_RADIUS, drone, owner);
            }
            drone.discard();
        } finally {
            BUSY.remove(drone);
        }
    }

    /** Battery reached 0: a bomb drone detonates in place; a grenade drone falls (core). / 电量耗尽：炸弹无人机原地引爆。 */
    public static void deplete(DroneEntity drone) {
        if (drone.kind() == DroneKind.BOMB) {
            detonate(drone);
        }
    }

    /**
     * Owner lost (dead/disconnected): bomb drone is removed with a smoke puff, no blast.
     * 主人失联（死亡/断线）：炸弹无人机冒烟消失，不爆炸。
     */
    public static void fizzle(DroneEntity drone) {
        if (!(drone.getWorld() instanceof ServerWorld world) || !usable(drone)) {
            return;
        }
        BUSY.add(drone);
        try {
            DronePilotService.endFor(drone, DronePilotEndReason.PILOT_DOWN);
            dropPayload(world, drone);
            Vec3d center = drone.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 8, 0.15, 0.1, 0.15, 0.02);
            world.spawnParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 16, 0.2, 0.1, 0.2, 0.01);
            world.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.PLAYERS, 0.5F, 1.4F);
            drone.discard();
        } finally {
            BUSY.remove(drone);
        }
    }

    /**
     * Vanilla damage path ({@code DroneEntity#damage}): projectiles, explosions and other attacker-bearing sources
     * break the drone; plain player melee ({@code PLAYER_ATTACK}) is owned by the left-click callback, so fists never
     * reach here; fall, void, fire and other environmental damage are ignored. True when broken (an arrow then stops
     * instead of deflecting).
     * 原版伤害路径：投射物、爆炸及其他带攻击者的伤害会击毁无人机；玩家近战（PLAYER_ATTACK）由左键回调负责，空手永远不会走到这里；
     * 摔落、虚空、火焰等环境伤害被忽略。击毁时返回 true（箭矢随之停下而非弹开）。
     */
    public static boolean onDamaged(DroneEntity drone, DamageSource source, float amount) {
        if (!(drone.getWorld() instanceof ServerWorld) || !usable(drone) || source == null
                || !(amount > 0.0F) || !Float.isFinite(amount) || source.isOf(DamageTypes.PLAYER_ATTACK)) {
            return false;
        }
        boolean breaks = source.isIn(DamageTypeTags.IS_EXPLOSION)
                || source.isIn(DamageTypeTags.IS_PROJECTILE)
                || source.getAttacker() != null
                || source.getSource() != null;
        if (!breaks) {
            return false;
        }
        destroy(drone, source.getAttacker() instanceof ServerPlayerEntity player ? player : null);
        return true;
    }

    /**
     * Left-click weapon test from the item itself, never live attributes (Strength would turn fists into weapons):
     * id whitelist, Wathe knives, swords/axes/mace/trident, or a positive ADD_VALUE main-hand attack-damage modifier;
     * pickaxes, shovels and hoes never count.
     * 仅依据物品本身判断左键武器，绝不读取实时属性（力量效果会让空手变成武器）：id 白名单、Wathe 刀、剑/斧/重锤/三叉戟，
     * 或主手正值 ADD_VALUE 攻击伤害修饰；镐、锹、锄永远不算。
     */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)) {
            return false;
        }
        if (DroneWeaponRules.isWhitelistedMeleeId(Registries.ITEM.getId(stack.getItem()).toString())
                || stack.getItem() instanceof KnifeItem
                || stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES)
                || stack.isOf(Items.MACE) || stack.isOf(Items.TRIDENT)) {
            return true;
        }
        AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                AttributeModifiersComponent.DEFAULT);
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == EntityAttributes.GENERIC_ATTACK_DAMAGE.value()
                    && entry.slot().matches(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == EntityAttributeModifier.Operation.ADD_VALUE
                    && entry.modifier().value() > 0.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Server thread: run a Wathe grenade / M67 detonation that breaks drones itself; SparkWitch's blast hook stays
     * muted until it returns (see the class note). Re-entrant.
     * 服务器线程：执行会自行击毁无人机的 Wathe 手雷 / M67 引爆；返回前 SparkWitch 的爆炸钩子保持静默（见类说明）。可重入。
     */
    public static void runOwnBlast(Runnable blast) {
        ownBlastDepth++;
        try {
            blast.run();
        } finally {
            ownBlastDepth--;
        }
    }

    /** True while a {@link #runOwnBlast} detonation is running. / runOwnBlast 引爆进行中时为 true。 */
    static boolean inOwnBlast() {
        return ownBlastDepth > 0;
    }

    /** Break every drone caught in a blast centred on {@code origin}. / 击毁以 origin 为中心爆炸范围内的所有无人机。 */
    public static void breakDronesInBlast(ServerWorld world, Entity origin, double radius, @Nullable ServerPlayerEntity cause) {
        if (world == null || origin == null) {
            return;
        }
        breakDronesInBlast(world, origin.getBoundingBox().getCenter(), radius, origin, cause);
    }

    /**
     * Point form: drones whose box centre is inside the sphere with line of sight to the centre break (bomb drones
     * chain-detonate). Collected first so one break never changes the set seen by the rest.
     * 点形式：箱体中心位于球内且与爆心有视线的无人机被击毁（炸弹无人机连锁引爆）。先收集，避免一次击毁影响其余判定。
     */
    public static void breakDronesInBlast(ServerWorld world, Vec3d center, double radius, @Nullable Entity context,
                                          @Nullable ServerPlayerEntity cause) {
        if (world == null || center == null || !(radius > 0.0) || !Double.isFinite(radius)) {
            return;
        }
        Box search = new Box(center, center).expand(radius + 1.0);
        List<DroneEntity> caught = world.getEntitiesByClass(DroneEntity.class, search, drone -> drone != context
                && usable(drone) && DroneHitGeometry.caughtInBlast(world, center, radius, drone, context));
        for (DroneEntity drone : caught) {
            destroy(drone, cause);
        }
    }

    /** Replay: the owner placed a drone (called by DroneItem). / 回放：主人放置了无人机（由 DroneItem 调用）。 */
    public static void recordPlaced(ServerPlayerEntity owner, DroneEntity drone) {
        if (owner == null || drone == null || !(drone.getWorld() instanceof ServerWorld world)) {
            return;
        }
        NbtCompound extra = new NbtCompound();
        extra.putString("kind", drone.kind().id());
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.DRONE_PLACED, owner, extra);
    }

    /** Live, server-tracked and not already being removed. / 存活且未处于移除流程中。 */
    static boolean usable(@Nullable DroneEntity drone) {
        return DroneHitGeometry.isLive(drone) && !BUSY.contains(drone);
    }

    private static void recordDestroyed(ServerWorld world, DroneEntity drone, @Nullable ServerPlayerEntity attacker) {
        NbtCompound extra = new NbtCompound();
        extra.putString("kind", drone.kind().id());
        UUID ownerUuid = drone.ownerUuid();
        if (ownerUuid != null) {
            extra.putUuid("target", ownerUuid);
        }
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.DRONE_DESTROYED, attacker, extra);
    }

    private static void recordDetonated(ServerWorld world, @Nullable UUID ownerUuid) {
        NbtCompound extra = new NbtCompound();
        if (ownerUuid != null) {
            // Stored directly so an offline owner is still named. / 直接写入，主人离线时仍可显示名字。
            extra.putUuid("actor", ownerUuid);
        }
        GameRecordManager.recordGlobalEvent(world, SparkStrengthReplayFormatters.BOMB_DRONE_DETONATED, null, extra);
    }

    /** The bound M67 falls out as a pickable item and never explodes. / 挂载的 M67 以可拾取物品掉出，绝不爆炸。 */
    private static void dropPayload(ServerWorld world, DroneEntity drone) {
        if (drone.kind() != DroneKind.GRENADE || !drone.hasPayload()) {
            return;
        }
        drone.setPayload(false);
        ItemEntity item = new ItemEntity(world, drone.getX(), drone.getBoundingBox().minY, drone.getZ(),
                new ItemStack(SparkStrengthItems.m67()));
        item.setToDefaultPickupDelay();
        world.spawnEntity(item);
    }

    /**
     * Empty grenade drone: a dry click only the pilot hears, positioned at the drone where the pilot's camera is.
     * 未挂载的投弹无人机：只有驾驶者能听到的空响，位置在无人机处（驾驶者镜头所在）。
     */
    private static void clickToPilot(ServerWorld world, ServerPlayerEntity pilot, DroneEntity drone) {
        pilot.networkHandler.sendPacket(new PlaySoundS2CPacket(
                Registries.SOUND_EVENT.getEntry(SoundEvents.BLOCK_DISPENSER_FAIL), SoundCategory.PLAYERS,
                drone.getX(), drone.getY(), drone.getZ(), 0.6F, 1.4F, world.getRandom().nextLong()));
    }
}
