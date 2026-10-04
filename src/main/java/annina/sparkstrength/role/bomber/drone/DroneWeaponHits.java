package annina.sparkstrength.role.bomber.drone;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.coroner.CoronerService;
import annina.sparkstrength.role.veteran.VeteranRules;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.GrenadeEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.item.KnifeItem;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One entry per weapon path that can break a drone ("any weapon breaks it; fists and non-weapons don't"). Every
 * accepted hit ends in {@link DroneCombatService#destroy}. Ray entries are nearest-wins: a drone strictly nearer than
 * the player target absorbs the hit and shields that player. Client-picked hits (knife stab, Wathe guns, Demon Hunter
 * pistol) are re-validated on the server (weapon, reach/distance, aim, line of sight) like SparkWitch's Seeker devices.
 * 每条能击毁无人机的武器路径一个入口（“任何武器都能击毁，空手与非武器不能”）。所有被接受的命中都汇入
 * {@link DroneCombatService#destroy}。射线入口遵循“最近者命中”：严格比玩家目标更近的无人机吸收命中并挡住该玩家。
 * 由客户端选定的命中（刀刺、Wathe 枪械、猎魔枪）会在服务端重新校验（武器、距离、瞄准、视线），做法同 SparkWitch 搜寻者设备。
 */
public final class DroneWeaponHits {
    /**
     * Fabric callback phase for the left-click and use-item hooks: after SparkWitch's lock phases (session, stun,
     * rift; created on demand, inert without SparkWitch) and before the default phase, where SparkWitch's ceremonial
     * sword answers SUCCESS and its shuriken FAIL for any entity.
     * 左键与使用物品钩子的 Fabric 回调阶段：在 SparkWitch 各锁定阶段（会话、眩晕、裂隙；按需创建，未安装 SparkWitch 时无效）之后、
     * 默认阶段之前（默认阶段中 SparkWitch 仪礼剑对任何实体返回 SUCCESS，手里剑返回 FAIL）。
     */
    public static final Identifier PHASE = SparkStrength.id("drone_weapon");
    private static final Identifier[] LOCK_PHASES = {
            Identifier.of("sparkwitch", "seeker_session_lock"),
            Identifier.of("sparkwitch", "control_expert_stun"),
            Identifier.of("sparkwitch", "rift_session_lock")
    };
    /**
     * Thrown weapons with their own (player-only) hit logic that should still break a drone on contact; optional
     * entries, e.g. SparkWitch's shuriken. Arrows, tridents and fireballs break drones through damage() already.
     * 自带（仅玩家）命中逻辑、但接触无人机时仍应将其击毁的投掷武器；条目可选，如 SparkWitch 手里剑。
     * 箭、三叉戟与火球已经通过 damage() 击毁无人机。
     */
    public static final TagKey<EntityType<?>> DRONE_BREAKING_PROJECTILES =
            TagKey.of(RegistryKeys.ENTITY_TYPE, SparkStrength.id("drone_breaking_projectiles"));

    // Server thread only. / 仅服务器线程。
    private static final Map<UUID, DashWatch> DASH_WATCHES = new HashMap<>();
    private static boolean registered;

    private DroneWeaponHits() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        order(AttackEntityCallback.EVENT);
        order(UseItemCallback.EVENT);
        AttackEntityCallback.EVENT.register(PHASE, DroneWeaponHits::onAttack);
        UseItemCallback.EVENT.register(PHASE, DroneWeaponHits::onUseItem);
        ServerTickEvents.END_SERVER_TICK.register(DroneWeaponHits::tickDashWatches);
    }

    private static void order(Event<?> event) {
        for (Identifier lock : LOCK_PHASES) {
            event.addPhaseOrdering(lock, PHASE);
        }
        event.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
    }

    // ---- Left click (both sides) / 左键（两端） ----

    /**
     * AttackEntityCallback fires at the head of the attack, before Wathe's player-only {@code PlayerEntity#attack}
     * wrapper (which would drop any non-player target). Client: a weapon on a drone answers SUCCESS so Fabric sends
     * the attack packet itself, resetting the local attack charge exactly when the server will; anything else PASSes.
     * Server: a weapon breaks the drone at full charge (the Wathe bat's rule); a non-weapon FAILs so even creative fists
     * never reach vanilla damage.
     * AttackEntityCallback 在攻击开头触发，早于 Wathe 只认玩家的 PlayerEntity#attack 包装（它会丢弃非玩家目标）。
     * 客户端：持武器攻击无人机返回 SUCCESS，由 Fabric 自行发送攻击包，并在服务端会重置时同步重置本地蓄力；其余返回 PASS。
     * 服务端：武器在蓄满时击毁无人机（Wathe 球棒规则）；非武器返回 FAIL，连创造模式空手也到不了原版伤害。
     */
    static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity,
                                 @Nullable EntityHitResult hitResult) {
        if (!(entity instanceof DroneEntity drone) || player == null || player.isSpectator()) {
            return ActionResult.PASS;
        }
        ItemStack weapon = player.getStackInHand(hand);
        boolean weaponHeld = DroneCombatService.isMeleeWeapon(weapon);
        if (world.isClient()) {
            if (!weaponHeld) {
                return ActionResult.PASS;
            }
            if (player.getAttackCooldownProgress(0.5F) >= 1.0F) {
                player.resetLastAttackedTicks();
            }
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity attacker) || !weaponHeld) {
            return ActionResult.FAIL;
        }
        return onMelee(attacker, drone, weapon) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    private static boolean onMelee(ServerPlayerEntity attacker, DroneEntity drone, ItemStack weapon) {
        if (!DroneCombatService.usable(drone) || drone.getWorld() != attacker.getWorld() || !mayBreak(attacker)
                || SparkTraitsCompat.isMeleeActionBlocked(attacker, weapon)
                // A knife that may not stab here (Shadow Jester fake knife, betrayal trophy) may not swing at drones
                // either. / 此处不可刺击的刀（影子小丑假刀、背叛战利品）同样不能挥砍无人机。
                || weapon.getItem() instanceof KnifeItem && noellesRolesBlocksStab(attacker)) {
            return false;
        }
        // Vanilla checks reach only; re-check it and line of sight against forged packets. / 原版只查距离，补查距离与视线。
        Vec3d eye = attacker.getEyePos();
        if (!DroneWeaponRules.withinReach(DroneHitGeometry.squaredDistanceToBox(eye, DroneHitGeometry.targetBox(drone)),
                DroneWeaponRules.MELEE_REACH)
                || !DroneHitGeometry.hasLineOfSight(attacker.getWorld(), eye, drone.getBoundingBox(), attacker)
                || attacker.getAttackCooldownProgress(0.5F) < 1.0F) {
            return false;
        }
        attacker.resetLastAttackedTicks();
        DroneCombatService.destroy(drone, attacker);
        return true;
    }

    // ---- Wathe knife right-click stab / Wathe 刀右键刺击 ----

    /**
     * Server stab receiver for a drone id (the client pick comes from the widened {@code KnifeItem#getKnifeTarget}).
     * Validates a Wathe knife in either hand, no knife cooldown, the SparkTraits weapon-action gate, reach (3 + 0.5,
     * eye to box) and line of sight, then breaks the drone and costs the knife cooldown a real stab would (none for the
     * Veteran / instant-knife Coroner disguise, whose stabs never set one). No Veteran stab use is consumed.
     * 无人机 id 的服务端刀刺接收（客户端目标来自扩展后的 KnifeItem#getKnifeTarget）。校验任一手持 Wathe 刀、刀未冷却、
     * SparkTraits 武器动作门槛、距离（眼到箱体 3 + 0.5）与视线，然后击毁无人机，并按真实刺杀付出刀冷却
     * （老兵/验尸官瞬刀伪装的刺杀本就没有冷却）。不消耗老兵刺杀次数。
     */
    public static void onKnifeStab(ServerPlayerEntity attacker, DroneEntity drone) {
        if (attacker == null || attacker.isSpectator() || !DroneCombatService.usable(drone)
                || drone.getWorld() != attacker.getWorld() || !mayBreak(attacker) || noellesRolesBlocksStab(attacker)) {
            return;
        }
        Hand hand = attacker.getMainHandStack().getItem() instanceof KnifeItem ? Hand.MAIN_HAND
                : attacker.getOffHandStack().getItem() instanceof KnifeItem ? Hand.OFF_HAND : null;
        if (hand == null) {
            return;
        }
        ItemStack knife = attacker.getStackInHand(hand);
        double reach = DroneWeaponRules.KNIFE_RANGE + DroneWeaponRules.KNIFE_REACH_TOLERANCE;
        Vec3d eye = attacker.getEyePos();
        if (attacker.getItemCooldownManager().isCoolingDown(knife.getItem())
                || SparkTraitsCompat.isMeleeActionBlocked(attacker, knife)
                || !DroneWeaponRules.withinReach(DroneHitGeometry.squaredDistanceToBox(eye,
                DroneHitGeometry.targetBox(drone)), reach)
                || !DroneHitGeometry.hasLineOfSight(attacker.getWorld(), eye, drone.getBoundingBox(), attacker)) {
            return;
        }
        DroneCombatService.destroy(drone, attacker);
        attacker.swingHand(hand, true);
        applyKnifeStabCooldown(attacker, knife);
    }

    /**
     * Mirrors NoellesRoles' ShadowJesterKnifeMixin (a higher-priority HEAD handler our drone stab hook runs before):
     * a Shadow Jester without the real knife may only stab its duel partner, which a drone never is, and a betrayal
     * trophy holder's stabs never work. Re-check against NoellesRoles when it changes that mixin.
     * 镜像 NoellesRoles 的 ShadowJesterKnifeMixin（优先级更高的 HEAD 处理，我们的无人机刀刺钩子先于它执行）：没有真刀的影子小丑只能刺其
     * 决斗搭档（无人机永远不是），背叛战利品持有者的刺击永远无效。NoellesRoles 修改该 mixin 时需同步复核。
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
     * Wathe revolver/derringer receiver at its {@code recordItemUse} anchor (after Wathe's spectator, gun-tag, cooldown
     * and spent-derringer checks). A drone id never resolves to Wathe's player target, so Wathe then finishes the shot
     * as a miss: sounds, derringer spent, cooldown, no innocent penalty. The SparkWitch shotgun shares the gun tag but
     * never sends a drone id, so only the revolver and derringer count.
     * Wathe 左轮/德林加接收器的 recordItemUse 锚点（位于 Wathe 的旁观、枪械标签、冷却与德林加已用检查之后）。无人机 id 永远不会被
     * Wathe 解析为玩家目标，因此 Wathe 随后按未命中完成这一枪：音效、德林加用尽、冷却，无误伤惩罚。
     * SparkWitch 猎枪同属枪械标签但不会发送无人机 id，因此只认左轮与德林加。
     */
    public static void onGunShot(ServerPlayerEntity shooter, @Nullable Entity target) {
        if (shooter == null || !(target instanceof DroneEntity drone)) {
            return;
        }
        ItemStack gun = shooter.getMainHandStack();
        if (gun.isOf(WatheItems.REVOLVER) || gun.isOf(WatheItems.DERRINGER)) {
            breakTargeted(shooter, drone);
        }
    }

    /**
     * NoellesRoles Demon Hunter pistol receiver at its target lookup (after its held-pistol, cooldown and bullet
     * checks). True = broke; the caller then resolves the target to null so NoellesRoles runs its miss path.
     * NoellesRoles 猎魔枪接收器的目标查找处（位于其手持、冷却与子弹检查之后）。返回 true 表示已击毁，调用方随后把目标解析为 null，
     * 让 NoellesRoles 走未命中流程。
     */
    public static boolean onDemonHunterShot(ServerPlayerEntity shooter, @Nullable Entity target) {
        return shooter != null && target instanceof DroneEntity drone && breakTargeted(shooter, drone);
    }

    private static boolean breakTargeted(ServerPlayerEntity shooter, DroneEntity drone) {
        if (!DroneCombatService.usable(drone) || drone.getWorld() != shooter.getWorld() || !mayBreak(shooter)
                || shooter.distanceTo(drone) >= DroneWeaponRules.GUN_MAX_DISTANCE
                || !DroneHitGeometry.gunAimedAndVisible(shooter.getWorld(), shooter.getEyePos(),
                shooter.getRotationVec(1.0F), DroneWeaponRules.GUN_MAX_DISTANCE, drone, shooter)) {
            return false;
        }
        DroneCombatService.destroy(drone, shooter);
        return true;
    }

    // ---- Use item: SparkWitch kunai and ceremonial sword dash (id only) / 使用物品：SparkWitch 苦无与仪礼剑冲刺（仅按 id） ----

    /**
     * Server only. Kunai: its own use is a server raycast that only sees players, so a drone strictly nearer than the
     * player it would hit (or any drone in range) breaks here and the kunai's use never runs (no one behind is killed;
     * the kunai's kill cooldown applies). Ceremonial sword: the dash is server-moved, so watch the dasher for a few
     * ticks and break drones its path sweeps through; the dash itself is untouched (PASS).
     * 仅服务端。苦无：其使用是只看玩家的服务端射线，因此严格比其将命中玩家更近（或射程内任意）的无人机在此被击毁，苦无本身不再执行
     * （身后的人不会被杀，并计入苦无击杀冷却）。仪礼剑：冲刺由服务端移动，因此短时监视冲刺者，击毁其路径扫过的无人机；冲刺本身不受影响（PASS）。
     */
    static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient() || !(player instanceof ServerPlayerEntity user) || stack.isEmpty()) {
            return TypedActionResult.pass(stack);
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (DroneWeaponRules.NINJA_KNIFE_ID.equals(id)) {
            return onKunai(user, hand, stack) ? TypedActionResult.consume(stack) : TypedActionResult.pass(stack);
        }
        if (DroneWeaponRules.CEREMONIAL_SWORD_ID.equals(id) && mayBreak(user)
                && !user.getItemCooldownManager().isCoolingDown(stack.getItem())
                && !SparkTraitsCompat.isMeleeActionBlocked(user, stack)) {
            DASH_WATCHES.put(user.getUuid(), new DashWatch(user.getPos(), DroneWeaponRules.SWORD_DASH_WATCH_TICKS));
        }
        return TypedActionResult.pass(stack);
    }

    private static boolean onKunai(ServerPlayerEntity user, Hand hand, ItemStack kunai) {
        if (!mayBreak(user) || user.getItemCooldownManager().isCoolingDown(kunai.getItem())
                || SparkTraitsCompat.isMeleeActionBlocked(user, kunai)) {
            return false;
        }
        double range = DroneWeaponRules.NINJA_KNIFE_RANGE;
        // The kunai's own pick (players only), to know whom the drone would shield. / 苦无自身的玩家选择。
        HitResult aimed = ProjectileUtil.getCollision(user, entity -> entity instanceof ServerPlayerEntity victim
                && victim != user && GameFunctions.isPlayerAliveAndSurvival(victim), range);
        Vec3d start = user.getEyePos();
        Vec3d end = DroneHitGeometry.clipToBlocks(user.getWorld(), start,
                start.add(user.getRotationVec(1.0F).multiply(range)), user);
        double beat = aimed instanceof EntityHitResult entityHit
                ? DroneHitGeometry.targetDistanceSquared(start, end, entityHit.getEntity().getBoundingBox())
                : Double.POSITIVE_INFINITY;
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(user.getWorld(), start, end, beat,
                DroneCombatService::usable);
        if (hit == null) {
            return false;
        }
        DroneCombatService.destroy(hit.drone(), user);
        user.swingHand(hand, true);
        user.getItemCooldownManager().set(kunai.getItem(), DroneWeaponRules.NINJA_KNIFE_COOLDOWN_TICKS);
        return true;
    }

    private static void tickDashWatches(MinecraftServer server) {
        if (DASH_WATCHES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, DashWatch>> iterator = DASH_WATCHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DashWatch> entry = iterator.next();
            DashWatch watch = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.isSpectator() || --watch.ticksLeft < 0) {
                iterator.remove();
                continue;
            }
            Vec3d now = player.getPos();
            Vec3d step = now.subtract(watch.lastPos);
            watch.lastPos = now;
            // Only dash-speed movement counts, so walking into a drone with the sword never breaks it.
            // 只有冲刺速度的移动才算，持剑走进无人机不会将其击毁。
            if (step.lengthSquared() < DroneWeaponRules.SWORD_DASH_MIN_STEP * DroneWeaponRules.SWORD_DASH_MIN_STEP) {
                continue;
            }
            Box swept = player.getBoundingBox().offset(step.negate()).stretch(step)
                    .expand(DroneWeaponRules.SWORD_DASH_PADDING);
            for (DroneEntity drone : player.getServerWorld().getEntitiesByClass(DroneEntity.class, swept,
                    DroneCombatService::usable)) {
                DroneCombatService.destroy(drone, player);
            }
        }
    }

    // ---- Projectiles / 投射物 ----

    /**
     * Which projectiles may collide with a drone at all. Damaging ones (arrows, tridents, fireballs) break it through
     * damage(); tagged thrown weapons break it on contact; a Wathe grenade detonates there. Every other throwable
     * (potions, snowballs, capsules, aroma orbs, cooling oil...) passes straight through.
     * 哪些投射物会与无人机碰撞。有伤害的（箭、三叉戟、火球）经 damage() 击毁；带标签的投掷武器接触即击毁；Wathe 手雷在该处爆炸。
     * 其余投掷物（药水、雪球、胶囊、香氛球、风油精……）直接穿过。
     */
    public static boolean projectileMayHitDrone(ProjectileEntity projectile) {
        return projectile instanceof PersistentProjectileEntity
                || projectile instanceof ExplosiveProjectileEntity
                || projectile instanceof GrenadeEntity
                || projectile.getType().isIn(DRONE_BREAKING_PROJECTILES);
    }

    /** Tagged projectile met a drone (server): break it and stop the projectile. True = handled. / 带标签投射物命中无人机。 */
    public static boolean onTaggedProjectileHit(ProjectileEntity projectile, DroneEntity drone) {
        if (projectile.getWorld().isClient() || !projectile.getType().isIn(DRONE_BREAKING_PROJECTILES)
                || !DroneCombatService.usable(drone)) {
            return false;
        }
        DroneCombatService.destroy(drone, projectile.getOwner() instanceof ServerPlayerEntity owner ? owner : null);
        projectile.discard();
        return true;
    }

    /**
     * NoellesRoles throwing axe segment for this tick (it pierces players, so they never shield a drone): breaks the
     * nearest drone before the first block and returns it, or null.
     * NoellesRoles 飞斧本刻路径（飞斧贯穿玩家，玩家不会替无人机挡下）：击毁第一个方块前最近的无人机并返回，否则为 null。
     */
    @Nullable
    public static DroneHitGeometry.DroneHit onThrowingAxeSweep(Entity axe, @Nullable Entity thrower, Vec3d from,
                                                              Vec3d to) {
        if (axe == null || from == null || to == null || axe.getWorld().isClient() || noDroneNear(axe.getWorld(), from, to)) {
            return null;
        }
        Vec3d end = DroneHitGeometry.clipToBlocks(axe.getWorld(), from, to, axe);
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(axe.getWorld(), from, end,
                Double.POSITIVE_INFINITY, DroneCombatService::usable);
        if (hit != null) {
            DroneCombatService.destroy(hit.drone(), thrower instanceof ServerPlayerEntity player ? player : null);
        }
        return hit;
    }

    // ---- SparkWitch server hitscans (via its SeekerDeviceHits seams) / SparkWitch 服务端即时射线 ----

    /**
     * Shotgun, feather blade, taser, shriek gun: nearest-wins along the shooter's aim; a drone strictly nearer than the
     * target (any drone in range on a miss) breaks and the caller hits nobody. True = absorbed.
     * 猎枪、羽刃、电击枪、啸音铳：沿射手瞄准的最近者命中；严格比目标更近的无人机（未命中时射程内任意无人机）被击毁，调用方不再命中任何人。
     */
    public static boolean onSparkWitchRay(@Nullable PlayerEntity shooter, @Nullable PlayerEntity target, double range) {
        if (!(shooter instanceof ServerPlayerEntity user) || !(range > 0.0) || !Double.isFinite(range)) {
            return false;
        }
        Vec3d start = user.getEyePos();
        Vec3d end = DroneHitGeometry.clipToBlocks(user.getWorld(), start,
                start.add(user.getRotationVec(1.0F).multiply(range)), user);
        double beat = target == null ? Double.POSITIVE_INFINITY
                : DroneHitGeometry.targetDistanceSquared(start, end, target.getBoundingBox());
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(user.getWorld(), start, end, beat,
                DroneCombatService::usable);
        if (hit == null) {
            return false;
        }
        DroneCombatService.destroy(hit.drone(), user);
        return true;
    }

    /**
     * Murderous Witch death ray (piercing): cut it at the nearest drone, which breaks; players before it are still hit.
     * 杀意魔女死光（穿透）：在最近的无人机处截断并将其击毁；无人机之前的玩家照常命中。
     */
    public static double onSparkWitchDeathRay(@Nullable ServerPlayerEntity user, @Nullable Vec3d start,
                                              @Nullable Vec3d direction, double visibleDistance) {
        if (user == null || start == null || direction == null || !(visibleDistance > 0.0)
                || !Double.isFinite(visibleDistance) || direction.lengthSquared() == 0.0) {
            return visibleDistance;
        }
        Vec3d end = start.add(direction.normalize().multiply(visibleDistance));
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(user.getWorld(), start, end,
                Double.POSITIVE_INFINITY, DroneCombatService::usable);
        if (hit == null) {
            return visibleDistance;
        }
        DroneCombatService.destroy(hit.drone(), user);
        return Math.min(visibleDistance, Math.sqrt(hit.distanceSquared()));
    }

    /**
     * Potion Gunner shell in flight: a drone on this tick's segment (before any nearer living player) breaks; returns
     * its entry point so the shell bursts there, or null.
     * 飞行中的药炮手炮弹：本刻路径上（且早于更近的存活玩家）的无人机被击毁；返回其入射点让炮弹在此爆炸，否则为 null。
     */
    @Nullable
    public static Vec3d onSparkWitchShellSweep(@Nullable Entity shell, @Nullable Entity thrower, @Nullable Vec3d from,
                                               @Nullable Vec3d to) {
        if (shell == null || from == null || to == null || shell.getWorld().isClient()
                || noDroneNear(shell.getWorld(), from, to)) {
            return null;
        }
        Vec3d end = DroneHitGeometry.clipToBlocks(shell.getWorld(), from, to, shell);
        double beat = Double.POSITIVE_INFINITY;
        for (PlayerEntity player : shell.getWorld().getEntitiesByClass(PlayerEntity.class,
                new Box(from, end).expand(1.0), candidate -> candidate != thrower
                        && GameFunctions.isPlayerAliveAndSurvival(candidate) && candidate.canBeHitByProjectile())) {
            double distance = DroneHitGeometry.entryDistanceSquared(from, end,
                    player.getBoundingBox().expand(player.getTargetingMargin()));
            if (distance >= 0.0 && distance < beat) {
                beat = distance;
            }
        }
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(shell.getWorld(), from, end, beat,
                DroneCombatService::usable);
        if (hit == null) {
            return null;
        }
        DroneCombatService.destroy(hit.drone(), thrower instanceof ServerPlayerEntity player ? player : null);
        return hit.point();
    }

    /** Potion launcher backblast lane: a drone strictly nearer than {@code reach} absorbs it. / 炮筒尾焰通道。 */
    public static boolean onSparkWitchBackblast(@Nullable ServerPlayerEntity gunner, @Nullable Vec3d start,
                                                @Nullable Vec3d direction, double reach) {
        if (gunner == null || start == null || direction == null || !(reach > 0.0) || !Double.isFinite(reach)
                || direction.lengthSquared() == 0.0) {
            return false;
        }
        Vec3d end = DroneHitGeometry.clipToBlocks(gunner.getWorld(), start,
                start.add(direction.normalize().multiply(reach)), gunner);
        DroneHitGeometry.DroneHit hit = DroneHitGeometry.nearestDrone(gunner.getWorld(), start, end, reach * reach,
                DroneCombatService::usable);
        if (hit == null) {
            return false;
        }
        DroneCombatService.destroy(hit.drone(), gunner);
        return true;
    }

    /**
     * SparkWitch area blasts. Only its own (potion shell) blasts break drones here: Wathe grenades and M67s already break
     * drones through their own hooks, after their kills, so their SparkWitch reports are ignored.
     * SparkWitch 范围爆炸。此处只处理其自有（药炮手炮弹）爆炸：Wathe 手雷与 M67 已通过自身钩子在击杀之后击毁无人机，因此忽略它们的
     * SparkWitch 上报。
     */
    public static void onSparkWitchBlast(@Nullable ServerWorld world, @Nullable Vec3d center, double radius,
                                         @Nullable ServerPlayerEntity owner) {
        // Inside a Wathe grenade / M67 detonation our own hooks break drones after its kills; SparkWitch reports the
        // grenade at its HEAD (before them), so skip here. / Wathe 手雷 / M67 引爆期间由自身钩子在击杀后处理；SparkWitch 在 HEAD
        // （击杀之前）上报手雷，因此此处跳过。
        if (DroneCombatService.inOwnBlast()) {
            return;
        }
        if (world != null && center != null) {
            DroneCombatService.breakDronesInBlast(world, center, radius, null, owner);
        }
    }

    // ---- Shared / 公共 ----

    /**
     * Melee and client-picked breakers must be live round participants in survival, not Last-Escape blocked and not
     * inside a pending SparkTraits Last Stand (our callback phase runs before the default phase where SparkTraits
     * enforces that lock, so it is re-checked here).
     * 近战与客户端选目标的击毁者必须是存活、生存模式的对局参与者，未被最后逃脱阻止，且不处于 SparkTraits 背水一战待决中
     * （我们的回调阶段早于 SparkTraits 执行该锁的默认阶段，因此在此复查）。
     */
    static boolean mayBreak(@Nullable ServerPlayerEntity player) {
        return player != null && !player.isRemoved() && player.isAlive()
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !SparkTraitsCompat.isKillerInteractionBlocked(player)
                && !SparkTraitsDroneCompat.isLastStandPending(player);
    }

    /** Cheap per-tick pre-check for projectile sweeps. / 投射物逐刻扫掠的廉价预检查。 */
    private static boolean noDroneNear(World world, Vec3d from, Vec3d to) {
        List<DroneEntity> nearby = world.getEntitiesByClass(DroneEntity.class, new Box(from, to).expand(1.0),
                DroneCombatService::usable);
        return nearby.isEmpty();
    }

    private static final class DashWatch {
        private Vec3d lastPos;
        private int ticksLeft;

        private DashWatch(Vec3d lastPos, int ticksLeft) {
            this.lastPos = lastPos;
            this.ticksLeft = ticksLeft;
        }
    }
}
