package annina.sparkstrength.role.bodyguard;

import annina.sparkstrength.component.bodyguard.BodyguardGearComponent;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.GameRecordTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether the Bodyguard's Democracy Shield or vest stops a kill. Runs at the head of GameFunctions.killPlayer,
 * so the shield and vest are spent before NoellesRoles' Iron Man / whiskey shield and Wathe's psycho armour
 * (owner order: shield, then vest, then everything else). Forced kills are never blocked or charged.
 * 判定保镖的民主盾牌或防弹衣能否挡下一次击杀。在 GameFunctions.killPlayer 开头执行，因此盾与防弹衣先于
 * NoellesRoles 的铁人药水/威士忌护盾与 Wathe 疯魔护甲结算（所有者顺序：盾 → 防弹衣 → 其他）。强制击杀从不阻挡、也不消耗。
 */
public final class BodyguardProtectionService {
    public static final String SHIELD_SOURCE = "sparkstrength:democracy_shield";
    public static final String VEST_SOURCE = "sparkstrength:bodyguard_vest";
    private static boolean registered;

    private BodyguardProtectionService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ShouldPunishGunShooter.EVENT.register(BodyguardProtectionService::shouldPunishGunShooter);
    }

    /**
     * @return true to cancel the kill / 返回 true 取消本次击杀
     */
    public static boolean tryBlockKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force
    ) {
        if (force || victim == null || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return false;
        }
        Vec3d blast = AttackOriginScope.currentBlast();
        BodyguardProtectionRules.Protection protection = BodyguardProtectionRules.forReason(deathReason, blast != null);
        if (!protection.shieldBlocks() && !protection.vest()) {
            return false;
        }
        BodyguardGearComponent gear = BodyguardGearComponent.KEY.get(victim);
        boolean shieldApplies = protection.shieldBlocks() && shieldFaces(victim, killer, protection.source(), blast);
        if (!shieldApplies && !(protection.vest() && gear.isVestWorn())) {
            return false;
        }

        // SparkTraits' Second Strike re-settles a shielded attack once, but its snapshot does not know this gear, so it
        // is mirrored here: two settlements, each stopped by the shield if still up, else by the vest, else the kill
        // goes on to the remaining protections.
        // SparkTraits 二次打击会让被护盾挡下的攻击再结算一次，但它的快照不认识这套装备，所以在这里模拟：共两次结算，
        // 每次先由仍举着的盾挡，否则由防弹衣挡，都挡不住时击杀继续交给其余保护。
        int settlements = killer != null && killer != victim
                && SparkTraitsCompat.hasTrait(killer, BodyguardRules.SECOND_STRIKE_TRAIT_ID) ? 2 : 1;
        for (; settlements > 0; settlements--) {
            if (shieldApplies && BodyguardShieldService.isRaisedForBlocking(victim)) {
                blockWithShield(victim, killer, protection.shieldPoints(), deathReason.toString());
            } else if (protection.vest() && gear.isVestWorn()) {
                breakVest(victim, killer, gear, deathReason);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Taotie's swallow is not a kill; NoellesRoles calls this right before its Iron Man check.
     * 饕餮吞噬不是击杀；NoellesRoles 在铁人药水检查前调用这里。
     */
    public static boolean tryBlockSwallow(ServerPlayerEntity target, ServerPlayerEntity taotie) {
        if (!BodyguardShieldService.isRaisedForBlocking(target) || !inFront(target, taotie.getPos())) {
            return false;
        }
        blockWithShield(target, taotie, BodyguardRules.SHIELD_SWALLOW_POINTS, "noellesroles:swallow");
        return true;
    }

    /**
     * A revolver shot the shield will stop costs no shooter anything, killer or civilian (owner rule): no gun drop,
     * no backfire, no shot-innocent death. Uses the same check as the kill itself, which follows in the same call.
     * 会被盾挡下的左轮射击不惩罚射手，杀手与平民都一样（所有者规则）：不掉枪、不走火、不因误杀而死。
     * 与随后同一调用中的击杀判定使用相同条件。
     */
    private static @Nullable ShouldPunishGunShooter.PunishResult shouldPunishGunShooter(PlayerEntity shooter, PlayerEntity victim) {
        if (victim instanceof ServerPlayerEntity target && shooter instanceof ServerPlayerEntity gunner
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && BodyguardProtectionRules.forReason(GameConstants.DeathReasons.GUN, false).shieldBlocks()
                && shieldFaces(target, gunner, BodyguardProtectionRules.Source.ATTACKER, null)) {
            return ShouldPunishGunShooter.PunishResult.cancel();
        }
        return null;
    }

    private static boolean shieldFaces(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            BodyguardProtectionRules.Source source,
            @Nullable Vec3d blast
    ) {
        if (!BodyguardShieldService.isRaisedForBlocking(victim)) {
            return false;
        }
        Vec3d origin;
        if (source == BodyguardProtectionRules.Source.BLAST && blast != null) {
            origin = blast;
        } else if (killer == null) {
            return false;
        } else if (source == BodyguardProtectionRules.Source.MELEE
                && victim.distanceTo(killer) > BodyguardRules.MELEE_MAX_DISTANCE) {
            // A knife kill from afar is a Black Raven mark or a Magician puppet: nobody is in front. / 远处的刀杀是黑鸦标记或魔术师傀儡：面前没有人。
            return false;
        } else {
            origin = killer.getPos();
        }
        return inFront(victim, origin);
    }

    private static boolean inFront(ServerPlayerEntity holder, Vec3d origin) {
        return BodyguardRules.isInFront(holder.getHeadYaw(), holder.getX(), holder.getZ(), origin.x, origin.z);
    }

    private static void blockWithShield(
            ServerPlayerEntity holder,
            @Nullable ServerPlayerEntity attacker,
            int points,
            String deathReason
    ) {
        holder.getServerWorld().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 0.8F + holder.getRandom().nextFloat() * 0.4F);
        recordBlock(holder, attacker, SHIELD_SOURCE, deathReason);

        PlayerStaminaComponent stamina = PlayerStaminaComponent.KEY.get(holder);
        if (stamina.isInfiniteStamina()) {
            return;
        }
        // Even a block the stamina cannot pay for still lands (owner rule); the shield then breaks.
        // 即使体力不够也照样挡下（所有者规则），随后破盾。
        float left = stamina.getSprintingTicks() - BodyguardRules.staminaCostTicks(points);
        if (left > 0.0F) {
            stamina.setSprintingTicks(left);
            stamina.sync();
            return;
        }
        stamina.setSprintingTicks(0.0F);
        stamina.setExhausted(true);
        stamina.sync();
        BodyguardShieldService.breakShield(holder);
    }

    private static void breakVest(
            ServerPlayerEntity wearer,
            @Nullable ServerPlayerEntity attacker,
            BodyguardGearComponent gear,
            Identifier deathReason
    ) {
        gear.breakVest();
        wearer.getServerWorld().playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
                SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 0.8F);
        recordBlock(wearer, attacker, VEST_SOURCE, deathReason.toString());
    }

    private static void recordBlock(
            ServerPlayerEntity holder,
            @Nullable ServerPlayerEntity attacker,
            String source,
            String deathReason
    ) {
        // Wathe's shield_blocked formatter turns the source into replay.shield_blocked.<ns>.<path>[.by].
        // Wathe 的 shield_blocked 格式化器把 source 转成 replay.shield_blocked.<命名空间>.<路径>[.by]。
        GameRecordManager.EventBuilder event = GameRecordManager.event(GameRecordTypes.SHIELD_BLOCKED)
                .actor(holder)
                .put("source", source)
                .put("death_reason", deathReason);
        if (attacker != null && attacker != holder) {
            event.target(attacker);
        }
        event.record();
    }
}
