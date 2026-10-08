package annina.sparkstrength.role.serialkiller;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.item.SerialPistolItem;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
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
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server authority for {@code sparkstrength:serial_pistol_shoot}. Mirrors Wathe's {@code GunShootPayload.Receiver}
 * (click, record, ShouldPunishGunShooter custom punishment, killPlayer with DeathReasons.GUN, shoot sound, muzzle,
 * cooldown) and adds what that receiver trusts the client for: the shooter must be a living Serial Killer in psycho
 * mode holding the matching pistol in the claimed hand, and the target must be alive, in survival, in range, in clear
 * line of sight and affectable under SparkFactionAPI. A shot refused by a shooter gate, SparkTraits Last Escape or
 * SparkFactionAPI leaves no trace (no sound, record or cooldown), like the revolver; a target that fails the liveness or
 * sight check turns the shot into a recorded miss, as Wathe does for a missing target.
 * 连环手枪开火包的服务端权威处理。复刻 Wathe 左轮接收器（咔哒声、回放记录、ShouldPunishGunShooter 自定义惩罚、
 * 以 GUN 死因击杀、枪声、枪口火光、冷却），并补上原接收器信任客户端的部分：射手须为疯魔中的存活连环杀手且所声明的手
 * 持有对应手枪；目标须存活、生存模式、在射程内、视线无遮挡且 SparkFactionAPI 允许影响。被射手门槛、SparkTraits 脱险或
 * SparkFactionAPI 拒绝的射击与左轮一样不留痕迹（无声音、记录、冷却）；目标未通过存活或视线检查时按空枪记录，与 Wathe 对无目标的处理一致。
 */
public final class SerialPistolShotService {
    /**
     * Server-side slack over the 30-block client ray: positions here are feet-to-feet and one tick behind.
     * 服务端射程余量：此处按实体坐标测距且比客户端晚一刻。
     */
    public static final double SERVER_RANGE_SLACK_BLOCKS = 2.0D;
    /** Delay Wathe uses before running a ShouldPunishGunShooter custom punishment. / Wathe 执行自定义惩罚前的延迟。 */
    public static final int CUSTOM_PUNISHMENT_DELAY_TICKS = 4;
    private static final double LINE_OF_SIGHT_INSET = 0.05D;

    private SerialPistolShotService() {
    }

