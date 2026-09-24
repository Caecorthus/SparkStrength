package annina.sparkstrength.tablet;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.role.corruptcop.CorruptCopRules;
import annina.sparkstrength.role.veteran.VeteranRules;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Shop and highlight rules for the SparkStrength tablet item.
 * SparkStrength 平板物品的商店与高亮规则。
 */
public final class TabletShopRules {
    public static final Identifier VIGILANTE_ID = Identifier.of("wathe", "vigilante");
    public static final String TABLET_ENTRY_ID = "sparkstrength_tablet";
    public static final int TABLET_PRICE = 150;
    public static final int TABLET_HIGHLIGHT_COLOR = 0x1B8AE5;
    public static final int SUSPECT_HIGHLIGHT_COLOR = 0xFF8C00;

    private TabletShopRules() {
    }

    public static boolean canBuyTabletRole(@Nullable Role role) {
        if (role == null || VeteranRules.isVeteran(role)) {
            return false;
        }
        Boolean police = SparkFactionCompat.isPoliceRole(role);
        // Old/absent APIs keep exactly the original whitelist, never infer police from gear/faction.
        // 缺失或旧版 API 严格保留原白名单，绝不通过装备或阵营推断警职。
        return police != null ? police : isVigilante(role) || CorruptCopRules.isCorruptCop(role);
    }

    public static boolean isVigilante(@Nullable Role role) {
        return role != null && VIGILANTE_ID.equals(role.identifier());
    }
}
