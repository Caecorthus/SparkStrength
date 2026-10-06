package annina.sparkstrength.role.pathogen;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.morphling.MorphBodyDisguiseWorldComponent;
import annina.sparkstrength.component.pathogen.PathogenStrainComponent;
import annina.sparkstrength.component.pathogen.VirusCarrierComponent;
import annina.sparkstrength.compat.SparkTraitsReviveCompat;
import annina.sparkstrength.mixin.noellesroles.HiddenBodiesWorldComponentAccessor;
import annina.sparkstrength.mixin.wathe.GameWorldComponentDeadPlayersAccessor;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.util.AnnounceWelcomePayload;
import dev.doctor4t.wathe.util.ShopUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.pathogen.InfectedPlayerComponent;
import org.agmas.noellesroles.scavenger.HiddenBodiesWorldComponent;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * T-Virus (T病毒) on a corpse: the dead player stands up where the body lay and becomes a converted Pathogen (Q5, Q6).
 * The revive follows SparkTraits' Last Stand: leave Wathe's dead set, adventure mode, full health, back to the body,
 * body discarded, voice group reset. Then the inventory is cleared and the Pathogen role is set and announced through
 * {@code RoleAssigned}, so NoellesRoles gives its master key and infect cooldown. The balance is 0 and the shop is
 * rebuilt as Virus only. Kill rewards and replay death lines already written stay as they are.
 * 对尸体使用 T病毒：死者在尸体位置站起并成为转化病原体（Q5、Q6）。复活流程与 SparkTraits 背水一战一致：移出 Wathe 死亡
 * 名单、冒险模式、回满血、回到尸体处、移除尸体、重置语音分组。随后清空背包，设置病原体身份并通过 {@code RoleAssigned}
 * 通知（NoellesRoles 会发放万能钥匙并设置感染冷却），余额归零，商店重建为只有病毒。已发放的击杀奖励与已写入的回放死亡记录
 * 保持不变。
 */
public final class PathogenReviveService {
    public static final Identifier REVIVED_EVENT = SparkStrength.id("pathogen_revived");

    private PathogenReviveService() {
    }

