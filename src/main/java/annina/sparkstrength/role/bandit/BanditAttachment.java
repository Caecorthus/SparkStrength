package annina.sparkstrength.role.bandit;

import org.jetbrains.annotations.Nullable;

/**
 * Parts sold in the Bandit's shop (owner rules 2026-11-12, see {@link BanditRevolverRules}). {@link #code()} is the
 * stable save/network value; 0 means "nothing fitted".
 * 强盗商店出售的配件（负责人 2026-11-12 的规则，见 {@link BanditRevolverRules}）。{@link #code()} 是稳定的存档/网络取值，
 * 0 表示该部位没有配件。
 */
public enum BanditAttachment {
    LONG_BARREL(1, BanditAttachmentSlot.BARREL, "long_barrel", 100),
    SHORT_BARREL(2, BanditAttachmentSlot.BARREL, "short_barrel", 100),
    SPEED_GRIP(3, BanditAttachmentSlot.GRIP, "speed_grip", 50),
    RELOAD_GRIP(4, BanditAttachmentSlot.GRIP, "reload_grip", 75),
    EXPANDED_CYLINDER(5, BanditAttachmentSlot.CYLINDER, "expanded_cylinder", 25);

    public static final int NONE = 0;

    private final int code;
    private final BanditAttachmentSlot slot;
    private final String key;
    private final int price;

    BanditAttachment(int code, BanditAttachmentSlot slot, String key, int price) {
        this.code = code;
        this.slot = slot;
        this.key = key;
        this.price = price;
    }

    public int code() {
        return code;
    }

    public BanditAttachmentSlot slot() {
        return slot;
    }

    /** Item path suffix and lang fragment. / 物品路径后缀与语言键片段。 */
    public String key() {
        return key;
    }

    public int price() {
        return price;
    }

    /** Shop entry id; the client's shop page split keys on {@link BanditRevolverRules#SHOP_ENTRY_PREFIX}. / 商店条目 id。 */
    public String shopEntryId() {
        return BanditRevolverRules.SHOP_ENTRY_PREFIX + key;
    }

    /** The part with this code, or null for 0 and unknown codes. / 该取值对应的配件；0 或未知取值返回 null。 */
    public static @Nullable BanditAttachment byCode(int code) {
        for (BanditAttachment attachment : values()) {
            if (attachment.code == code) {
                return attachment;
            }
        }
        return null;
    }
}
