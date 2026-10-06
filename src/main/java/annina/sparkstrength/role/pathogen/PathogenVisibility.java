package annina.sparkstrength.role.pathogen;

import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/**
 * Who may see carriers, on both sides. Pathogens are read from the real role ({@code getRole}). Toxicologists go through
 * {@code isRole} on purpose, so SparkWitch's Black Raven acting overlay gets the same view. A Coroner disguised as a
 * Toxicologist does too, like its other Toxicologist copies.
 * 双端共用：谁可以看到带毒者。病原体读取真实身份（{@code getRole}）；毒理学家刻意经由 {@code isRole} 判定，
 * 使 SparkWitch 黑羽鸦的扮演覆盖层获得相同视角；伪装成毒理学家的验尸官也同样可见，与其他毒理学家复刻一致。
 */
public final class PathogenVisibility {
    private PathogenVisibility() {
    }

    public static boolean isPathogen(@Nullable PlayerEntity player) {
        return player != null
                && PathogenRules.isPathogen(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean isToxicologistViewer(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.TOXICOLOGIST)
                || CoronerService.hasToxicologistDisguise(player);
    }

    public static boolean seesCarriers(@Nullable PlayerEntity player) {
        return isPathogen(player) || isToxicologistViewer(player);
    }
}