    /** Server only: true when the revive happened (the caller consumes the item). / 仅服务端：复活成功时返回 true。 */
    public static boolean useTVirus(ServerPlayerEntity user, PlayerBodyEntity body) {
        if (!(body.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID ownerId = body.getPlayerUuid();
        ServerPlayerEntity owner = ownerId == null || world.getServer() == null
                ? null
                : world.getServer().getPlayerManager().getPlayer(ownerId);
        PathogenRules.ReviveVerdict verdict = PathogenRules.reviveVerdict(
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                        && VirusService.isLivingParticipant(user)
                        && PathogenVisibility.isPathogen(user),
                SwallowedPlayerComponent.isPlayerSwallowed(user),
                owner != null,
                ownerId != null && game.hasAnyRole(ownerId),
                ownerId != null && game.isPlayerDead(ownerId),
                owner != null && owner.isSpectator(),
                user.getUuid().equals(ownerId),
                ownerId != null && isNewestBody(world, body, ownerId),
                SparkTraitsReviveCompat.isFakeDeathBody(body),
                ownerId != null && HiddenBodiesWorldComponent.KEY.get(world).isHidden(ownerId),
                owner != null && SparkTraitsReviveCompat.isLastStandDeathIntercepted(owner)
        );
        if (verdict == PathogenRules.ReviveVerdict.UNAVAILABLE) {
            user.sendMessage(Text.translatable("message.sparkstrength.pathogen.revive_unavailable"), true);
            return false;
        }
        if (verdict != PathogenRules.ReviveVerdict.OK || owner == null) {
            user.sendMessage(Text.translatable("message.sparkstrength.pathogen.revive_failed"), true);
            return false;
        }
        revive(user, body, owner, world, game);
        return true;
    }

    private static void revive(ServerPlayerEntity user, PlayerBodyEntity body, ServerPlayerEntity owner,
                               ServerWorld world, GameWorldComponent game) {
        UUID ownerId = owner.getUuid();
        double x = body.getX();
        double y = body.getY();
        double z = body.getZ();
        float yaw = body.getYaw();

        // Alive again, exactly like Last Stand. / 与背水一战一样恢复存活。
        ((GameWorldComponentDeadPlayersAccessor) game).sparkstrength$getDeadPlayers().remove(ownerId);
        game.sync();
        owner.setCameraEntity(owner);
        owner.changeGameMode(GameMode.ADVENTURE);
        owner.clearStatusEffects();
        owner.setHealth(owner.getMaxHealth());
        owner.setAir(owner.getMaxAir());
        owner.fallDistance = 0.0F;
        owner.teleport(world, x, y, z, Set.of(), yaw, owner.getPitch());
        owner.getInventory().clear();
        PlayerPoisonComponent.KEY.get(owner).reset();
        InfectedPlayerComponent.KEY.get(owner).reset();
        VirusService.clearPlayer(owner);
        forgetDeathTraces(world, ownerId);

        // A converted Pathogen: its shop never sells the T-Virus. / 转化病原体：商店不出售 T病毒。
        PathogenStrainComponent strain = PathogenStrainComponent.KEY.get(owner);
        strain.clear();
        strain.markConverted();
        Role pathogen = Noellesroles.PATHOGEN;
        game.addRole(owner, pathogen);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(owner);
        try {
            // NoellesRoles: master key, infect cooldown by player count; other mods reset their role state.
            // NoellesRoles：发放万能钥匙并按人数设置感染冷却；其他模组重置各自的身份状态。
            RoleAssigned.EVENT.invoker().assignRole(owner, pathogen);
        } catch (RuntimeException exception) {
            // The role is committed: log the listener failure, never undo. / 身份已提交：记录监听器故障，绝不撤销。
            SparkStrength.LOGGER.error("T-Virus revive committed but a role-assignment listener failed for {}",
                    ownerId, exception);
        } finally {
            shop.setBalance(0);
            shop.initializeShop(ShopUtils.getShopEntriesForPlayer(owner));
            shop.sync();
            game.sync();
        }

        body.discard();
        resetVoiceChatGroup(ownerId);
        resyncPathogenViews(world);

        int killers = game.getStartingKillerCount();
        ServerPlayNetworking.send(owner, new AnnounceWelcomePayload(
                pathogen.identifier().toString(),
                killers,
                Math.max(0, game.getAllPlayers().size() - killers)
        ));
        owner.sendMessage(Text.translatable("message.sparkstrength.pathogen.revived"), false);
        user.sendMessage(Text.translatable("message.sparkstrength.pathogen.revive_done", owner.getDisplayName()),
                true);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.PLAYERS,
                1.0F, 1.0F);
        VirusService.recordPair(user, REVIVED_EVENT, owner);
    }

    /**
     * Only the owner's newest corpse may be revived; an older one is just a leftover from an earlier death.
     * 只能复活尸体主人最新的一具尸体；更早的尸体只是之前死亡的残留。
     */
    private static boolean isNewestBody(ServerWorld world, PlayerBodyEntity body, UUID ownerId) {
        for (Entity entity : world.iterateEntities()) {
            if (entity != body
                    && entity instanceof PlayerBodyEntity other
                    && !other.isRemoved()
                    && ownerId.equals(other.getPlayerUuid())
                    && other.getDeathGameTime() > body.getDeathGameTime()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Per-owner death state keyed by UUID that would otherwise follow the revived player into its next death.
     * 按 UUID 记录、否则会跟随复活玩家进入下一次死亡的尸体状态。
     */
    private static void forgetDeathTraces(ServerWorld world, UUID ownerId) {
        HiddenBodiesWorldComponent hidden = HiddenBodiesWorldComponent.KEY.get(world);
        if (((HiddenBodiesWorldComponentAccessor) hidden).sparkstrength$getHiddenBodies().remove(ownerId)) {
            HiddenBodiesWorldComponent.KEY.sync(world);
        }
        MorphBodyDisguiseWorldComponent.KEY.get(world).clearBodyDisguise(ownerId);
    }

    /**
     * Infection is synced only when it changes and carriers every few seconds; a new Pathogen needs them now.
     * 感染状态只在变化时同步、带毒状态每隔几秒同步；新病原体需要立即获得。
     */
    private static void resyncPathogenViews(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            InfectedPlayerComponent.KEY.sync(player);
            VirusCarrierComponent carrier = VirusCarrierComponent.KEY.get(player);
            if (carrier.isCarrier()) {
                carrier.sync();
            }
        }
    }

    /** Same reflective call SparkTraits' Last Stand uses. / 与 SparkTraits 背水一战相同的反射调用。 */
    private static void resetVoiceChatGroup(UUID uuid) {
        try {
            Class<?> plugin = Class.forName("dev.doctor4t.wathe.compat.TrainVoicePlugin");
            Method resetPlayer = plugin.getMethod("resetPlayer", UUID.class);
            resetPlayer.invoke(null, uuid);
        } catch (ReflectiveOperationException | LinkageError exception) {
            SparkStrength.LOGGER.debug("Unable to reset the voice chat group after a T-Virus revive", exception);
        }
    }
}
