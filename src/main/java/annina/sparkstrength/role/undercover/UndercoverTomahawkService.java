package annina.sparkstrength.role.undercover;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.undercover.UndercoverPlayerComponent;
import annina.sparkstrength.role.veteran.InnocentKnifeKillRules;
import annina.sparkstrength.sabotage.SabotageActionLocks;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The tactical tomahawk's left-click kill (owner 2026-10-19): attack speed 1, a fully charged swing at a living player
 * kills them with {@code sparkstrength:tomahawk}, then the tomahawk cools down 20 s, or 3 s in a blackout (also when a
 * vest or shield stopped the kill). Killing an effective innocent costs the Undercover its life ("小脑",
 * {@code wathe:shot_innocent}), with the Veteran knife's rule. Any other tomahawk swing does nothing at all, so it never
 * falls back to a vanilla punch.
 * 战术斧的左键击杀（负责人 2026-10-19）：攻速 1，蓄满后挥向存活玩家即以 sparkstrength:tomahawk 击杀，随后冷却 20 秒，
 * 熄灯时 3 秒（被背心或盾挡下也一样）。杀死有效好人时卧底自己「小脑」（wathe:shot_innocent），规则同老兵匕首。
 * 其余任何挥砍都不会生效，绝不会退回到原版拳击。
 *
 * <p>{@link AttackEntityCallback} runs at the head of the attack on both sides: the client returns SUCCESS to send the
 * attack packet, the server re-checks everything. Both reset the swing charge themselves, because a non-PASS result
 * skips vanilla's reset at the end of the attack.
 * AttackEntityCallback 在双端攻击开头执行：客户端返回 SUCCESS 以发送攻击包，服务端重新检查一切。
 * 双端都自行重置蓄力，因为非 PASS 结果会跳过原版在攻击末尾的重置。</p>
 */
public final class UndercoverTomahawkService {
    /** Pass this constant to killPlayer; other code compares death reasons by reference. / 传给 killPlayer 的常量；其他代码按引用比较死因。 */
    public static final Identifier DEATH_REASON = SparkStrength.id("tomahawk");
    private static final float FULL_CHARGE_BASE = 0.5F;
    /** Lag allowance on top of the 3-block reach. / 3 格射程之外的延迟容差。 */
    private static final double REACH_TOLERANCE = 0.5;
    private static boolean registered;

