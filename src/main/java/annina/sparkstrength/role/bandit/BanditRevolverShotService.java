package annina.sparkstrength.role.bandit;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.item.BanditRevolverItem;
import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import annina.sparkstrength.role.serialkiller.SerialPistolShotService;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.Scheduler;
import dev.doctor4t.wathe.util.ShootMuzzleS2CPayload;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority for {@code sparkstrength:bandit_revolver_shoot}. Same flow as the Serial Killer pistols
 * ({@link SerialPistolShotService}, itself a mirror of Wathe's {@code GunShootPayload.Receiver}): click, record,
 * ShouldPunishGunShooter custom punishment, killPlayer with DeathReasons.GUN, shot sound, muzzle flash, cooldown. On
 * top of it: the shooter must be a living Bandit holding the revolver in the main hand with a chambered round, the
 * reach and cooldown follow the fitted parts, the shot spends the first chambered round, and the long barrel and a
 * PS12B round each add one more kill attempt on a target the previous attempt left alive.
 * 强盗改装左轮开火包的服务端权威处理。流程与连环杀手手枪相同（其本身复刻 Wathe 左轮接收器）：咔哒声、回放记录、
 * ShouldPunishGunShooter 自定义惩罚、以 GUN 死因击杀、枪声、枪口火光、冷却。在此之上：射手须为主手持改装左轮且已有上膛子弹的
 * 存活强盗；射程与冷却取决于所装配件；每枪消耗第一发上膛子弹；长枪管与 PS12B 各自在上一次判定未击杀目标时追加一次击杀判定。
 *
 * <p>A shot refused by a shooter gate, SparkTraits Last Escape or SparkFactionAPI leaves no trace and keeps its round;
 * a target that fails the liveness or sight check turns the shot into a recorded miss that still spends the round.</p>
 * <p>被射手门槛、SparkTraits 脱险或 SparkFactionAPI 拒绝的射击不留痕迹，也不消耗子弹；目标未通过存活或视线检查时按空枪
 * 记录，子弹照常消耗。</p>
 */
public final class BanditRevolverShotService {
    private BanditRevolverShotService() {
    }

    public static void handle(ServerPlayerEntity shooter, int targetId) {
        MinecraftServer server = shooter.getServer();
        if (server == null) {
            return;
        }
        if (!server.isOnThread()) {
            server.execute(() -> handle(shooter, targetId));
            return;
        }
        if (SparkTraitsCompat.isKillerInteractionBlocked(shooter)) {
            return;
        }
        ItemStack gun = authorizedRevolver(shooter);
        if (gun == null) {
            return;
        }
        BanditRevolverState state = BanditRevolverItem.state(gun);
        BanditAmmo ammo = state.nextRound();
        if (state.rounds().isEmpty()) {
            return;
        }
        double range = BanditRevolverRules.rangeBlocks(state.barrelPart())
                + SerialPistolShotService.SERVER_RANGE_SLACK_BLOCKS;
        ServerPlayerEntity candidate = claimedTarget(shooter, targetId, range);
        // SparkFactionAPI drops a revolver shot at a protected player outright: no sound, record, cooldown or round.
        // SparkFactionAPI 对受保护玩家的左轮射击整包丢弃：无声音、无记录、无冷却，也不消耗子弹。
        if (candidate != null && !SparkFactionCompat.canAffectPlayer(shooter, candidate, GameConstants.DeathReasons.GUN)) {
            return;
        }
        ServerWorld world = shooter.getServerWorld();
        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), WatheSounds.ITEM_REVOLVER_CLICK,
                SoundCategory.PLAYERS, 0.5F, 1.0F + shooter.getRandom().nextFloat() * 0.1F - 0.05F);
        BanditRevolverItem.write(gun, state.withRoundFired());

        if (candidate == null) {
            // Non-player ids: SparkWitch's Magician puppet / Seeker device, then a Partygoer; the shot stays a miss.
            // 非玩家 id：先交给 SparkWitch 的魔术师皮套/搜寻者设备，再看派对客；这一枪仍按未命中记录。
            Entity entity = targetId < 0 ? null : world.getEntityById(targetId);
            if (entity != null && !(entity instanceof PlayerEntity)) {
                SparkWitchCompat.hitGunWorldTarget(shooter, entity, gun, range);
            }
            PartygoerWeaponHits.onSerialPistolShot(shooter, entity, range);
        }

        ServerPlayerEntity target = candidate != null && isHittable(shooter, candidate) ? candidate : null;
        int hits = BanditRevolverRules.hits(state.barrelPart(), ammo);
        NbtCompound extra = new NbtCompound();
        extra.putString("ammo", ammo == null ? "" : ammo.key());
        extra.putInt("hits", hits);
        GameRecordManager.recordItemUse(shooter, BanditRevolverService.itemId(), target, extra);
        if (target != null) {
            ShouldPunishGunShooter.PunishResult punishResult =
                    ShouldPunishGunShooter.EVENT.invoker().shouldPunish(shooter, target);
            if (punishResult != null && punishResult.hasCustomPunishment()) {
                Scheduler.schedule(punishResult::executeCustomPunishment,
                        SerialPistolShotService.CUSTOM_PUNISHMENT_DELAY_TICKS);
            }
            for (int hit = 0; hit < hits; hit++) {
                // An extra attack only lands on a target the previous one left alive (a spent vest, shield block or
                // psycho armour); a dead target is never killed twice.
                // 追加的攻击只打在上一次判定后仍存活的目标上（消耗掉的防弹衣、盾牌格挡或疯魔护甲）；已死亡的目标不会被击杀两次。
                if (hit > 0 && !(GameFunctions.isPlayerPlayingAndAlive(target)
                        && GameFunctions.isPlayerAliveAndSurvival(target))) {
                    break;
                }
                GameFunctions.killPlayer(target, true, shooter, GameConstants.DeathReasons.GUN);
            }
        }

        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), WatheSounds.ITEM_REVOLVER_SHOOT,
                SoundCategory.PLAYERS, 5.0F, 1.0F + shooter.getRandom().nextFloat() * 0.1F - 0.05F);
        for (ServerPlayerEntity tracking : PlayerLookup.tracking(shooter)) {
            ServerPlayNetworking.send(tracking, new ShootMuzzleS2CPayload(shooter.getUuidAsString()));
        }
        ServerPlayNetworking.send(shooter, new ShootMuzzleS2CPayload(shooter.getUuidAsString()));
        if (!shooter.isCreative()) {
            shooter.getItemCooldownManager().set(gun.getItem(),
                    BanditRevolverRules.cooldownTicks(state.barrelPart(), state.gripPart()));
        }
    }

    /** The main-hand revolver when the shooter may fire it now, else null. / 射手此刻可开火时返回主手的改装左轮，否则 null。 */
    private static @Nullable ItemStack authorizedRevolver(ServerPlayerEntity shooter) {
        if (shooter.isSpectator()) {
            return null;
        }
        if (!shooter.isCreative() && !(GameFunctions.isPlayerPlayingAndAlive(shooter)
                && BanditRevolverService.isBandit(shooter))) {
            return null;
        }
        ItemStack stack = shooter.getMainHandStack();
        if (!BanditRevolverItem.isRevolver(stack)
                || shooter.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            return null;
        }
        return stack;
    }

    /** Same gates as the Serial Killer pistols, with this gun's reach. / 与连环杀手手枪相同的门槛，射程取本枪的射程。 */
    private static @Nullable ServerPlayerEntity claimedTarget(ServerPlayerEntity shooter, int targetId, double range) {
        if (targetId < 0) {
            return null;
        }
        Entity entity = shooter.getServerWorld().getEntityById(targetId);
        if (!(entity instanceof ServerPlayerEntity target)
                || target instanceof FakePlayer
                || target == shooter
                || target.isSpectator()
                || target.distanceTo(shooter) > range) {
            return null;
        }
        return target;
    }

    private static boolean isHittable(ServerPlayerEntity shooter, ServerPlayerEntity target) {
        return GameFunctions.isPlayerAliveAndSurvival(target)
                && (GameFunctions.isPlayerPlayingAndAlive(target)
                || SparkWitchCompat.isBoundKillerTargetingVendetta(shooter, target))
                && SerialPistolShotService.hasLineOfSight(shooter, target);
    }
}
