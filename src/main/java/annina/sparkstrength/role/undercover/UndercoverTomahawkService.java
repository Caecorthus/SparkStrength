package annina.sparkstrength.role.undercover;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.role.veteran.InnocentKnifeKillRules;
import annina.sparkstrength.sabotage.SabotageActionLocks;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The tactical tomahawk's left-click kill (owner 2026-10-19): attack speed 1, a fully charged swing at a living player
 * kills them with {@code sparkstrength:tomahawk}, then the tomahawk cools down 20 s (also when a vest or shield stopped
 * the kill). Killing an effective innocent costs the Undercover its life ("小脑", {@code wathe:shot_innocent}), with
 * the Veteran knife's rule. Any other tomahawk swing does nothing at all, so it never falls back to a vanilla punch.
 * 战术斧的左键击杀（负责人 2026-10-19）：攻速 1，蓄满后挥向存活玩家即以 sparkstrength:tomahawk 击杀，随后冷却 20 秒
 * （被背心或盾挡下也一样）。杀死有效好人时卧底自己「小脑」（wathe:shot_innocent），规则同老兵匕首。
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
        if (!(entity instanceof PlayerEntity target) || !UndercoverRoles.isLivingStage2(player)
                || player.getAttackCooldownProgress(FULL_CHARGE_BASE) < 1.0F
                || player.getItemCooldownManager().isCoolingDown(tomahawk)) {
            return ActionResult.FAIL;
        }
        player.resetLastAttackedTicks();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity attacker && target instanceof ServerPlayerEntity victim) {
            strike(attacker, victim, held, tomahawk);
        }
        return ActionResult.SUCCESS;
    }

    private static void strike(ServerPlayerEntity attacker, ServerPlayerEntity victim, ItemStack held, Item tomahawk) {
        if (!GameFunctions.isPlayerPlayingAndAlive(victim)
                || attacker.squaredDistanceTo(victim) > square(UndercoverRules.TOMAHAWK_RANGE + 1.0)
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
        attacker.getWorld().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 0.8F);
        GameFunctions.killPlayer(victim, true, attacker, DEATH_REASON);
        attacker.getItemCooldownManager().set(tomahawk, UndercoverRules.TOMAHAWK_KILL_COOLDOWN_TICKS);
        // 小脑 only after a death that really happened, and never twice. / 只有确实死亡才小脑，且不会重复处死。
        if (InnocentKnifeKillRules.shouldPunish(preKill, game.isPlayerDead(victim.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(attacker))) {
            GameFunctions.killPlayer(attacker, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    private static double square(double value) {
        return value * value;
    }
}
