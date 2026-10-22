package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.partyanimal.PartyBalloonMarkComponent;
import annina.sparkstrength.replay.SparkStrengthReplayFormatters;
import annina.sparkstrength.role.undercover.UndercoverRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;

/**
 * Server side of the Party Balloon (派对气球). Rules live in {@link PartyBalloonRules}; this class reads the world,
 * applies the mark and tells only the Party Animal. The recipient gets no message, sound or item.
 * 派对气球的服务端逻辑。规则位于 {@link PartyBalloonRules}；这里读取世界状态、写入标记并只通知派对狂。
 * 被塞气球的人不会收到任何消息、声音或物品。
 */
public final class PartyBalloonService {
    /** SparkFactionAPI action id for giving a balloon. / 塞气球在 SparkFactionAPI 中的行为 id。 */
    public static final Identifier BALLOON_ACTION_ID = SparkStrength.id("party_balloon");

    private static final float SQUEAK_VOLUME = 0.35F;
    private static final float SQUEAK_PITCH = 1.6F;

    private PartyBalloonService() {
    }

    /**
     * Server only: tries to give {@code target} a balloon; true when it was given (the caller consumes one).
     * 仅服务端：尝试把气球塞给 {@code target}；成功时返回 true（由调用方消耗一个）。
     */
    public static boolean giveBalloon(ServerPlayerEntity user, ServerPlayerEntity target) {
        GameWorldComponent game = GameWorldComponent.KEY.get(user.getWorld());
        boolean userIsLivingPartyAnimal = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && isLivingParticipant(user)
                && PartyAnimalRules.isPartyAnimal(game.getRole(user));
        PartyBalloonRules.Verdict verdict = PartyBalloonRules.verdict(
                userIsLivingPartyAnimal,
                SwallowedPlayerComponent.isPlayerSwallowed(user),
                target != user && isLivingParticipant(target),
                SwallowedPlayerComponent.isPlayerSwallowed(target),
                SparkFactionCompat.canAffectPlayer(user, target, BALLOON_ACTION_ID),
                SparkTraitsCompat.isEffectiveKiller(game.getRole(target), target),
                UndercoverRoles.isUndercover(target),
                UndercoverRoles.isStage2(target),
                PartyBalloonMarkComponent.isMarked(target),
                target.getBoundingBox().squaredMagnitude(user.getEyePos()),
                user.canSee(target)
        );
        switch (verdict) {
            case UNAVAILABLE -> {
                user.sendMessage(Text.translatable("message.sparkstrength.party_balloon.unavailable"), true);
                return false;
            }
            case KILLER_TARGET -> {
                user.sendMessage(Text.translatable("message.sparkstrength.party_balloon.killer_target"), true);
                return false;
            }
            case ALREADY_MARKED -> {
                user.sendMessage(Text.translatable("message.sparkstrength.party_balloon.already_marked"), true);
                return false;
            }
            case OUT_OF_RANGE -> {
                user.sendMessage(Text.translatable("message.sparkstrength.party_balloon.out_of_range"), true);
                return false;
            }
            case OK -> {
            }
        }

        PartyBalloonMarkComponent.KEY.get(target).mark(user.getUuid());
        // A soft pop for the giver only; the recipient must not hear a thing. / 只有塞气球的人听到轻轻一声，对方毫无察觉。
        user.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, SQUEAK_VOLUME, SQUEAK_PITCH);
        user.sendMessage(Text.translatable("message.sparkstrength.party_balloon.given", target.getDisplayName()), true);
        NbtCompound extra = new NbtCompound();
        extra.putUuid("target", target.getUuid());
        GameRecordManager.recordGlobalEvent(user.getServerWorld(), SparkStrengthReplayFormatters.PARTY_BALLOON_GIVEN,
                user, extra);
        return true;
    }

    /** Death, reset and round end. / 死亡、重置与局末。 */
    public static void clearPlayer(PlayerEntity player) {
        PartyBalloonMarkComponent.KEY.get(player).clear();
    }

    private static boolean isLivingParticipant(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player);
    }
}
