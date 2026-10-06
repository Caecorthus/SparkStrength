package annina.sparkstrength.client.role.pathogen;

import annina.sparkstrength.component.pathogen.VirusCarrierComponent;
import annina.sparkstrength.role.pathogen.PathogenRules;
import annina.sparkstrength.role.pathogen.PathogenVisibility;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Pathogen buff outlines (Q10, Q11). A living Pathogen sees fellow Pathogens through walls at any distance and carriers
 * in dark green with line of sight. A living Toxicologist (Black Raven acting overlay and Coroner disguise included)
 * sees carriers in green, or purple when they are also poisoned, with line of sight.
 * <p>
 * Priority {@code PRIORITY_DEFAULT + 40} beats NoellesRoles' own outlines (priority 0: Pathogen lime, Toxicologist
 * poison) but stays below SparkWitch's Black Raven teammate colour (+50), explicit skips (+100) and the Professor's
 * invisibility (300). Whole-method vetoes such as SparkWitch's Fear and SparkStrength's Corrupt Cop concealment still
 * hide everything. SparkTraits answers blue-poisoned targets for Toxicologists before this event, so a blue-poisoned
 * carrier keeps SparkTraits' colour.
 * 病原体增强描边（Q10、Q11）。存活病原体可无视距离穿墙看到其他病原体，并在视线内以深绿看到带毒者；存活毒理学家（含黑羽鸦
 * 扮演覆盖层与验尸官伪装）在视线内以绿色看到带毒者，同时中毒时为紫色。
 * <p>
 * 优先级 {@code PRIORITY_DEFAULT + 40}：高于 NoellesRoles 自身描边（优先级 0：病原体亮绿、毒理学家中毒色），低于 SparkWitch
 * 黑羽鸦队友色（+50）、显式跳过（+100）与教授隐身（300）。SparkWitch 恐惧、SparkStrength 黑警隐蔽等整方法否决仍会隐藏一切。
 * SparkTraits 在本事件之前为毒理学家处理蓝毒目标，因此蓝毒带毒者保持 SparkTraits 的颜色。
 */
public final class PathogenClientHooks {
    public static final int HIGHLIGHT_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 40;
    private static boolean registered;

    private PathogenClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(PathogenClientHooks::highlight);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity entity) {
        if (!(entity instanceof PlayerEntity target)) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer == target || !WatheClient.isPlayerPlayingAndAlive()
                || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            return null;
        }
        boolean carrier = VirusCarrierComponent.isCarrier(target);
        Integer color = null;
        if (PathogenVisibility.isPathogen(viewer)) {
            color = PathogenRules.pathogenViewColor(
                    PathogenVisibility.isPathogen(target), carrier, carrier && viewer.canSee(target));
        } else if (carrier && PathogenVisibility.isToxicologistViewer(viewer)) {
            color = PathogenRules.toxicologistViewColor(
                    true, PlayerPoisonComponent.KEY.get(target).poisonTicks > 0, viewer.canSee(target));
        }
        return color == null ? null : GetInstinctHighlight.HighlightResult.always(color, HIGHLIGHT_PRIORITY);
    }
}
