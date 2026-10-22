package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.bomber.drone.DroneHitGeometry;
import annina.sparkstrength.role.bomber.drone.DroneWeaponHits;
import annina.sparkstrength.role.bomber.drone.DroneWeaponRules;
import annina.sparkstrength.role.coroner.CoronerService;
import annina.sparkstrength.role.silencer.SilencerQuietService;
import annina.sparkstrength.role.veteran.VeteranRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.item.KnifeItem;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.bomber.BomberPlayerComponent;
import org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One entry per LETHAL weapon path that must also kill a Partygoer in one hit (owner rule 2026-10-21: anything that can
 * kill a player kills it; fists, tasers, shriek guns, the Wathe knife's left click, potions... never do). The lethal-only
 * twin of {@link DroneWeaponHits}: every accepted hit ends in {@link PartygoerService#killByWeapon} with the weapon's own
 * death reason. Rays are nearest-wins against the player target AND Bomber drones (whose own hooks run unchanged when no
 * Partygoer is strictly nearer); client-picked hits are re-validated on the server (weapon, distance, aim, sight).
 * 每条致命武器路径一个入口，这些路径也必须一击打死派对客（主人规则 2026-10-21：任何能杀死玩家的东西都能打死它；拳头、电击枪、
 * 啸音铳、Wathe 刀左键、药水……永远不能）。是 {@link DroneWeaponHits} 的“仅致命”孪生类：所有被接受的命中都以武器自身的死因汇入
 * {@link PartygoerService#killByWeapon}。射线对玩家目标与炸弹客无人机都遵循“最近者命中”（没有严格更近的派对客时，无人机自身钩子
 * 原样执行）；由客户端选定的命中会在服务端重新校验（武器、距离、瞄准、视线）。
 *
 * <p>Entry points (callers): Wathe bat (left-click callback, {@link DroneWeaponHits#PHASE}), Wathe revolver/derringer,
 * knife stab, NoellesRoles Demon Hunter pistol and throwing axe, SparkWitch shuriken (the drone mixins), Wathe grenade /
 * M67 / bomb drone blasts, Serial Killer pistols, Undercover tomahawk, SparkWitch kunai ({@code DroneWeaponHits}),
 * NoellesRoles timed bomb, poison needle and poison gas, SparkWitch seams (own mixins).
 * 入口（调用方）：Wathe 球棒（左键回调，PHASE 阶段）、Wathe 左轮/德林加、刀刺、NoellesRoles 猎魔枪与飞斧、SparkWitch 手里剑
 * （无人机 mixin）、Wathe 手雷 / M67 / 炸弹无人机爆炸、连环杀手手枪、卧底战术斧、SparkWitch 苦无（DroneWeaponHits）、
 * NoellesRoles 定时炸弹、毒针与毒气、SparkWitch 接缝（各自的 mixin）。</p>
 */
public final class PartygoerWeaponHits {
    /**
     * Thrown weapons with their own player-only (lethal) hit logic that also kill a Partygoer on contact; optional
     * entries, e.g. SparkWitch's shuriken. Lethal only: unlike the drone tag, nothing non-lethal may join it.
     * 自带只认玩家（致命）命中逻辑、接触派对客时也应将其击杀的投掷武器；条目可选，如 SparkWitch 手里剑。仅限致命武器：与无人机标签不同，
     * 不致命的东西不得加入。
     */
    public static final TagKey<EntityType<?>> PARTYGOER_KILLING_PROJECTILES =
            TagKey.of(RegistryKeys.ENTITY_TYPE, SparkStrength.id("partygoer_killing_projectiles"));
    private static final String SPARKWITCH_DEATH_REASONS = "dev.caecorthus.sparkwitch.SparkWitchDeathReasons";
    /** Resolved SparkWitch death reasons by field name (the provider's own instances when present). / 已解析的 SparkWitch 死因。 */
    private static final Map<String, Identifier> SPARKWITCH_REASONS = new ConcurrentHashMap<>();
    private static boolean registered;

    private PartygoerWeaponHits() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Same phase as the drone's left-click hook (ordered by DroneWeaponHits after the lock phases, before default):
        // only the Wathe bat answers here; every other item PASSes to its own handler (tomahawk, SparkWitch sword...).
        // 与无人机左键钩子同一阶段（由 DroneWeaponHits 排在锁定阶段之后、默认阶段之前）：这里只处理 Wathe 球棒；
        // 其余物品一律 PASS，交给各自的处理器（战术斧、SparkWitch 仪礼剑……）。
        AttackEntityCallback.EVENT.register(DroneWeaponHits.PHASE, PartygoerWeaponHits::onAttack);
    }

    // ---- Wathe bat (left click, both sides) / Wathe 球棒（左键，两端） ----

    /**
     * Wathe kills a player with a fully charged bat inside its player-only {@code PlayerEntity#attack} wrapper, which
     * drops any other target, so the bat is handled here. Client: SUCCESS makes Fabric send the attack packet and resets
     * the local charge exactly when the server will. Server: a full charge kills ({@code wathe:bat}), with Wathe's hit
     * sound unless the Silencer's quiet bat is swinging.
     * Wathe 在只认玩家的 PlayerEntity#attack 包装中用蓄满的球棒击杀玩家，会丢弃其他目标，因此球棒在此处理。客户端：SUCCESS 让 Fabric
     * 发送攻击包，并在服务端会重置时同步重置本地蓄力。服务端：蓄满即击杀（wathe:bat），静语者的静音球棒之外播放 Wathe 命中声。
     */
    static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity,
                                 @Nullable EntityHitResult hitResult) {
        if (!(entity instanceof PartygoerEntity partygoer) || player == null || player.isSpectator()
                || hand != Hand.MAIN_HAND || !player.getMainHandStack().isOf(WatheItems.BAT)
                || !PartygoerHitGeometry.isTargetable(partygoer)) {
            return ActionResult.PASS;
        }
        if (world.isClient()) {
            if (PartygoerWeaponRules.fullyCharged(player.getAttackCooldownProgress(0.5F))) {
                player.resetLastAttackedTicks();
            }
            return ActionResult.SUCCESS;
        }
        return player instanceof ServerPlayerEntity attacker && onBat(attacker, partygoer, player.getMainHandStack())
                ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    private static boolean onBat(ServerPlayerEntity attacker, PartygoerEntity partygoer, ItemStack bat) {
        if (!mayKill(attacker) || partygoer.getWorld() != attacker.getWorld()
                || SparkTraitsCompat.isMeleeActionBlocked(attacker, bat)
                || !PartygoerWeaponRules.fullyCharged(attacker.getAttackCooldownProgress(0.5F))
                // Vanilla checks reach only; re-check it and line of sight. / 原版只查距离，补查距离与视线。
                || !PartygoerHitGeometry.inMeleeReach(attacker, partygoer, PartygoerWeaponRules.MELEE_REACH)) {
            return false;
        }
        attacker.resetLastAttackedTicks();
        double x = partygoer.getX();
        double y = partygoer.getEyeY();
        double z = partygoer.getZ();
        if (!PartygoerService.killByWeapon(attacker, partygoer, GameConstants.DeathReasons.BAT)) {
            return false;
        }
        if (!SilencerQuietService.shouldSilenceWatheBat(attacker)) {
            attacker.getServerWorld().playSound(null, x, y, z, WatheSounds.ITEM_BAT_HIT, SoundCategory.PLAYERS,
                    3.0F, 1.0F);
        }
        return true;
    }

    // ---- Undercover tomahawk (called by UndercoverTomahawkService) / 卧底战术斧 ----

    /**
     * The tomahawk's stage-2, charge and cooldown gates were applied by {@code UndercoverTomahawkService}; this adds the
     * shared attacker gate, the SparkTraits weapon gate and reach + sight. No 小脑: a Partygoer is never an innocent.
     * 战术斧的第二阶段、蓄力与冷却门槛已由 UndercoverTomahawkService 检查；这里补上通用攻击者门槛、SparkTraits 武器门槛与距离和视线。
     * 不会小脑：派对客永远不是好人。
     */
    public static boolean onTomahawk(ServerPlayerEntity attacker, PartygoerEntity partygoer, ItemStack tomahawk,
                                     Identifier deathReason) {
        return mayKill(attacker) && partygoer.getWorld() == attacker.getWorld()
                && PartygoerHitGeometry.isTargetable(partygoer)
                && !SparkTraitsCompat.isMeleeActionBlocked(attacker, tomahawk)
                && PartygoerHitGeometry.inMeleeReach(attacker, partygoer, PartygoerWeaponRules.TOMAHAWK_REACH)
                && PartygoerService.killByWeapon(attacker, partygoer, deathReason);
    }

    // ---- Wathe knife right-click stab / Wathe 刀右键刺击 ----

    /**
     * Server stab receiver for a Partygoer id (every knife path sends Wathe's stab payload: the charged release, the
     * Veteran / Coroner instant stabs, the Silencer's quiet knife). Same checks as a drone stab, then the knife cooldown
     * a real stab costs (none for the Veteran / instant-knife Coroner disguise); no Veteran stab use is consumed.
     * 派对客 id 的服务端刀刺接收（所有刀路径都发送 Wathe 刀刺包：蓄力释放、老兵 / 验尸官瞬刺、静语者静音刀）。校验与刺无人机相同，
     * 之后按真实刺杀付出刀冷却（老兵 / 验尸官瞬刀伪装没有冷却）；不消耗老兵刺杀次数。
     */
    public static void onKnifeStab(ServerPlayerEntity attacker, PartygoerEntity partygoer) {
        if (attacker == null || attacker.isSpectator() || partygoer.getWorld() != attacker.getWorld()
                || !mayKill(attacker) || noellesRolesBlocksStab(attacker)
                || !PartygoerHitGeometry.isTargetable(partygoer)) {
            return;
        }
        Hand hand = attacker.getMainHandStack().getItem() instanceof KnifeItem ? Hand.MAIN_HAND
                : attacker.getOffHandStack().getItem() instanceof KnifeItem ? Hand.OFF_HAND : null;
        if (hand == null) {
            return;
        }
        ItemStack knife = attacker.getStackInHand(hand);
        if (attacker.getItemCooldownManager().isCoolingDown(knife.getItem())
                || SparkTraitsCompat.isMeleeActionBlocked(attacker, knife)
                || !PartygoerHitGeometry.inMeleeReach(attacker, partygoer,
                PartygoerWeaponRules.KNIFE_RANGE + PartygoerWeaponRules.KNIFE_REACH_TOLERANCE)) {
            return;
        }
        if (PartygoerService.killByWeapon(attacker, partygoer, GameConstants.DeathReasons.KNIFE)) {
            attacker.swingHand(hand, true);
            applyKnifeStabCooldown(attacker, knife);
        }
    }

    /**
     * Mirrors NoellesRoles' ShadowJesterKnifeMixin (our priority-900 stab hook runs before it): a Shadow Jester without
     * the real knife may only stab its duel partner, which a Partygoer never is; a betrayal trophy never stabs.
     * 镜像 NoellesRoles 的 ShadowJesterKnifeMixin（我们的优先级 900 刀刺钩子先于它执行）：没有真刀的影子小丑只能刺其决斗搭档
     * （派对客永远不是），背叛战利品永远无法刺击。
     */
    private static boolean noellesRolesBlocksStab(ServerPlayerEntity attacker) {
        ShadowJesterPlayerComponent shadow = ShadowJesterPlayerComponent.KEY.get(attacker);
        if (GameWorldComponent.KEY.get(attacker.getWorld()).isRole(attacker, Noellesroles.SHADOW_JESTER)) {
            return !shadow.isRealKnife();
        }
        return shadow.isBetrayalTrophy();
    }

    private static void applyKnifeStabCooldown(ServerPlayerEntity attacker, ItemStack knife) {
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getWorld());
        if (attacker.isCreative() || game.getGameMode() == WatheGameModes.LOOSE_ENDS
                || VeteranRules.isVeteran(game.getRole(attacker))
                || CoronerService.hasInstantSilentKnifeDisguise(attacker)) {
            return;
        }
        int cooldown = DroneWeaponRules.knifeCooldownTicks(
                GameConstants.ITEM_COOLDOWNS.getOrDefault(WatheItems.KNIFE, GameConstants.getInTicks(1, 0)),
                attacker.getServerWorld().getPlayers().size(),
                game.getAllKillerTeamPlayers().size(),
                game.getKillerDividend(),
                GameConstants.getInTicks(0, 10),
                GameConstants.getInTicks(0, 5));
        attacker.getItemCooldownManager().set(knife.getItem(), cooldown);
    }

    // ---- Client-picked guns / 客户端选目标的枪械 ----

    /**
     * Wathe revolver/derringer receiver at its {@code recordItemUse} anchor (after Wathe's own gates). A Partygoer id
     * never resolves to Wathe's player target, so Wathe then finishes the shot as a miss (sounds, derringer spent,
     * cooldown, no innocent penalty).
     * Wathe 左轮/德林加接收器的 recordItemUse 锚点（位于 Wathe 自身门槛之后）。派对客 id 永远不会被 Wathe 解析为玩家目标，
     * 因此 Wathe 随后按未命中完成这一枪（音效、德林加用尽、冷却、无误伤惩罚）。
     */
    public static void onGunShot(ServerPlayerEntity shooter, @Nullable Entity target) {
        if (shooter == null || !(target instanceof PartygoerEntity partygoer)) {
            return;
        }
        ItemStack gun = shooter.getMainHandStack();
        if (gun.isOf(WatheItems.REVOLVER) || gun.isOf(WatheItems.DERRINGER)) {
            killTargeted(shooter, partygoer, GameConstants.DeathReasons.GUN, PartygoerWeaponRules.GUN_MAX_DISTANCE);
        }
    }

    /**
     * NoellesRoles Demon Hunter pistol (after its pistol, cooldown and bullet checks). A Partygoer is a monster: no
     * Jester / frenzy rule. True = killed; the caller then resolves the target to null so NoellesRoles runs its miss path.
     * NoellesRoles 猎魔枪（位于其手持、冷却与子弹检查之后）。派对客是怪物：不适用小丑 / 疯魔规则。返回 true 表示已击杀，调用方随后把
     * 目标解析为 null，让 NoellesRoles 走未命中流程。
     */
    public static boolean onDemonHunterShot(ServerPlayerEntity shooter, @Nullable Entity target) {
        return shooter != null && target instanceof PartygoerEntity partygoer
                && killTargeted(shooter, partygoer, Noellesroles.DEATH_REASON_DEMON_HUNTER,
                PartygoerWeaponRules.GUN_MAX_DISTANCE);
    }

    /**
     * Serial Killer pistol (its service already checked the shooter, pistol, cooldown and Last Escape): a claimed
     * Partygoer id dies ({@code wathe:gun}); the shot is then finished as a recorded miss.
     * 连环杀手手枪（其服务已检查射手、手枪、冷却与脱险）：声明的派对客 id 被击杀（wathe:gun）；这一枪随后按空枪记录。
     */
    public static boolean onSerialPistolShot(ServerPlayerEntity shooter, @Nullable Entity target, double maxDistance) {
        return shooter != null && target instanceof PartygoerEntity partygoer
                && killTargeted(shooter, partygoer, GameConstants.DeathReasons.GUN, maxDistance);
    }

    private static boolean killTargeted(ServerPlayerEntity shooter, PartygoerEntity partygoer, Identifier deathReason,
                                        double maxDistance) {
        if (!mayKill(shooter) || partygoer.getWorld() != shooter.getWorld()
                || !PartygoerHitGeometry.isTargetable(partygoer)
                || shooter.distanceTo(partygoer) >= maxDistance
                || !PartygoerHitGeometry.gunAimedAndVisible(shooter.getWorld(), shooter.getEyePos(),
                shooter.getRotationVec(1.0F), maxDistance, partygoer, shooter)) {
            return false;
        }
        return PartygoerService.killByWeapon(shooter, partygoer, deathReason);
    }

    // ---- Use item: SparkWitch kunai (called first by DroneWeaponHits) / 使用物品：SparkWitch 苦无 ----

    /**
     * Kunai: its own use is a server raycast that only sees players, so a Partygoer strictly nearer than the player it
     * would hit (and than any drone on the ray) dies here; the kunai's use never runs, its kill cooldown applies.
     * False leaves the drone check and the kunai untouched.
     * 苦无：其使用是只看玩家的服务端射线，因此严格比其将命中的玩家（以及射线上任何无人机）更近的派对客在此被击杀；苦无本身不再执行，
     * 并计入其击杀冷却。返回 false 时无人机检查与苦无原样执行。
     */
    public static boolean onKunai(ServerPlayerEntity user, Hand hand, ItemStack kunai) {
        if (!mayKill(user) || user.getItemCooldownManager().isCoolingDown(kunai.getItem())
                || SparkTraitsCompat.isMeleeActionBlocked(user, kunai)) {
            return false;
        }
        double range = PartygoerWeaponRules.NINJA_KNIFE_RANGE;
        HitResult aimed = ProjectileUtil.getCollision(user, entity -> entity instanceof ServerPlayerEntity victim
                && victim != user && GameFunctions.isPlayerAliveAndSurvival(victim), range);
        if (!killNearestOnLook(user, range, aimed instanceof EntityHitResult hit ? hit.getEntity() : null,
                sparkWitchReason("NINJA_KNIFE_KILL", "ninja_knife_kill"), true)) {
            return false;
        }
        user.swingHand(hand, true);
        user.getItemCooldownManager().set(kunai.getItem(), PartygoerWeaponRules.NINJA_KNIFE_COOLDOWN_TICKS);
        return true;
    }

    // ---- Projectiles / 投射物 ----

    /**
     * Tagged thrown weapon (SparkWitch shuriken, whose own hit logic only kills players) met a Partygoer (server): kill
     * it and stop the projectile. True = handled.
     * 带标签的投掷武器（SparkWitch 手里剑，其命中逻辑只杀玩家）命中派对客（服务端）：将其击杀并让投射物停下。返回 true 表示已处理。
     */
    public static boolean onTaggedProjectileHit(ProjectileEntity projectile, PartygoerEntity partygoer) {
        Entity owner = projectile.getOwner();
        if (projectile.getWorld().isClient() || !projectile.getType().isIn(PARTYGOER_KILLING_PROJECTILES)
                || !PartygoerHitGeometry.targetableBy(owner, partygoer)) {
            return false;
        }
        String type = Registries.ENTITY_TYPE.getId(projectile.getType()).toString();
        Identifier reason = PartygoerWeaponRules.SHURIKEN_ENTITY_ID.equals(type)
                ? sparkWitchReason("NINJA_SHURIKEN_KILL", "ninja_shuriken_kill")
                : GameConstants.DeathReasons.GENERIC;
        if (!PartygoerService.killByWeapon(owner instanceof ServerPlayerEntity player ? player : null, partygoer,
                reason)) {
            return false;
        }
        projectile.discard();
        return true;
    }

    /**
     * NoellesRoles throwing axe segment for this tick: it pierces, so every Partygoer it enters before the first block
     * (and before the drone that stops it, {@code cutSquared}) dies and the axe keeps flying.
     * NoellesRoles 飞斧本刻路径：飞斧会贯穿，因此它在第一个方块之前（以及挡住它的无人机 cutSquared 之前）进入的每只派对客都被击杀，
     * 飞斧继续飞行。
     */
    public static void onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from, Vec3d to,
                                          double cutSquared) {
        if (axe == null || from == null || to == null || axe.getWorld().isClient()) {
            return;
        }
        Vec3d end = DroneHitGeometry.clipToBlocks(axe.getWorld(), from, to, axe);
        ServerPlayerEntity attacker = thrower instanceof ServerPlayerEntity player ? player : null;
        for (PartygoerHitGeometry.PartygoerHit hit : PartygoerHitGeometry.allBefore(axe.getWorld(), from, end,
                cutSquared, thrower)) {
            PartygoerService.killByWeapon(attacker, hit.partygoer(), Noellesroles.DEATH_REASON_THROWING_AXE);
        }
    }

    // ---- Blasts / 爆炸 ----

    /** Wathe grenade or M67 blast centred on the grenade (after its own kills). / Wathe 手雷或 M67 爆炸（在其自身击杀之后）。 */
    public static void onGrenadeBlast(ServerWorld world, Entity grenade, double radius,
                                      @Nullable ServerPlayerEntity thrower) {
        if (world != null && grenade != null) {
            onBlast(world, grenade.getBoundingBox().getCenter(), radius, grenade, thrower,
                    GameConstants.DeathReasons.GRENADE);
        }
    }

    /**
     * Every Partygoer caught in a blast dies with {@code deathReason} (feet or centre inside the sphere, visible from
     * the centre). Collected first so one kill never changes the set seen by the rest.
     * 爆炸范围内的每只派对客都以 deathReason 死亡（脚下或中心位于球内，且爆心可见）。先收集，避免一次击杀影响其余判定。
     */
    public static void onBlast(ServerWorld world, Vec3d center, double radius, @Nullable Entity context,
                               @Nullable ServerPlayerEntity cause, Identifier deathReason) {
        if (world == null || center == null || !(radius > 0.0) || !Double.isFinite(radius)) {
            return;
        }
        Box search = new Box(center, center).expand(radius + 2.0);
        List<PartygoerEntity> caught = world.getEntitiesByClass(PartygoerEntity.class, search,
                partygoer -> PartygoerHitGeometry.isTargetable(partygoer)
                        && PartygoerHitGeometry.caughtInBlast(world, center, radius, partygoer, context));
        for (PartygoerEntity partygoer : caught) {
            PartygoerService.killByWeapon(cause, partygoer, deathReason);
        }
    }

    // ---- NoellesRoles timed bomb, poison needle, poison gas / NoellesRoles 定时炸弹、毒针、毒气 ----

    /**
     * Timed bomb right-clicked on a Partygoer (server; the HEAD of {@code TimedBombItem#useOnEntity}, before its
     * players-only filter). The bomb would kill whoever carries it, so it goes off at once: a Bomber (or the Coroner's
     * Bomber disguise) spends the bomb; a carrier with a beeping bomb is rid of it. Null = leave NoellesRoles alone.
     * 对派对客右键使用定时炸弹（服务端；TimedBombItem#useOnEntity 的 HEAD，早于其只认玩家的过滤）。炸弹会炸死携带者，因此立即爆炸：
     * 炸弹客（或验尸官的炸弹客伪装）消耗这颗炸弹；携带滴答炸弹的人则就此脱手。返回 null 表示不干预 NoellesRoles。
     */
    public static @Nullable ActionResult onTimedBomb(PlayerEntity user, ItemStack stack, PartygoerEntity partygoer) {
        if (!(user instanceof ServerPlayerEntity bomber) || !mayKill(bomber)
                || bomber.getItemCooldownManager().isCoolingDown(stack.getItem())
                || !PartygoerHitGeometry.targetableBy(bomber, partygoer)) {
            return null;
        }
        BomberPlayerComponent carried = BomberPlayerComponent.KEY.get(bomber);
        if (carried.isBeeping()) {
            if (!carried.hasBomb() || !PartygoerService.killByWeapon(bomber, partygoer,
                    Noellesroles.DEATH_REASON_BOMB)) {
                return null;
            }
            // Handed on to a Partygoer: clear the carrier's bomb state and item. / 交给派对客：清除携带者的炸弹状态与物品。
            carried.reset();
            return ActionResult.CONSUME;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(bomber.getWorld());
        if (!game.isRole(bomber, Noellesroles.BOMBER) && !CoronerService.hasBomberDisguise(bomber)) {
            return null;
        }
        if (!PartygoerService.killByWeapon(bomber, partygoer, Noellesroles.DEATH_REASON_BOMB)) {
            return null;
        }
        stack.decrementUnlessCreative(1, bomber);
        return ActionResult.CONSUME;
    }

    /**
     * Poison needle right-clicked on a Partygoer (HEAD of {@code PoisonNeedleItem#useOnEntity}). Client: swing like a
     * stab on a player. Server: a Poisoner (or the Coroner's Poisoner disguise) kills it at once ({@code wathe:poison})
     * and the needle cools down 45 s. Null = leave NoellesRoles alone.
     * 对派对客右键使用毒针（PoisonNeedleItem#useOnEntity 的 HEAD）。客户端：与刺玩家一样挥手。服务端：毒师（或验尸官的毒师伪装）立即
     * 将其毒死（wathe:poison），毒针冷却 45 秒。返回 null 表示不干预 NoellesRoles。
     */
    public static @Nullable ActionResult onPoisonNeedle(PlayerEntity user, Item needle, PartygoerEntity partygoer) {
        if (user == null || user.getItemCooldownManager().isCoolingDown(needle)
                || !PartygoerHitGeometry.targetableBy(user, partygoer)) {
            return null;
        }
        if (user.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity poisoner) || !mayKill(poisoner)) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(poisoner.getWorld());
        if (!game.isRole(poisoner, Noellesroles.POISONER) && !CoronerService.hasPoisonerDisguise(poisoner)) {
            return null;
        }
        if (!PartygoerService.killByWeapon(poisoner, partygoer, GameConstants.DeathReasons.POISON)) {
            return null;
        }
        poisoner.getItemCooldownManager().set(needle, PartygoerWeaponRules.POISON_NEEDLE_COOLDOWN_TICKS);
        return ActionResult.SUCCESS;
    }

    /**
     * NoellesRoles poison gas cloud tick (server, cloud alive): a Partygoer whose box touches a gas block dies at once
     * ({@code wathe:poison}), credited to the cloud's owner when online. Same block test as the cloud's player check.
     * NoellesRoles 毒气云每刻（服务端，云仍存在）：碰撞箱接触毒气方块的派对客立即死亡（wathe:poison），主人在线时记在主人头上。
     * 方块判定与毒气云对玩家的判定相同。
     */
    public static void onPoisonGasTick(ServerWorld world, @Nullable Set<BlockPos> gasBlocks, @Nullable UUID ownerUuid) {
        if (world == null || gasBlocks == null || gasBlocks.isEmpty()) {
            return;
        }
        ServerPlayerEntity owner = null;
        boolean ownerResolved = false;
        for (PartygoerEntity partygoer : PartygoerService.live(world)) {
            if (!PartygoerHitGeometry.isTargetable(partygoer) || !inGas(partygoer.getBoundingBox(), gasBlocks)) {
                continue;
            }
            if (!ownerResolved) {
                ownerResolved = true;
                owner = ownerUuid == null ? null : world.getServer().getPlayerManager().getPlayer(ownerUuid);
            }
            PartygoerService.killByWeapon(owner, partygoer, GameConstants.DeathReasons.POISON);
        }
    }

    private static boolean inGas(Box box, Set<BlockPos> gasBlocks) {
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY); y++) {
                for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
                    if (gasBlocks.contains(new BlockPos(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- SparkWitch seams (via @Pseudo mixins) / SparkWitch 接缝（经 @Pseudo mixin） ----

    /**
     * Hunter shotgun ({@code SeekerDeviceHits.onShotgunFired}): a Partygoer strictly nearer than the target and any drone
     * dies ({@code wathe:gun}); true = absorbed, the caller hits nobody.
     * 猎人猎枪：严格近于目标与任何无人机的派对客死亡（wathe:gun）；返回 true 表示已吸收，调用方不再命中任何人。
     */
    public static boolean onSparkWitchShotgun(@Nullable PlayerEntity shooter, @Nullable PlayerEntity target,
                                              double range) {
        return shooter instanceof ServerPlayerEntity user && !PartygoerCarry.isCarried(user)
                && killNearestOnLook(user, range, target, GameConstants.DeathReasons.GUN, true);
    }

    /**
     * Black Raven feather blade ({@code onFeatherBladeFired}, which SparkWitch calls before its Black Raven check): the
     * blade's mark kills after 20 s, so a nearer Partygoer dies at once ({@code wathe:knife}) and the blade cools down as
     * after a mark. True = absorbed (SparkWitch then marks nobody and sets no cooldown itself).
     * 黑鸦羽刃（SparkWitch 在黑羽鸦身份检查之前调用 onFeatherBladeFired）：羽刃的标记 20 秒后致死，因此更近的派对客立即死亡
     * （wathe:knife），羽刃与标记后一样进入冷却。返回 true 表示已吸收（SparkWitch 随后不标记任何人，也不自行设冷却）。
     */
    public static boolean onSparkWitchFeatherBlade(@Nullable ServerPlayerEntity user, @Nullable PlayerEntity target,
                                                   double range) {
        if (user == null || !mayKill(user) || !hasRole(user, PartygoerWeaponRules.BLACK_RAVEN_ROLE_ID)) {
            return false;
        }
        Item blade = heldItem(user, PartygoerWeaponRules.FEATHER_BLADE_ID);
        if (blade == null || !killNearestOnLook(user, range, target, GameConstants.DeathReasons.KNIFE, true)) {
            return false;
        }
        user.getItemCooldownManager().set(blade, PartygoerWeaponRules.FEATHER_COOLDOWN_TICKS);
        return true;
    }

    /**
     * Time Stealer clock ({@code onClockFired}, reached only when a player was aimed): a nearer Partygoer takes the
     * curse, which kills, so it dies now ({@code sparkwitch:time_stolen}); true = absorbed, and SparkWitch then starts
     * the clock's cooldown itself. Drones never stop the clock, so they are not measured.
     * 窃时者时钟（onClockFired，仅在瞄准到玩家时才会调用）：更近的派对客承受会致死的诅咒，因此立即死亡（sparkwitch:time_stolen）；
     * 返回 true 表示已吸收，SparkWitch 随后自行开始时钟冷却。无人机从不挡下时钟，因此不参与测量。
     */
    public static boolean onSparkWitchClock(@Nullable ServerPlayerEntity user, @Nullable PlayerEntity target,
                                            double range) {
        return user != null && !PartygoerCarry.isCarried(user)
                && killNearestOnLook(user, range, target, sparkWitchReason("TIME_STOLEN", "time_stolen"), false);
    }

    /**
     * Murderous Witch death ray (piercing): every Partygoer before {@code cutDistance} (the ray already cut at the
     * nearest drone) dies; the ray goes on. / 杀意魔女死光（穿透）：cutDistance（已在最近的无人机处截断）之前的每只派对客都死亡；
     * 射线继续前进。
     */
    public static void onSparkWitchDeathRay(@Nullable ServerPlayerEntity user, @Nullable Vec3d start,
                                            @Nullable Vec3d direction, double cutDistance) {
        if (user == null || start == null || direction == null || !(cutDistance > 0.0)
                || !Double.isFinite(cutDistance) || direction.lengthSquared() == 0.0
                || PartygoerCarry.isCarried(user)) {
            return;
        }
        Vec3d end = start.add(direction.normalize().multiply(cutDistance));
        Identifier reason = sparkWitchReason("PIERCED_BY_RAY", "pierced_by_ray");
        for (PartygoerHitGeometry.PartygoerHit hit : PartygoerHitGeometry.allBefore(user.getWorld(), start, end,
                Double.POSITIVE_INFINITY, user)) {
            PartygoerService.killByWeapon(user, hit.partygoer(), reason);
        }
    }

    /**
     * Potion launcher backblast lane: a Partygoer strictly nearer than {@code reach} (the victim's distance, or the lane
     * length) and than any drone takes it ({@code sparkwitch:potion_backblast}). True = absorbed.
     * 炮筒尾焰通道：严格近于 reach（受害者距离或通道长度）且近于任何无人机的派对客承受尾焰（sparkwitch:potion_backblast）。
     */
    public static boolean onSparkWitchBackblast(@Nullable ServerPlayerEntity gunner, @Nullable Vec3d start,
                                                @Nullable Vec3d direction, double reach) {
        if (gunner == null || start == null || direction == null || !(reach > 0.0) || !Double.isFinite(reach)
                || direction.lengthSquared() == 0.0 || PartygoerCarry.isCarried(gunner)) {
            return false;
        }
        Vec3d end = DroneHitGeometry.clipToBlocks(gunner.getWorld(), start,
                start.add(direction.normalize().multiply(reach)), gunner);
        return killNearestOnSegment(gunner, start, end, reach * reach,
                sparkWitchReason("POTION_BACKBLAST", "potion_backblast"), true);
    }

    /**
     * USEC sniper rifle match shot ({@code onUsecRifleFired}): walks the bullet's polyline (already bent and cut by
     * SparkWitch) and lets the first Partygoer OR drone it enters strictly before {@code reach} win. Only a Partygoer win
     * acts here (it dies, {@code wathe:gun}, and its path distance is returned); a drone win returns {@code reach}
     * unchanged so the drone hook breaks it exactly as before.
     * USEC 狙击步枪对局射击：沿子弹折线（已由 SparkWitch 处理下坠与截断）遍历，让其在 reach 之前严格先进入的第一只派对客或第一架无人机
     * 获胜。只有派对客获胜时在此处理（其死亡，wathe:gun，并返回其路径距离）；无人机获胜时原样返回 reach，由无人机钩子照旧击毁。
     */
    public static double onSparkWitchUsecRifle(@Nullable ServerPlayerEntity shooter, @Nullable List<Vec3d> path,
                                               double reach) {
        if (shooter == null || path == null || path.size() < 2 || !(reach > 0.0) || !Double.isFinite(reach)
                || shooter.getWorld().isClient() || !GameWorldComponent.KEY.get(shooter.getWorld()).isRunning()
                || PartygoerCarry.isCarried(shooter)) {
            return reach;
        }
        World world = shooter.getWorld();
        double[] lengths = new double[path.size() - 1];
        for (int index = 0; index < lengths.length; index++) {
            Vec3d from = path.get(index);
            Vec3d to = path.get(index + 1);
            lengths[index] = from == null || to == null ? Double.NaN : from.distanceTo(to);
        }
        PartygoerHitGeometry.PartygoerHit[] winner = new PartygoerHitGeometry.PartygoerHit[1];
        double distance = DroneWeaponRules.firstPathEntry(lengths, reach, (index, cut) -> {
            Vec3d from = path.get(index);
            Vec3d to = path.get(index + 1);
            Vec3d end = cut < lengths[index] ? from.add(to.subtract(from).multiply(cut / lengths[index])) : to;
            PartygoerHitGeometry.PartygoerHit hit = PartygoerHitGeometry.nearest(world, from, end, cut * cut, shooter);
            double partygoer = hit == null ? -1.0 : hit.distanceSquared();
            double drone = PartygoerHitGeometry.droneDistanceSquared(world, from, end, cut * cut);
            winner[0] = PartygoerWeaponRules.partygoerWins(partygoer, cut * cut, drone) ? hit : null;
            return PartygoerWeaponRules.firstEntryDistance(partygoer, drone);
        });
        // The walk stops at the accepted probe, so winner[0] is that segment's Partygoer (null when a drone won).
        // 遍历止于被接受的探测，winner[0] 即该段的派对客（无人机获胜时为 null）。
        if (!(distance >= 0.0) || winner[0] == null
                || !PartygoerService.killByWeapon(shooter, winner[0].partygoer(), GameConstants.DeathReasons.GUN)) {
            return reach;
        }
        return distance;
    }

    /**
     * Witch Maiden crimson wither skull ({@code MagicianPuppetHits.onCrimsonSkullHit}, reached for a non-player entity
     * hit): a Partygoer dies ({@code sparkwitch:crimson_wither_skull}); true = the skull is spent there.
     * 魔女少女的猩红凋零头颅（对非玩家实体命中时调用）：派对客死亡（sparkwitch:crimson_wither_skull）；返回 true 表示头颅就此消耗。
     */
    public static boolean onCrimsonSkullHit(@Nullable Entity skull, @Nullable Entity owner,
                                            @Nullable EntityHitResult hit) {
        if (skull == null || hit == null || skull.getWorld().isClient()
                || !(hit.getEntity() instanceof PartygoerEntity partygoer)
                || !PartygoerHitGeometry.targetableBy(owner, partygoer)) {
            return false;
        }
        return PartygoerService.killByWeapon(owner instanceof ServerPlayerEntity player ? player : null, partygoer,
                sparkWitchReason("CRIMSON_WITHER_SKULL", "crimson_wither_skull"));
    }

    /**
     * Grand Witch ceremonial sword, fully charged left click ({@code MagicianPuppetHits.onCeremonialSwordStrike}, which
     * SparkWitch reaches for any non-player target): a Partygoer in reach dies as a sword kill.
     * 大魔女仪礼剑蓄满左键（SparkWitch 对任何非玩家目标都会调用该接缝）：距离内的派对客按一次剑杀死亡。
     */
    public static boolean onCeremonialSwordStrike(@Nullable ServerPlayerEntity attacker, @Nullable Entity target) {
        if (attacker == null || attacker.getWorld().isClient() || !(target instanceof PartygoerEntity partygoer)
                || !PartygoerWeaponRules.CEREMONIAL_SWORD_ID.equals(
                Registries.ITEM.getId(attacker.getMainHandStack().getItem()).toString())
                || !mayKill(attacker) || partygoer.getWorld() != attacker.getWorld()
                || !PartygoerHitGeometry.isTargetable(partygoer)
                || !PartygoerHitGeometry.inMeleeReach(attacker, partygoer, PartygoerWeaponRules.MELEE_REACH)) {
            return false;
        }
        return ceremonialSwordKill(attacker, partygoer);
    }

    /**
     * Ceremonial sword dash step ({@code MagicianPuppetHits.onCeremonialSwordDash}, after the step's player contact
     * check): the nearest Partygoer the step's swept box meets is a dash contact like a player, so the dash stops there
     * (true) and it dies as a sword kill when one may be attempted now.
     * 仪礼剑冲刺的每一步（在该步的玩家接触检查之后）：该步扫掠箱碰到的最近派对客与玩家一样构成冲刺接触，冲刺就此停下（返回 true），
     * 此刻可尝试剑杀时它按一次剑杀死亡。
     */
    public static boolean onCeremonialSwordDash(@Nullable ServerPlayerEntity attacker, @Nullable Box sweptBox) {
        if (attacker == null || sweptBox == null || !(attacker.getWorld() instanceof ServerWorld world)
                || PartygoerCarry.isCarried(attacker)) {
            return false;
        }
        PartygoerEntity contact = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (PartygoerEntity partygoer : world.getEntitiesByClass(PartygoerEntity.class, sweptBox.expand(1.0),
                PartygoerHitGeometry::isTargetable)) {
            double distance = partygoer.squaredDistanceTo(attacker);
            if (distance < nearest && partygoer.getBoundingBox().intersects(sweptBox)) {
                contact = partygoer;
                nearest = distance;
            }
        }
        if (contact == null) {
            return false;
        }
        if (mayKill(attacker)) {
            ceremonialSwordKill(attacker, contact);
        }
        return true;
    }

    /**
     * A sword kill on a Partygoer, gated and paid like SparkWitch's own (no sword kill cooldown, no strike in progress,
     * the SparkTraits weapon gate; then the 30 s sword kill cooldown and the trident hit sound). The Grand Witch runtime
     * is read by its CCA key (no compile dependency); when it can't be read the kill still happens, uncharged.
     * 对派对客的一次剑杀，门槛与代价同 SparkWitch 自身（无剑杀冷却、无进行中的攻击、SparkTraits 武器门槛；之后 30 秒剑杀冷却与三叉戟
     * 命中声）。大魔女运行时按其 CCA key 读取（无编译期依赖）；读取失败时仍然击杀，但不计冷却。
     */
    private static boolean ceremonialSwordKill(ServerPlayerEntity attacker, PartygoerEntity partygoer) {
        Item sword = heldItem(attacker, PartygoerWeaponRules.CEREMONIAL_SWORD_ID);
        if (!GrandWitchSword.mayStrike(attacker)
                || sword != null && SparkTraitsCompat.isMeleeActionBlocked(attacker, new ItemStack(sword))) {
            return false;
        }
        Vec3d at = partygoer.getPos();
        if (!PartygoerService.killByWeapon(attacker, partygoer,
                sparkWitchReason("CEREMONIAL_BLADE", "ceremonial_blade"))) {
            return false;
        }
        GrandWitchSword.startKillCooldown(attacker);
        attacker.getServerWorld().playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_TRIDENT_HIT,
                SoundCategory.PLAYERS, 1.0F, 0.8F);
        return true;
    }

    /**
     * Potion Gunner shell blast ({@code MagicianPuppetHits.onPotionShellBlast}, after the Seeker blast and before the
     * shell's own targets). Only the TR shell kills (its item names the type): every Partygoer the player rule would
     * catch dies — feet in the 5×5×5 cube, clear line from the centre to its feet, body centre or eyes
     * ({@code sparkwitch:potion_shell}). In flight the shell already stops at a Partygoer (any non-player living entity
     * stops it), so a direct hit bursts on it.
     * 药炮手炮弹爆炸（在搜寻者爆炸之后、炮弹自身目标之前）。只有 TR 炮弹致死（由其物品判断类型）：玩家规则会波及的每只派对客都死亡——
     * 脚下位于 5×5×5 立方体内，且爆心到其脚部、身体中心或眼睛有一条无遮挡线段（sparkwitch:potion_shell）。飞行中的炮弹本就会停在
     * 派对客身上（任何非玩家生物都会挡下它），因此直接命中时在其身上爆炸。
     */
    public static void onPotionShellBlast(@Nullable ServerWorld world, @Nullable Vec3d center,
                                          @Nullable ServerPlayerEntity gunner, @Nullable ItemStack shell) {
        if (world == null || center == null || shell == null || shell.isEmpty()
                || !PartygoerWeaponRules.TR_SHELL_ID.equals(Registries.ITEM.getId(shell.getItem()).toString())
                || !GameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        Identifier reason = sparkWitchReason("POTION_SHELL", "potion_shell");
        for (PartygoerEntity partygoer : PartygoerService.live(world)) {
            Box box = partygoer.getBoundingBox();
            if (!PartygoerHitGeometry.isTargetable(partygoer)
                    || !PartygoerWeaponRules.inShellCube(PartygoerWeaponRules.TR_SHELL_SIZE,
                    partygoer.getX() - center.x, partygoer.getZ() - center.z, box.minY - center.y,
                    box.maxY - center.y)) {
                continue;
            }
            if (DroneHitGeometry.segmentClear(world, center, partygoer.getPos(), null)
                    || DroneHitGeometry.segmentClear(world, center, box.getCenter(), null)
                    || DroneHitGeometry.segmentClear(world, center, partygoer.getEyePos(), null)) {
                PartygoerService.killByWeapon(gunner, partygoer, reason);
            }
        }
    }

    // ---- Shared / 公共 ----

    /**
     * Attackers we are the authority for must be live round participants in survival, not Last-Escape blocked, not
     * inside a pending SparkTraits Last Stand (our callback phase runs before the default phase where SparkTraits
     * enforces that lock) and not carried by a Partygoer (a carried player can't hit one).
     * 由我们裁定的攻击者必须是存活、生存模式的对局参与者，未被最后逃脱阻止，不处于 SparkTraits 背水一战待决中（我们的回调阶段早于
     * SparkTraits 执行该锁的默认阶段），且没有被派对客扛着（被扛着的人不能打派对客）。
     */
    public static boolean mayKill(@Nullable ServerPlayerEntity player) {
        return player != null && !player.isRemoved() && player.isAlive()
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !SparkTraitsCompat.isKillerInteractionBlocked(player)
                && !SparkTraitsDroneCompat.isLastStandPending(player)
                && !PartygoerCarry.isCarried(player);
    }

    /**
     * Ray along the user's look ({@code range}, cut at the first block): the nearest Partygoer strictly nearer than the
     * target (and, when {@code dronesBlock}, than any drone) dies. / 沿视线的射线（range，止于第一个方块）：严格近于目标
     * （dronesBlock 时还须近于任何无人机）的最近派对客死亡。
     */
    private static boolean killNearestOnLook(ServerPlayerEntity user, double range, @Nullable Entity target,
                                             Identifier deathReason, boolean dronesBlock) {
        if (!(range > 0.0) || !Double.isFinite(range)) {
            return false;
        }
        Vec3d start = user.getEyePos();
        Vec3d end = DroneHitGeometry.clipToBlocks(user.getWorld(), start,
                start.add(user.getRotationVec(1.0F).multiply(range)), user);
        double beat = target == null ? Double.POSITIVE_INFINITY
                : DroneHitGeometry.targetDistanceSquared(start, end, target.getBoundingBox());
        return killNearestOnSegment(user, start, end, beat, deathReason, dronesBlock);
    }

    private static boolean killNearestOnSegment(ServerPlayerEntity user, Vec3d start, Vec3d end, double beat,
                                                Identifier deathReason, boolean dronesBlock) {
        PartygoerHitGeometry.PartygoerHit hit = PartygoerHitGeometry.nearest(user.getWorld(), start, end, beat, user);
        if (hit == null) {
            return false;
        }
        double drone = dronesBlock ? PartygoerHitGeometry.droneDistanceSquared(user.getWorld(), start, end, beat)
                : Double.POSITIVE_INFINITY;
        return PartygoerWeaponRules.partygoerWins(hit.distanceSquared(), beat, drone)
                && PartygoerService.killByWeapon(user, hit.partygoer(), deathReason);
    }

    private static boolean hasRole(ServerPlayerEntity player, String roleId) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && role.identifier() != null && roleId.equals(role.identifier().toString());
    }

    /** The held item (main hand first) with this registry id, or null. / 手持（先主手）的该 id 物品，或 null。 */
    private static @Nullable Item heldItem(PlayerEntity player, String id) {
        for (Hand hand : Hand.values()) {
            Item item = player.getStackInHand(hand).getItem();
            if (id.equals(Registries.ITEM.getId(item).toString())) {
                return item;
            }
        }
        return null;
    }

    /**
     * SparkWitch death reason by field name: the provider's own static instance when SparkWitch is loaded, else an
     * equal identifier (only the Partygoer's corpse reads it, by equality).
     * 按字段名取 SparkWitch 死因：SparkWitch 已加载时使用其自身的静态实例，否则使用相等的标识符（只有派对客尸体读取，按 equals 比较）。
     */
    static Identifier sparkWitchReason(String field, String path) {
        return SPARKWITCH_REASONS.computeIfAbsent(field, key -> {
            try {
                Object value = Class.forName(SPARKWITCH_DEATH_REASONS).getField(key).get(null);
                if (value instanceof Identifier identifier) {
                    return identifier;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // SparkWitch absent or changed: fall back to the plain id. / SparkWitch 缺失或变更：退回普通 id。
            }
            return Identifier.of("sparkwitch", path);
        });
    }

    /**
     * Reflective view of SparkWitch's Grand Witch runtime component ({@code getSwordKillCooldownTicks},
     * {@code isSwordStrikeInProgress}, {@code setSwordKillCooldownTicks}), looked up by its CCA key. Fails open.
     * 通过 CCA key 反射访问 SparkWitch 大魔女运行时组件。失败时放行。
     */
    private static final class GrandWitchSword {
        private static final Map<Class<?>, Method[]> METHODS = new ConcurrentHashMap<>();

        private GrandWitchSword() {
        }

        static boolean mayStrike(ServerPlayerEntity player) {
            Object runtime = runtime(player);
            Method[] methods = runtime == null ? null : methods(runtime.getClass());
            if (methods == null) {
                return true;
            }
            try {
                return ((Integer) methods[0].invoke(runtime)) <= 0 && !((Boolean) methods[1].invoke(runtime));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return true;
            }
        }

        static void startKillCooldown(ServerPlayerEntity player) {
            Object runtime = runtime(player);
            Method[] methods = runtime == null ? null : methods(runtime.getClass());
            if (methods == null) {
                return;
            }
            try {
                methods[2].invoke(runtime, PartygoerWeaponRules.CEREMONIAL_SWORD_KILL_COOLDOWN_TICKS);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Uncharged kill. / 不计冷却。
            }
        }

        private static @Nullable Object runtime(ServerPlayerEntity player) {
            try {
                ComponentKey<?> key = ComponentRegistry.get(Identifier.of(PartygoerWeaponRules.GRAND_WITCH_RUNTIME_ID));
                return key == null ? null : key.getNullable(player);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        /** The three accessors, or null when SparkWitch changed them. / 三个访问方法；SparkWitch 变更时为 null。 */
        private static Method[] methods(Class<?> type) {
            Method[] methods = METHODS.computeIfAbsent(type, owner -> {
                try {
                    return new Method[] {
                            owner.getMethod("getSwordKillCooldownTicks"),
                            owner.getMethod("isSwordStrikeInProgress"),
                            owner.getMethod("setSwordKillCooldownTicks", int.class)
                    };
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    return new Method[0];
                }
            });
            return methods.length == 3 ? methods : null;
        }
    }
}
