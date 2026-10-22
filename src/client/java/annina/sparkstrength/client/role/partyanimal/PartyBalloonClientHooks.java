package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.component.partyanimal.PartyBalloonMarkComponent;
import annina.sparkstrength.role.partyanimal.PartyAnimalRules;
import annina.sparkstrength.role.partyanimal.PartyBalloonRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/**
 * Party Balloon outline: a living Party Animal always sees balloon-marked players in yellow, through walls and without
 * the instinct key, like NoellesRoles' helium pink. The mark only syncs to Party Animals, so nobody else can answer here.
 * Priority {@link PartyBalloonRules#HIGHLIGHT_PRIORITY} beats the helium pink (priority 0) on a player who is both.
 * The blackout and Undercover arbiters wrap {@code WatheClient.getInstinctHighlight} from outside, so they still apply.
 * 派对气球描边：存活的派对狂无需本能键即可穿墙常驻看到被塞气球的玩家（黄色），与 NoellesRoles 的氦气粉色相同方式。
 * 标记只同步给派对狂，其他人在这里不会作答。优先级 {@link PartyBalloonRules#HIGHLIGHT_PRIORITY} 高于氦气粉色（优先级 0），
 * 两者同时适用时显示黄色。熄灯与卧底裁决从外层包装 {@code WatheClient.getInstinctHighlight}，因此仍然生效。
 */
public final class PartyBalloonClientHooks {
    private static boolean registered;

    private PartyBalloonClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(PartyBalloonClientHooks::highlight);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity entity) {
        // Same target filter as NoellesRoles' listener: spectators and invisible players get no answer.
        // 与 NoellesRoles 监听器相同的目标过滤：旁观者与隐身玩家不作答。
        if (!(entity instanceof PlayerEntity target) || target.isSpectator() || target.isInvisible()) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer == target || !PartyBalloonMarkComponent.isMarked(target)) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        boolean viewerIsLivingPartyAnimal = WatheClient.isPlayerPlayingAndAlive()
                && PartyAnimalRules.isPartyAnimal(game.getRole(viewer));
        // NoellesRoles: a Survival Master behind an obstacle is immune to killer instinct outlines.
        // NoellesRoles：被障碍物遮挡的生存大师免疫杀手本能描边。
        boolean hiddenSurvivalMaster = game.isRole(target, Noellesroles.SURVIVAL_MASTER) && !viewer.canSee(target);
        if (!PartyBalloonRules.showsHighlight(viewerIsLivingPartyAnimal, GameFunctions.isPlayerPlayingAndAlive(target),
                true, hiddenSurvivalMaster)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(
                PartyBalloonRules.HIGHLIGHT_COLOR, PartyBalloonRules.HIGHLIGHT_PRIORITY);
    }
}
