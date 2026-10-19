package annina.sparkstrength.sabotage;

import annina.sparkstrength.role.coroner.CoronerService;
import annina.sparkstrength.role.undercover.UndercoverRoles;
import annina.sparkstrength.role.undercover.UndercoverRules;
import annina.sparkstrength.tablet.TabletChannel;
import annina.sparkstrength.tablet.TabletChannelResolver;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The one permission check of the sabotage system: tablet page, prying, hacking and triggering all ask it.
 * 破坏系统唯一的权限判定：平板页面、撬开、入侵与触发都只问它。
 *
 * <p>"May sabotage" is exactly "belongs to the killer tablet network by identity": real killers, the Impostor trait
 * and the NoellesRoles Undercover (who must not be exposed by a page teammates have), while the witch faction (checked
 * first, so a recruited Accomplice that kept Impostor stays out), police and Conscience killers are excluded. See
 * {@code TabletChannelRules#allowed}. The identity facts are owner-synced, so the client can ask the same question
 * about itself.
 * 「可以破坏」正好等于「按身份属于杀手平板网络」：真杀手、内鬼词条，以及 NoellesRoles 卧底（不能因为队友有这一页而他没有
 * 就露馅）；魔女阵营（最先判断，保留内鬼词条的被招募共犯也排除在外）、警察与良心杀手不行。见 TabletChannelRules#allowed。
 * 这些身份事实会同步给本人，所以客户端也能对自己问同样的问题。</p>
 *
 * <p>The reworked Undercover (2026-10-19) keeps the page in both stages, but only pries and hacks in stage 1 and only
 * triggers in stage 2, on its own track. / 重做后的卧底（2026-10-19）两个阶段都有破坏页，但只在第一阶段撬箱入侵、只在第二阶段
 * 用自己的轨道触发。</p>
 */
public final class SabotageAccess {
    public static final Identifier ENGINEER_ROLE_ID = Identifier.of("noellesroles", "engineer");

    private SabotageAccess() {
    }

    /** Identity only, side-agnostic. / 只看身份，与端无关。 */
    public static boolean hasSabotageIdentity(PlayerEntity player) {
        return player != null && TabletChannelResolver.identityChannels(player).contains(TabletChannel.KILLER);
    }

    /**
     * Server: identity plus alive in a running round. This is the sabotage page and the notices; what the player may
     * actually do on it is {@link #canPryOrHack} / {@link #canTrigger}.
     * 服务端：身份，且在进行中的对局里存活。这决定破坏页与通知；具体能做什么看 canPryOrHack / canTrigger。
     */
    public static boolean canUse(ServerPlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE && hasSabotageIdentity(player);
    }

    /**
     * Junction boxes: everyone with the page, except a stage-2 Undercover, whose own track needs no box.
     * 接线箱：有破坏页的人都可以，二阶段卧底除外（它自己的轨道不需要箱子）。
     */
    public static boolean canPryOrHack(ServerPlayerEntity player) {
        return canUse(player) && UndercoverRules.mayPryOrHack(UndercoverRoles.isUndercover(player),
                UndercoverRoles.isStage2(player));
    }

    /** Triggering: an Undercover only once it has put the CQCM mask on (stage 2). / 触发：卧底只有戴过 CQCM 面具（第二阶段）后才行。 */
    public static boolean canTrigger(ServerPlayerEntity player) {
        return canUse(player) && UndercoverRules.mayTrigger(UndercoverRoles.isUndercover(player),
                UndercoverRoles.isStage2(player));
    }

    /**
     * Which sabotage track {@code player} triggers on: its own uuid for a stage-2 Undercover, null for the killer team
     * (stage-1 Undercovers included, who watch the team's track).
     * player 触发时使用的轨道：二阶段卧底是自己的 uuid，杀手团队为 null（第一阶段卧底也看团队轨道）。
     */
    public static @Nullable UUID trackOf(PlayerEntity player) {
        return UndercoverRoles.isStage2(player) ? player.getUuid() : null;
    }

    /** The real Engineer, or a Coroner disguised as one, may repair junction boxes. / 真工程师或伪装成工程师的验尸官可以修复接线箱。 */
    public static boolean isEngineer(ServerPlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        return (role != null && ENGINEER_ROLE_ID.equals(role.identifier())) || CoronerService.hasEngineerDisguise(player);
    }

    /** Anyone alive in a running round may repair a circuit breaker, killers included. / 进行中的对局里任何存活玩家都能修电闸，杀手也可以。 */
    public static boolean canRepairBreaker(ServerPlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }
}
