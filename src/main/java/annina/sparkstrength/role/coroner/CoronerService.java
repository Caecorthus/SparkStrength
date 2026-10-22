package annina.sparkstrength.role.coroner;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsReviveCompat;
import annina.sparkstrength.component.coroner.CoronerBodySnapshotComponent;
import annina.sparkstrength.component.coroner.CoronerPlayerComponent;
import annina.sparkstrength.component.morphling.MorphBodyDisguiseWorldComponent;
import annina.sparkstrength.component.morphling.MorphMarkPlayerComponent;
import annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent;
import annina.sparkstrength.component.timekeeper.TimekeeperWatchComponent;
import annina.sparkstrength.record.AchievementRecordRules;
import annina.sparkstrength.record.AchievementRecords;
import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.PsychoModeEvents;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.demonhunter.DemonHunterPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 验尸官增强的服务端权威逻辑。
 *
 * <p>客户端只负责显示头像按钮和提交 UUID；是否是验尸官、这具尸体是否解锁、变形成哪张脸、
 * 发什么临时装备、何时回收装备，都统一在这里判断。</p>
 *
 * <p>This class is the entry point: registration, the world tick, the body bag, disguise selection and the death hooks.
 * The disguise queries live in {@link CoronerDisguises} and the loaned gear in {@link CoronerLoanedGear}.
 * 本类是入口：注册、世界 tick、采尸袋、伪装选择与死亡钩子。伪装查询位于 {@link CoronerDisguises}，
 * 临时装备位于 {@link CoronerLoanedGear}。</p>
 */
public final class CoronerService {
    private static boolean registered;