    public static void handle(ServerPlayerEntity shooter, int targetId, Hand hand) {
        MinecraftServer server = shooter.getServer();
        if (server == null) {
            return;
        }
        // Fabric already runs play payload handlers on the server thread; re-queue defensively if that ever changes.
        // Fabric 已在服务端主线程执行该处理器；若将来改变，这里仍会转回主线程，绝不在网络线程改动状态。
        if (!server.isOnThread()) {
            server.execute(() -> handle(shooter, targetId, hand));
            return;
        }
        // SparkTraits Last Escape: the revolver's receiver is cancelled at HEAD, before any side effect.
        // SparkTraits 脱险：左轮接收器在 HEAD 即被取消，先于任何副作用。
        if (SparkTraitsCompat.isKillerInteractionBlocked(shooter)) {
            return;
        }
        ItemStack stack = authorizedPistol(shooter, hand);
        if (stack == null) {
            return;
        }
        ServerPlayerEntity candidate = claimedTarget(shooter, targetId);
        // SparkFactionAPI drops a revolver shot at a protected player outright: no sound, record or cooldown.
        // SparkFactionAPI 对受保护玩家的左轮射击整包丢弃：无声音、无记录、无冷却。
        if (candidate != null && !SparkFactionCompat.canAffectPlayer(shooter, candidate, GameConstants.DeathReasons.GUN)) {
            return;
        }
        ServerWorld world = shooter.getServerWorld();
        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), WatheSounds.ITEM_REVOLVER_CLICK,
                SoundCategory.PLAYERS, 0.5F, 1.0F + shooter.getRandom().nextFloat() * 0.1F - 0.05F);

        if (candidate == null) {
            // SparkWitch hooks Wathe's revolver at this point (before the record): a non-player id naming a Magician
            // puppet or Seeker device ends or breaks it, and the shot below stays a recorded miss (D3: sound, muzzle,
            // cooldown; no punishment, no mood loss), exactly as for the revolver.
            // SparkWitch 在此处（记录之前）挂接 Wathe 左轮：非玩家 id 指向魔术师皮套或搜寻者设备时将其结束或打坏，下方这一枪
            // 仍按未命中记录（D3：声音、枪口火光、冷却；无惩罚、不扣理智），与左轮完全一致。
            hitSparkWitchWorldTarget(shooter, targetId, stack);
        }

        ServerPlayerEntity target = candidate != null && isHittable(shooter, candidate) ? candidate : null;
        GameRecordManager.recordItemUse(shooter, Registries.ITEM.getId(stack.getItem()), target, null);
        if (target != null) {
            // Same hook Wathe's revolver fires, so other mods' custom gun punishments still apply. The revolver-only
            // innocent punishment (gun drop / backfire) never applies here, exactly as for any non-revolver gun.
            // 与 Wathe 左轮相同的钩子，其他模组的自定义惩罚照常生效；仅限左轮的误杀惩罚（掉枪/走火）与其他非左轮枪一样不适用。
            ShouldPunishGunShooter.PunishResult punishResult =
                    ShouldPunishGunShooter.EVENT.invoker().shouldPunish(shooter, target);
            if (punishResult != null && punishResult.hasCustomPunishment()) {
                Scheduler.schedule(punishResult::executeCustomPunishment, CUSTOM_PUNISHMENT_DELAY_TICKS);
            }
            // killPlayer fires KillPlayer.BEFORE/AFTER, psycho armour and the attribution every gun kill gets.
            // killPlayer 会触发 KillPlayer.BEFORE/AFTER、疯魔护甲判定以及所有枪杀共用的击杀归属。
            GameFunctions.killPlayer(target, true, shooter, GameConstants.DeathReasons.GUN);
        }

        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), WatheSounds.ITEM_REVOLVER_SHOOT,
                SoundCategory.PLAYERS, 5.0F, 1.0F + shooter.getRandom().nextFloat() * 0.1F - 0.05F);
        for (ServerPlayerEntity tracking : PlayerLookup.tracking(shooter)) {
            ServerPlayNetworking.send(tracking, new ShootMuzzleS2CPayload(shooter.getUuidAsString()));
        }
        ServerPlayNetworking.send(shooter, new ShootMuzzleS2CPayload(shooter.getUuidAsString()));
        if (!shooter.isCreative()) {
            shooter.getItemCooldownManager().set(stack.getItem(), SerialKillerConstants.PISTOL_COOLDOWN_TICKS);
        }
    }

    /** The claimed hand's pistol when the shooter may fire it now, else null. / 射手此刻可开火时返回所声明手上的手枪，否则 null。 */
    private static @Nullable ItemStack authorizedPistol(ServerPlayerEntity shooter, Hand hand) {
        if (hand == null || shooter.isSpectator()
                || !GameFunctions.isPlayerPlayingAndAlive(shooter)
                || !GameFunctions.isPlayerAliveAndSurvival(shooter)) {
            return null;
        }
        if (!GameWorldComponent.KEY.get(shooter.getWorld()).isRole(shooter, Noellesroles.SERIAL_KILLER)
                || PlayerPsychoComponent.KEY.get(shooter).getPsychoTicks() <= 0) {
            return null;
        }
        ItemStack stack = shooter.getStackInHand(hand);
        if (!SerialPistolItem.isPistolForHand(stack, hand)
                || shooter.getItemCooldownManager().isCoolingDown(stack.getItem())) {
            return null;
        }
        return stack;
    }

    /**
     * The claimed entity when it is no player (a SparkWitch Magician puppet or Seeker device), handed to SparkWitch with
     * the pistol's server range; nothing happens without SparkWitch.
     * 声明的实体不是玩家时（SparkWitch 魔术师皮套或搜寻者设备）连同手枪的服务端射程交给 SparkWitch；未安装 SparkWitch 时无事发生。
     */
    private static void hitSparkWitchWorldTarget(ServerPlayerEntity shooter, int targetId, ItemStack stack) {
        Entity entity = targetId < 0 ? null : shooter.getServerWorld().getEntityById(targetId);
        if (entity != null && !(entity instanceof PlayerEntity)) {
            SparkWitchCompat.hitGunWorldTarget(shooter, entity, stack,
                    SerialKillerConstants.PISTOL_RANGE_BLOCKS + SERVER_RANGE_SLACK_BLOCKS);
        }
    }

    /**
     * The client-claimed player, with the same gates SparkFactionAPI's revolver guard applies before its faction check
     * (a non-spectator player in range); null means the shot is a miss. A Fabric fake player (such as SparkWitch's
     * server-only Magician replay proxy, which carries its owner's UUID) is never a target, so a pistol can never send
     * one through killPlayer.
     * 客户端声明的目标玩家，门槛与 SparkFactionAPI 左轮防护在阵营检查前所用的一致（非旁观且在射程内）；null 表示空枪。
     * Fabric 假玩家（例如 SparkWitch 仅存在于服务端、携带主人 UUID 的魔术师回放代理）永远不是目标，手枪绝不会让其进入 killPlayer。
     */
    private static @Nullable ServerPlayerEntity claimedTarget(ServerPlayerEntity shooter, int targetId) {
        if (targetId < 0) {
            return null;
        }
        Entity entity = shooter.getServerWorld().getEntityById(targetId);
        if (!(entity instanceof ServerPlayerEntity target)
                || target instanceof FakePlayer
                || target == shooter
                || target.isSpectator()
                || target.distanceTo(shooter) > SerialKillerConstants.PISTOL_RANGE_BLOCKS + SERVER_RANGE_SLACK_BLOCKS) {
            return null;
        }
        return target;
    }

    /**
     * What Wathe trusts the client for: a living, playing survival target in clear line of sight. The shooter's own
     * bound SparkWitch Vendetta counts as playing though Wathe counts it as dead, because Wathe's revolver hands it to
     * killPlayer, where SparkWitch resolves its terminal death (other Wraiths never reach here: SparkFactionAPI and
     * SparkWitch's Vendetta packet guard drop those shots first).
     * Wathe 信任客户端的部分：目标存活、在局、生存模式且视线无遮挡。射手自己绑定的 SparkWitch 仇杀客虽被 Wathe 视为已死亡，
     * 仍算在局，因为 Wathe 左轮会把它交给 killPlayer，由 SparkWitch 结算其终局死亡（其他冤魂到不了这里：SparkFactionAPI 与
     * SparkWitch 的仇杀客数据包防护会先丢弃这些射击）。
     */
    private static boolean isHittable(ServerPlayerEntity shooter, ServerPlayerEntity target) {
        return GameFunctions.isPlayerAliveAndSurvival(target)
                && (GameFunctions.isPlayerPlayingAndAlive(target)
                || SparkWitchCompat.isBoundKillerTargetingVendetta(shooter, target))
                && hasLineOfSight(shooter, target);
    }

    /**
     * Wathe re-raycasts nothing server-side; this re-checks that no collider block stands between the shooter's eye
     * and the target (eye, centre, upper and lower body), with the same COLLIDER/no-fluid rules as the client ray.
     * Wathe 服务端不重做射线；这里复查射手眼睛到目标（眼睛、中心、上身、下身）之间没有碰撞方块，规则与客户端射线一致。
     */
    public static boolean hasLineOfSight(ServerPlayerEntity shooter, ServerPlayerEntity target) {
        Vec3d eye = shooter.getEyePos();
        for (Vec3d point : sightPoints(target)) {
            HitResult hit = shooter.getWorld().raycast(new RaycastContext(eye, point,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private static List<Vec3d> sightPoints(ServerPlayerEntity target) {
        Box box = target.getBoundingBox();
        Vec3d centre = box.getCenter();
        double inset = Math.min(LINE_OF_SIGHT_INSET, box.getLengthY() / 4.0D);
        return List.of(
                target.getEyePos(),
                centre,
                new Vec3d(centre.x, box.maxY - inset, centre.z),
                new Vec3d(centre.x, box.minY + inset, centre.z));
    }
}
