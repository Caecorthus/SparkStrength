package annina.sparkstrength.client.role.coroner;

import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.api.event.ShouldShowCohort;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.bartender.BartenderPlayerComponent;
import org.agmas.noellesroles.bomber.BomberPlayerComponent;
import org.agmas.noellesroles.demonhunter.DemonHunterPlayerComponent;
import org.agmas.noellesroles.professor.IronManPlayerComponent;

import java.awt.Color;

/**
 * 验尸官伪装后的客户端本能提示。
 *
 * <p>当验尸官当前变形成“杀手阵营尸体身份”时，杀手阵营玩家会像看卧底一样，
 * 在按本能键时把他误认为同伙，并显示 cohort 提示。</p>
 */
public final class CoronerClientHooks {
    private static final int KILLER_COHORT_RED = MathHelper.hsvToRgb(0F, 1.0F, 0.6F);

    private CoronerClientHooks() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(CoronerClientHooks::highlightKillerDisguise);
        GetInstinctHighlight.EVENT.register(CoronerClientHooks::highlightBorrowedRoleInstincts);
        ShouldShowCohort.EVENT.register(CoronerClientHooks::cohortPrompt);
    }

    private static GetInstinctHighlight.HighlightResult highlightKillerDisguise(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer) || targetPlayer.isSpectator()) {
            return null;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!game.canUseKillerFeatures(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                || !(CoronerService.hasKillerFactionDisguise(targetPlayer)
                || CoronerService.hasUndercoverDisguise(targetPlayer))) {
            return null;
        }

        return GetInstinctHighlight.HighlightResult.withKeybind(
                KILLER_COHORT_RED,
                GetInstinctHighlight.HighlightResult.PRIORITY_HIGH
        );
    }

    private static ShouldShowCohort.CohortResult cohortPrompt(PlayerEntity viewer, PlayerEntity target) {
        if (viewer == null || target == null) {
            return null;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        if (!game.canUseKillerFeatures(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(target)
                || !(CoronerService.hasKillerFactionDisguise(target)
                || CoronerService.hasUndercoverDisguise(target))) {
            return null;
        }

        return ShouldShowCohort.CohortResult.show();
    }

    private static GetInstinctHighlight.HighlightResult highlightBorrowedRoleInstincts(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer) || !GameFunctions.isPlayerPlayingAndAlive(targetPlayer)) {
            return null;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        boolean hiddenSurvivalMaster = (game.isRole(targetPlayer, Noellesroles.SURVIVAL_MASTER)
                || CoronerService.hasSurvivalMasterDisguise(targetPlayer)) && !viewer.canSee(targetPlayer);
        if (hiddenSurvivalMaster) {
            return GetInstinctHighlight.HighlightResult.skip();
        }

        if (CoronerService.hasBomberDisguise(viewer)
                && BomberPlayerComponent.KEY.get(targetPlayer).hasBomb()) {
            return GetInstinctHighlight.HighlightResult.always(Noellesroles.BOMBER.color());
        }

        if (CoronerService.hasDemonHunterDisguise(viewer)) {
            DemonHunterPlayerComponent hunterComp = DemonHunterPlayerComponent.KEY.get(viewer);
            if (hunterComp.isPlayerFrenzied(targetPlayer.getUuid())
                    && !game.isRole(targetPlayer, Noellesroles.SILENCER)) {
                return GetInstinctHighlight.HighlightResult.always(Noellesroles.DEMON_HUNTER.color());
            }
        }

        if (PlayerPsychoComponent.KEY.get(viewer).getPsychoTicks() > 0
                && !game.isRole(viewer, Noellesroles.JESTER)
                && CoronerService.hasDemonHunterDisguise(targetPlayer)) {
            return GetInstinctHighlight.HighlightResult.always(Noellesroles.DEMON_HUNTER.color());
        }

        if (CoronerService.hasBartenderDisguise(viewer) && viewer.canSee(targetPlayer)
                && BartenderPlayerComponent.KEY.get(targetPlayer).glowTicks > 0) {
            return GetInstinctHighlight.HighlightResult.always(Color.GREEN.getRGB());
        }

        if (CoronerService.hasProfessorDisguise(viewer) && viewer.canSee(targetPlayer)
                && IronManPlayerComponent.KEY.get(targetPlayer).hasBuff()) {
            return GetInstinctHighlight.HighlightResult.always(Color.BLUE.getRGB());
        }

        if (CoronerService.hasToxicologistDisguise(viewer) && viewer.canSee(targetPlayer)
                && PlayerPoisonComponent.KEY.get(targetPlayer).poisonTicks > 0) {
            return GetInstinctHighlight.HighlightResult.always(Noellesroles.TOXICOLOGIST.color());
        }

        if (CoronerService.hasPoisonerDisguise(viewer)
                && PlayerPoisonComponent.KEY.get(targetPlayer).poisonTicks > 0) {
            /*
             * 验尸官伪装为毒师时，沿用 NoellesRoles 毒师的中毒透视：
             * 不要求视线，颜色使用毒师职业色；上方 hiddenSurvivalMaster 的 skip 已经保证
             * 生存大师在被遮挡时仍能免疫杀手阵营/毒师这类穿墙本能。
             */
            return GetInstinctHighlight.HighlightResult.always(Noellesroles.POISONER.color());
        }

        return null;
    }
}