    private UndercoverTomahawkService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AttackEntityCallback.EVENT.register(UndercoverTomahawkService::onAttack);
    }

    private static ActionResult onAttack(PlayerEntity player, World world, Hand hand, Entity entity,
                                         @Nullable EntityHitResult hitResult) {
        Item tomahawk = SparkStrengthItems.tacticalTomahawk();
        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(tomahawk)) {
            return ActionResult.PASS;
        }
        // Read the charge first, then every swing spends it, hit or not (a click spam never keeps a charge).
        // 先读蓄力，然后每次挥砍都会消耗，无论是否命中（连点不会保留蓄力）。
        boolean charged = player.getAttackCooldownProgress(FULL_CHARGE_BASE) >= 1.0F;
        player.resetLastAttackedTicks();
        // A SparkWitch Raven Form crow is a target too (owner 2026-10-21): the client sends the swing, the server
        // resolves it to the Black Raven. / SparkWitch 化鸦乌鸦同样是目标（所有者 2026-10-21）：客户端发送挥砍，服务端将其
        // 解析为黑羽鸦。
        boolean crow = !(entity instanceof PlayerEntity) && SparkWitchCompat.isBlackRavenCrow(entity);
        if (!(entity instanceof PlayerEntity) && !crow || !UndercoverRoles.isLivingStage2(player) || !charged
                || player.getItemCooldownManager().isCoolingDown(tomahawk)) {
            return ActionResult.FAIL;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity attacker) {
            if (entity instanceof ServerPlayerEntity victim) {
                strike(attacker, victim, held, tomahawk, null);
            } else if (crow) {
                // SparkWitch measured the reach and line on the crow and armed its owner substitution, so the strike
                // below (gates, kill, cooldown, 小脑) runs on the Black Raven exactly as on a direct hit.
                // SparkWitch 已在乌鸦上量取距离与视线并装好主人替身，因此下方的劈砍（门槛、击杀、冷却、小脑）与直接命中黑羽鸦完全一致。
                ServerPlayerEntity owner = SparkWitchCompat.crowMeleeStandIn(attacker, entity,
                        UndercoverRules.TOMAHAWK_RANGE);
                if (owner != null) {
                    strike(attacker, owner, held, tomahawk, entity.getPos());
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * @param crowPos where the Raven Form crow standing in for {@code victim} was hit (its reach was measured there and
     *                the hit sound plays there), or null for a direct hit / 代替 {@code victim} 被命中的化鸦乌鸦位置（距离已在该处
     *                量取，命中声也在该处播放）；直接命中时为 null
     */
    private static void strike(ServerPlayerEntity attacker, ServerPlayerEntity victim, ItemStack held, Item tomahawk,
                               @Nullable Vec3d crowPos) {
        if (!GameFunctions.isPlayerPlayingAndAlive(victim) || crowPos == null && !inReach(attacker, victim)
                || SabotageActionLocks.isLocked(attacker)
                || SparkTraitsCompat.isMeleeActionBlocked(attacker, held)
                || !SparkFactionCompat.canAffectPlayer(attacker, victim, DEATH_REASON)
                || SparkTraitsCompat.shouldCancelMeleeAttack(attacker, victim, held)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getServerWorld());
        // Read before killPlayer: SparkTraits clears the victim's traits in its KillPlayer.AFTER listener.
        // 必须在 killPlayer 之前读取：SparkTraits 会在 KillPlayer.AFTER 中清空死者天赋。
        InnocentKnifeKillRules.PreKill preKill = InnocentKnifeKillRules.beforeKill(
                game.isInnocent(victim),
                SparkTraitsCompat.hasImpostor(victim),
                SparkTraitsCompat.hasConscience(victim),
                SparkTraitsCompat.hasImpostor(attacker));
        GameRecordManager.recordItemUse(attacker, SparkStrengthItems.TACTICAL_TOMAHAWK_ID, victim, null);
        Vec3d hitAt = crowPos != null ? crowPos : victim.getPos();
        attacker.getWorld().playSound(null, hitAt.x, hitAt.y, hitAt.z,
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 0.8F);
        GameFunctions.killPlayer(victim, true, attacker, DEATH_REASON);
        ServerWorld world = attacker.getServerWorld();
        int cooldown = UndercoverRules.killCooldownTicks(WorldBlackoutComponent.KEY.get(world).isBlackoutActive());
        attacker.getItemCooldownManager().set(tomahawk, cooldown);
        // Recorded so a blackout can cut it later; UndercoverService owns it from here.
        // 记录下来以便之后熄灯时缩短；之后由 UndercoverService 管理。
        UndercoverPlayerComponent.KEY.get(attacker).setKillCooldownEndTick(world.getTime() + cooldown);
        // 小脑 only after a death that really happened, and never twice. / 只有确实死亡才小脑，且不会重复处死。
        if (InnocentKnifeKillRules.shouldPunish(preKill, game.isPlayerDead(victim.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(attacker))) {
            GameFunctions.killPlayer(attacker, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    /**
     * Server truth for a forged or laggy attack packet: the victim's box is within the knife's 3 blocks of the eyes and
     * nothing solid stands between (eyes to the victim's eyes or body centre).
     * 对伪造或延迟的攻击包以服务端为准：目标碰撞箱距眼睛不超过刀的 3 格，且之间没有实心方块（眼睛到目标眼睛或身体中心）。
     */
    private static boolean inReach(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        Vec3d eye = attacker.getEyePos();
        Box box = victim.getBoundingBox();
        Vec3d closest = new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX),
                MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        double reach = UndercoverRules.TOMAHAWK_RANGE + REACH_TOLERANCE;
        if (eye.squaredDistanceTo(closest) > reach * reach) {
            return false;
        }
        return clearLine(attacker, eye, victim.getEyePos()) || clearLine(attacker, eye, box.getCenter());
    }

    private static boolean clearLine(ServerPlayerEntity attacker, Vec3d from, Vec3d to) {
        return attacker.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, attacker)).getType() == HitResult.Type.MISS;
    }
}