    private CoronerService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ShouldPunishGunShooter.EVENT.register(CoronerService::shouldPunishGunShooter);
        PsychoModeEvents.ON_PSYCHO_START.register(CoronerService::onPsychoStart);
        PsychoModeEvents.ON_PSYCHO_END.register(CoronerService::onPsychoEnd);
        CoronerLoanedGear.registerLoadedItemTracking();
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        if (CoronerRules.isCoroner(role)) {
            CoronerPlayerComponent.KEY.get(player).initializeForRole();
            CoronerLoanedGear.removeTemporaryItems(player);
        } else {
            clearPlayer(player);
        }
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        CoronerLoanedGear.removeTemporaryItems(player);
        TimekeeperWatchComponent.KEY.get(player).reset();
        DemonHunterPlayerComponent.KEY.get(player).reset();
        CoronerPlayerComponent.KEY.get(player).clearAll();
    }

    public static void afterKill(ServerPlayerEntity victim) {
        CoronerLoanedGear.removeTemporaryItems(victim);
        CoronerLoanedGear.removeDroppedTemporaryItems(victim);
        TimekeeperWatchComponent.KEY.get(victim).reset();
        DemonHunterPlayerComponent.KEY.get(victim).reset();
        CoronerPlayerComponent.KEY.get(victim).clearAll();
    }

    public static void tick(ServerWorld world) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!GameFunctions.isPlayerPlayingAndAlive(player)
                    || !CoronerRules.isCoroner(game.getRole(player))) {
                continue;
            }
            rewardNearbyBodies(player, world);
            CoronerLoanedGear.refreshTemporaryEquipmentForMorphPriority(player);
            if (CoronerDisguises.hasDemonHunterDisguise(player)) {
                CoronerLoanedGear.refreshDemonHunterState(player);
            }
        }
    }

    /**
     * 由 GameFunctions 的尸体生成 mixin 调用，在尸体落地前写入“死亡时身份快照”。
     */
    public static void recordBodySnapshot(ServerPlayerEntity victim, PlayerBodyEntity body) {
        Role deathRole = GameWorldComponent.KEY.get(victim.getWorld()).getRole(victim);
        Identifier roleId = deathRole == null ? CoronerRules.WATHE_NO_ROLE_ID : deathRole.identifier();
        CoronerBodySnapshotComponent.KEY.get(body).setRoleId(roleId);
    }

    public static boolean collectBodyWithBag(ServerPlayerEntity coroner, PlayerBodyEntity body) {
        /*
         * Fake corpses are refused (owner 2026-10-16): false makes CoronerBodyBagItem pass, so the bag is kept, the
         * corpse stays, and no disguise or coins come from a living player's body.
         * 拒绝假尸体（所有者 2026-10-16）：返回 false 时采尸袋物品直接放行，袋子不消耗、尸体留在原地，
         * 也不会从活人的尸体解锁伪装或拿到金币。
         */
        if (!canUseCoronerAbility(coroner) || body == null || !isRealBody(coroner.getServerWorld(), body)) {
            return false;
        }

        UUID bodyOwnerUuid = body.getPlayerUuid();
        CoronerPlayerComponent.BodySnapshot snapshot = snapshotFromBody(coroner, body);
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(coroner);
        component.unlockBody(bodyOwnerUuid, snapshot);
        rewardBodyProximityIfEligible(coroner, component, body);
        recordBodyBagUse(coroner, body, snapshot);
        return true;
    }

    public static boolean selectDisguise(ServerPlayerEntity coroner, UUID targetUuid) {
        if (!canUseCoronerAbility(coroner) || targetUuid == null) {
            return false;
        }

        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(coroner);
        UUID previousUuid = component.activeDisguiseUuid();
        Identifier previousRoleId = component.activeDisguiseRoleId();
        if (targetUuid.equals(coroner.getUuid())) {
            CoronerLoanedGear.removeTemporaryItems(coroner);
            TimekeeperWatchComponent.KEY.get(coroner).reset();
            DemonHunterPlayerComponent.KEY.get(coroner).reset();
            component.clearDisguise();
            recordDisguiseChange(coroner, previousUuid, previousRoleId, null, null);
            return true;
        }

        if (!component.knowsDisguise(targetUuid)) {
            return false;
        }

        CoronerPlayerComponent.BodySnapshot snapshot = component.snapshot(targetUuid);
        if (snapshot == null) {
            return false;
        }

        CoronerLoanedGear.removeTemporaryItems(coroner);
        TimekeeperWatchComponent.KEY.get(coroner).reset();
        DemonHunterPlayerComponent.KEY.get(coroner).reset();
        component.setActiveDisguise(targetUuid, snapshot);
        if (CoronerDisguises.isHigherPriorityMorphActive(coroner)) {
            /*
             * SparkStrength/Noelles 的变形怪效果优先级高于验尸官长期伪装。
             * 高优先级变形存在时，验尸官先记住自己原本选中的尸体身份，但暂时不发放该身份装备。
             */
            component.setTemporaryEquipmentSuppressed(true);
        } else {
            CoronerLoanedGear.grantTemporaryEquipment(coroner, targetUuid, snapshot);
        }
        recordDisguiseChange(coroner, previousUuid, previousRoleId, targetUuid, component.activeDisguiseRoleId());
        return true;
    }

    /**
     * Achievement record for the Coroner's own disguise choice: only a real change (re-selecting the same body or
     * clearing nothing records nothing); a null role means the disguise was cleared.
     * 验尸官主动选择伪装时的成就记录：只记录真实变化（重选同一具尸体或无伪装时清除都不记录）；role 为 null 表示已清除。
     */
    private static void recordDisguiseChange(ServerPlayerEntity coroner, @Nullable UUID previousUuid,
                                             @Nullable Identifier previousRoleId, @Nullable UUID nextUuid,
                                             @Nullable Identifier nextRoleId) {
        if (AchievementRecordRules.disguiseChanged(previousUuid, idString(previousRoleId), nextUuid, idString(nextRoleId))) {
            AchievementRecords.coronerDisguise(coroner, nextRoleId);
        }
    }

    private static @Nullable String idString(@Nullable Identifier id) {
        return id == null ? null : id.toString();
    }

    /*
     * Disguise queries live in CoronerDisguises. These delegates keep the names other code binds to; SparkWitch and
     * SparkTraits look up hasKidnapperDisguise, hasKillerFactionDisguise, hasUndercoverDisguise and
     * hasInstantSilentKnifeDisguise on this class by reflection, so their names and signatures are a cross-mod contract.
     * 伪装查询位于 CoronerDisguises。这些委托保留其它代码绑定的方法名；SparkWitch 与 SparkTraits 会按本类反射查找
     * hasKidnapperDisguise、hasKillerFactionDisguise、hasUndercoverDisguise 与 hasInstantSilentKnifeDisguise，
     * 它们的名称与签名是跨模组契约。
     */
    public static @Nullable UUID activeDisguiseUuid(PlayerEntity player) {
        return CoronerDisguises.activeDisguiseUuid(player);
    }

    public static @Nullable Role activeDisguiseRoleForRules(PlayerEntity player) {
        return CoronerDisguises.activeDisguiseRole(player);
    }

    public static boolean hasKillerFactionDisguise(PlayerEntity player) {
        return CoronerDisguises.hasKillerFactionDisguise(player);
    }

    public static boolean hasVeteranDisguise(PlayerEntity player) {
        return CoronerDisguises.hasVeteranDisguise(player);
    }

    public static boolean hasInstantSilentKnifeDisguise(PlayerEntity player) {
        return CoronerDisguises.hasInstantSilentKnifeDisguise(player);
    }

    public static boolean hasWaiterDisguise(PlayerEntity player) {
        return CoronerDisguises.hasWaiterDisguise(player);
    }

    public static boolean hasCorruptCopDisguise(PlayerEntity player) {
        return CoronerDisguises.hasCorruptCopDisguise(player);
    }

    public static boolean hasPoisonerDisguise(PlayerEntity player) {
        return CoronerDisguises.hasPoisonerDisguise(player);
    }

    public static boolean hasToxicologistDisguise(PlayerEntity player) {
        return CoronerDisguises.hasToxicologistDisguise(player);
    }

    public static boolean hasProfessorDisguise(PlayerEntity player) {
        return CoronerDisguises.hasProfessorDisguise(player);
    }

    public static boolean hasAttendantDisguise(PlayerEntity player) {
        return CoronerDisguises.hasAttendantDisguise(player);
    }

    public static boolean hasTimekeeperDisguise(PlayerEntity player) {
        return CoronerDisguises.hasTimekeeperDisguise(player);
    }

    public static boolean hasUndercoverDisguise(PlayerEntity player) {
        return CoronerDisguises.hasUndercoverDisguise(player);
    }

    public static boolean hasNoisemakerDisguise(PlayerEntity player) {
        return CoronerDisguises.hasNoisemakerDisguise(player);
    }

    public static boolean hasSilencerDisguise(PlayerEntity player) {
        return CoronerDisguises.hasSilencerDisguise(player);
    }

    public static boolean hasBartenderDisguise(PlayerEntity player) {
        return CoronerDisguises.hasBartenderDisguise(player);
    }

    public static boolean hasEngineerDisguise(PlayerEntity player) {
        return CoronerDisguises.hasEngineerDisguise(player);
    }

    public static boolean hasBomberDisguise(PlayerEntity player) {
        return CoronerDisguises.hasBomberDisguise(player);
    }

    public static boolean hasKidnapperDisguise(PlayerEntity player) {
        return CoronerDisguises.hasKidnapperDisguise(player);
    }

    public static boolean isActualCoroner(PlayerEntity player) {
        return CoronerDisguises.isActualCoroner(player);
    }

    public static boolean canUseToxicologistDisguiseItem(PlayerEntity player) {
        return CoronerDisguises.canUseToxicologistDisguiseItem(player);
    }

    public static boolean canUseProfessorDisguiseItem(PlayerEntity player) {
        return CoronerDisguises.canUseProfessorDisguiseItem(player);
    }

    public static boolean canUseBartenderDisguiseItem(PlayerEntity player) {
        return CoronerDisguises.canUseBartenderDisguiseItem(player);
    }

    public static boolean canUseEngineerDisguiseItem(PlayerEntity player) {
        return CoronerDisguises.canUseEngineerDisguiseItem(player);
    }

    public static boolean hasNeutralDisguise(PlayerEntity player) {
        return CoronerDisguises.hasNeutralDisguise(player);
    }

    private static void rewardNearbyBodies(ServerPlayerEntity coroner, ServerWorld world) {
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(coroner);
        Box searchBox = coroner.getBoundingBox().expand(CoronerRules.BODY_PROXIMITY_RANGE);
        for (PlayerBodyEntity body : world.getEntitiesByClass(PlayerBodyEntity.class, searchBox,
                body -> body.squaredDistanceTo(coroner) <= CoronerRules.BODY_PROXIMITY_RANGE_SQUARED)) {
            rewardBodyProximityIfEligible(coroner, component, body);
        }
    }

    private static void rewardBodyProximityIfEligible(
            ServerPlayerEntity coroner,
            CoronerPlayerComponent component,
            PlayerBodyEntity body
    ) {
        if (body.squaredDistanceTo(coroner) > CoronerRules.BODY_PROXIMITY_RANGE_SQUARED) {
            return;
        }
        UUID bodyEntityUuid = body.getUuid();
        if (component.hasRewardedBody(bodyEntityUuid)) {
            return;
        }
        if (!isRealBody(coroner.getServerWorld(), body)) {
            // Not marked: a fake corpse still lying here when its owner really dies pays then.
            // 不记入已领：假尸体在主人真正死亡后仍在原地时，届时照常付钱。
            return;
        }
        component.markRewardedBody(bodyEntityUuid);
        PlayerShopComponent.KEY.get(coroner).addToBalance(CoronerRules.BODY_PROXIMITY_REWARD);
    }

    /**
     * Fake corpse guard (owner decision 2026-10-16): a corpse counts only while Wathe lists its owner as dead, which
     * covers every mod's fake corpse (SparkWitch Fiend feign death, NoellesRoles Jester, SparkTraits Depression)
     * without depending on any of them. SparkTraits' Last Stand pending corpse can sit on a dead-listed owner, so its
     * public facade is asked as well; without SparkTraits that answer is false. A fake corpse pays no proximity coins
     * and cannot be bagged.
     * 假尸体判定（所有者 2026-10-16 决定）：只有 Wathe 把尸体主人记为死亡时尸体才算数，无需依赖任何模组即可覆盖
     * SparkWitch 魔人假死、NoellesRoles 小丑、SparkTraits 抑郁等假尸体。SparkTraits 背水一战待决尸体的主人可能已在死亡
     * 名单中，因此同时询问其公开门面；未安装 SparkTraits 时该查询为 false。假尸体不给靠近金币，也不能装袋。
     *
     * <p>A Partygoer corpse (owner 2026-10-21) is owned by the Partygoer's UUID, which Wathe never lists as dead, so the
     * dead-list check already refuses it; the explicit marker check keeps it refused should such a corpse ever be given
     * a player's UUID.
     * 派对客尸体（所有者 2026-10-21）的主人是派对客的 UUID，Wathe 永远不会把它记为死亡，死亡名单检查已能拒绝它；
     * 显式的标记检查保证即使这种尸体将来改用玩家 UUID 也依然被拒绝。</p>
     */
    private static boolean isRealBody(ServerWorld world, PlayerBodyEntity body) {
        UUID ownerUuid = body.getPlayerUuid();
        return ownerUuid != null
                && GameWorldComponent.KEY.get(world).isPlayerDead(ownerUuid)
                && !SparkTraitsReviveCompat.isFakeDeathBody(body)
                && !PartygoerCorpseComponent.isPartygoerCorpse(body);
    }

    private static boolean canUseCoronerAbility(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return CoronerRules.isCoroner(role)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player);
    }

    private static CoronerPlayerComponent.BodySnapshot snapshotFromBody(ServerPlayerEntity coroner, PlayerBodyEntity body) {
        CoronerBodySnapshotComponent bodySnapshot = CoronerBodySnapshotComponent.KEY.get(body);
        Identifier roleId = bodySnapshot.roleId();
        if (!bodySnapshot.hasSnapshot()) {
            /*
             * 正常路径一定会由 GameFunctions mixin 写入尸体快照。
             * 这个兜底只服务于旧存档/其它模组临时生成的 PlayerBodyEntity，避免采尸袋完全失效。
             */
            Role fallbackRole = GameWorldComponent.KEY.get(coroner.getWorld()).getRole(body.getPlayerUuid());
            if (fallbackRole != null) {
                roleId = fallbackRole.identifier();
            }
        }
        return new CoronerPlayerComponent.BodySnapshot(roleId);
    }

    // GameFunctionsMixin asks this so a dying Coroner drops none of the loaned gear.
    // GameFunctionsMixin 借此保证验尸官死亡时不掉落任何临时装备。
    public static boolean isTemporaryGrant(ItemStack stack) {
        return CoronerLoanedGear.isTemporaryGrant(stack);
    }

    public static void recordDisguisedBodyIfNeeded(ServerPlayerEntity victim) {
        if (victim == null || MorphMarkPlayerComponent.KEY.get(victim).isActive()) {
            return;
        }
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(victim);
        UUID disguiseUuid = component.activeDisguiseUuid();
        Role disguiseRole = CoronerDisguises.activeDisguiseRole(victim);
        if (disguiseUuid == null || disguiseRole == null) {
            return;
        }
        /*
         * 验尸官伪装期间死亡时，尸体应展示为当前尸体身份的外观。
         * 这里复用 SparkStrength 变形试剂的尸体外观世界组件，但上方先检查 MorphMarkPlayerComponent，
         * 确保变形试剂死亡外观拥有更高优先级，验尸官不会覆盖它。
         */
        MorphBodyDisguiseWorldComponent.KEY.get(victim.getServerWorld())
                .recordBodyDisguise(victim.getUuid(), disguiseUuid, disguiseName(victim, disguiseUuid));
    }

    public static void applyNoisemakerBodyDeathEffects(ServerPlayerEntity victim, PlayerBodyEntity body) {
        if (!CoronerDisguises.hasNoisemakerDisguise(victim)) {
            return;
        }
        /*
         * 验尸官顶着大嗓门尸体身份死亡时，尸体发光和全体无辜提示也按大嗓门处理。
         * 这发生在 CoronerService.afterKill 清空伪装之前，因此能读到死亡瞬间的伪装身份。
         */
        body.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60, 0));
        ServerWorld serverWorld = victim.getServerWorld();
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(serverWorld);
        RegistryEntry<SoundEvent> soundEntry = RegistryEntry.of(SoundEvents.ENTITY_ALLAY_DEATH);
        long seed = serverWorld.random.nextLong();
        for (ServerPlayerEntity player : serverWorld.getServer().getPlayerManager().getPlayerList()) {
            if (gameWorld.isInnocent(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
                player.networkHandler.sendPacket(new PlaySoundS2CPacket(soundEntry, SoundCategory.PLAYERS,
                        player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, seed));
                player.sendMessage(Text.translatable("noellesroles.noisemaker.death_scream"), true);
            }
        }
    }

    private static void onPsychoStart(ServerPlayerEntity frenziedPlayer, dev.doctor4t.wathe.api.event.PsychoType type) {
        for (ServerPlayerEntity hunter : frenziedPlayer.getServerWorld().getPlayers()) {
            if (hunter == frenziedPlayer || !CoronerDisguises.hasDemonHunterDisguise(hunter)
                    || !GameFunctions.isPlayerPlayingAndAlive(hunter)) {
                continue;
            }
            DemonHunterPlayerComponent.KEY.get(hunter).addFrenzyPlayer(frenziedPlayer.getUuid());
            CoronerLoanedGear.ensureTemporaryDemonHunterPistol(hunter);
        }
    }

    private static void onPsychoEnd(ServerPlayerEntity frenziedPlayer, dev.doctor4t.wathe.api.event.PsychoType type) {
        for (ServerPlayerEntity hunter : frenziedPlayer.getServerWorld().getPlayers()) {
            if (!CoronerDisguises.hasDemonHunterDisguise(hunter)) {
                continue;
            }
            DemonHunterPlayerComponent.KEY.get(hunter).removeFrenzyPlayer(frenziedPlayer.getUuid());
            CoronerLoanedGear.refreshDemonHunterState(hunter);
        }
    }

    private static String disguiseName(ServerPlayerEntity player, UUID disguiseUuid) {
        ServerPlayerEntity disguisePlayer = player.getServer().getPlayerManager().getPlayer(disguiseUuid);
        if (disguisePlayer != null) {
            return disguisePlayer.getGameProfile().getName();
        }
        GameProfile profile = GameWorldComponent.KEY.get(player.getWorld()).getGameProfiles().get(disguiseUuid);
        return profile == null ? disguiseUuid.toString().substring(0, 8) : profile.getName();
    }

    private static @Nullable ShouldPunishGunShooter.PunishResult shouldPunishGunShooter(
            PlayerEntity shooter,
            PlayerEntity victim
    ) {
        /*
         * 用户确认：黑警尸体是中立阵营；验尸官顶着黑警尸体开枪误伤无辜者时，
         * 按黑警处理，不触发“误杀好人导致自己死亡”的惩罚。
         */
        return CoronerDisguises.hasCorruptCopDisguise(shooter)
                ? ShouldPunishGunShooter.PunishResult.cancel()
                : null;
    }

    private static void recordBodyBagUse(
            ServerPlayerEntity coroner,
            PlayerBodyEntity body,
            CoronerPlayerComponent.BodySnapshot snapshot
    ) {
        NbtCompound extra = new NbtCompound();
        extra.putUuid("body_uuid", body.getUuid());
        extra.putUuid("body_owner", body.getPlayerUuid());
        extra.putString("body_role", snapshot.roleId().toString());
        Vec3d bodyPos = body.getPos();
        GameRecordManager.putPos(extra, "body_pos", bodyPos);
        GameRecordManager.recordItemUse(
                coroner,
                Registries.ITEM.getId(SparkStrengthItems.coronerBodyBag()),
                null,
                extra
        );
    }

    public static void playBodyBagSound(ServerWorld world, PlayerBodyEntity body) {
        world.playSound(
                null,
                body.getX(),
                body.getY() + 0.1F,
                body.getZ(),
                SoundEvents.ITEM_BUNDLE_INSERT,
                SoundCategory.PLAYERS,
                0.5F,
                1.0F + world.random.nextFloat() * 0.1F - 0.05F
        );
    }
}
