package annina.sparkstrength.role.bandit;

import org.jetbrains.annotations.Nullable;

/**
 * 12.7x55 rounds for the Bandit's revolver (owner 2026-11-12): PS12 is the plain round, PS12B the armour-piercing one
 * that lands one extra attack. One purchase is one round. {@link #code()} is the stable save/network value.
 * 强盗改装左轮用的 12.7x55 子弹（负责人 2026-11-12）：PS12 为普通弹，PS12B 为穿甲弹，额外造成一次攻击。每次购买一发。
 * {@link #code()} 是稳定的存档/网络取值。
 */
public enum BanditAmmo {
    PS12(1, "ps12", 50, 0),
    PS12B(2, "ps12b", 100, 1);

    private final int code;
    private final String key;
    private final int price;
    private final int extraHits;

    BanditAmmo(int code, String key, int price, int extraHits) {
        this.code = code;
        this.key = key;
        this.price = price;
        this.extraHits = extraHits;
    }

    public int code() {
        return code;
    }

    public String key() {
        return key;
    }

    public int price() {
        return price;
    }

    /** Extra kill attempts after the first. / 首次之后额外的击杀判定次数。 */
    public int extraHits() {
        return extraHits;
    }

    public String shopEntryId() {
        return BanditRevolverRules.SHOP_ENTRY_PREFIX + key;
    }

    public static @Nullable BanditAmmo byCode(int code) {
        for (BanditAmmo ammo : values()) {
            if (ammo.code == code) {
                return ammo;
            }
        }
        return null;
    }
}
