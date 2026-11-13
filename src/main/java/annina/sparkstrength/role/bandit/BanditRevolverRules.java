package annina.sparkstrength.role.bandit;

import org.jetbrains.annotations.Nullable;

/**
 * Pure numbers for the Bandit's moddable revolver (owner rules 2026-11-12):
 * 强盗改装左轮的纯数值规则（负责人 2026-11-12）：
 * <ul>
 *   <li>The Bandit's shop loses Wathe's revolver; every Bandit starts with one moddable revolver, empty.
 *   / 强盗商店不再出售左轮；每名强盗开局获得一把空的改装左轮。</li>
 *   <li>Base gun: 15 s between shots, the revolver's 30-block reach, 6 chambers, 3 s to load each round.
 *   / 基础：开火冷却 15 秒，射程与左轮相同（30 格），6 发弹巢，每发装填 3 秒。</li>
 *   <li>Long barrel (100): +50% reach and one extra attack, but the trigger must be held 0.2 s.
 *   / 长枪管（100）：射程 +50%，额外造成一次攻击，但须按住右键 0.2 秒才开火。</li>
 *   <li>Short barrel (100): +15% movement speed in hand, -25% shot cooldown, -70% reach.
 *   / 短枪管（100）：手持移速 +15%，开火冷却 -25%，射程 -70%。</li>
 *   <li>Speed grip (50): +25% movement speed in hand. Reload grip (75): -30% shot cooldown (owner: the shot cooldown,
 *   not the loading time). / 移速握把（50）：手持移速 +25%。装填握把（75）：开火冷却 -30%（负责人确认是开火冷却）。</li>
 *   <li>Expanded cylinder (25): 8 chambers, loading 50% faster. / 弹巢扩容（25）：8 发，装填速度 +50%。</li>
 *   <li>PS12 (50) plain round; PS12B (100) armour piercing, one extra attack. One purchase is one round.
 *   / PS12（50）普通弹；PS12B（100）穿甲弹，额外造成一次攻击。每次购买一发。</li>
 *   <li>A dying Bandit drops one plain Wathe revolver on the spot. / 强盗死亡时原地掉落一把普通左轮。</li>
 * </ul>
 * Percentages of the same stat add up (short barrel + reload grip = -55% cooldown; short barrel + speed grip = +40%
 * speed). "One extra attack" is one more {@code killPlayer} call on a target the previous one did not kill, so each
 * spends one protection layer (vest, shield block, psycho armour).
 * 同一属性的百分比相加（短枪管 + 装填握把 = 冷却 -55%；短枪管 + 移速握把 = 移速 +40%）。“额外造成一次攻击”指上一次判定未能
 * 击杀时再调用一次 {@code killPlayer}，每次消耗一层保护（防弹衣、盾牌格挡、疯魔护甲）。
 */
public final class BanditRevolverRules {
    /** Shop entry id prefix of every part and round; the client puts these on the shop's second page. / 配件与子弹商店条目的 id 前缀。 */
    public static final String SHOP_ENTRY_PREFIX = "sparkstrength_bandit_";
    /** Wathe's shop entry the Bandit no longer gets. / 强盗不再拥有的 Wathe 商店条目。 */
    public static final String REMOVED_SHOP_ENTRY_ID = "revolver";

    public static final int BASE_COOLDOWN_TICKS = 15 * 20;
    /** Wathe's revolver ray length ({@code RevolverItem.getGunTarget}). / Wathe 左轮射线长度。 */
    public static final double BASE_RANGE_BLOCKS = 30.0D;
    public static final int BASE_CHAMBERS = 6;
    public static final int EXPANDED_CHAMBERS = 8;
    public static final int BASE_LOAD_TICKS = 3 * 20;
    /** +50% loading speed: 3 s / 1.5. / 装填速度 +50%：3 秒 / 1.5。 */
    public static final int EXPANDED_LOAD_TICKS = 2 * 20;

    public static final double LONG_BARREL_RANGE_MULTIPLIER = 1.5D;
    public static final double SHORT_BARREL_RANGE_MULTIPLIER = 0.3D;
    /** 0.2 s of held trigger. / 按住扳机 0.2 秒。 */
    public static final int LONG_BARREL_HOLD_TICKS = 4;
    public static final int SHORT_BARREL_COOLDOWN_PERCENT = 25;
    public static final int RELOAD_GRIP_COOLDOWN_PERCENT = 30;
    public static final double SHORT_BARREL_SPEED_BONUS = 0.15D;
    public static final double SPEED_GRIP_SPEED_BONUS = 0.25D;

    /** Opening lock, same as Wathe's revolver at round start. / 开局锁，与 Wathe 左轮开局冷却一致。 */
    public static final int OPENING_COOLDOWN_TICKS = 60 * 20;
    public static final int AMMO_MAX_STACK = 16;

    private BanditRevolverRules() {
    }

    public static int chambers(@Nullable BanditAttachment cylinder) {
        return cylinder == BanditAttachment.EXPANDED_CYLINDER ? EXPANDED_CHAMBERS : BASE_CHAMBERS;
    }

    /** Ticks to chamber one round. / 装入一发子弹所需刻数。 */
    public static int loadTicks(@Nullable BanditAttachment cylinder) {
        return cylinder == BanditAttachment.EXPANDED_CYLINDER ? EXPANDED_LOAD_TICKS : BASE_LOAD_TICKS;
    }

    public static int cooldownTicks(@Nullable BanditAttachment barrel, @Nullable BanditAttachment grip) {
        int percent = 100;
        if (barrel == BanditAttachment.SHORT_BARREL) {
            percent -= SHORT_BARREL_COOLDOWN_PERCENT;
        }
        if (grip == BanditAttachment.RELOAD_GRIP) {
            percent -= RELOAD_GRIP_COOLDOWN_PERCENT;
        }
        return BASE_COOLDOWN_TICKS * percent / 100;
    }

    public static double rangeBlocks(@Nullable BanditAttachment barrel) {
        if (barrel == BanditAttachment.LONG_BARREL) {
            return BASE_RANGE_BLOCKS * LONG_BARREL_RANGE_MULTIPLIER;
        }
        if (barrel == BanditAttachment.SHORT_BARREL) {
            return BASE_RANGE_BLOCKS * SHORT_BARREL_RANGE_MULTIPLIER;
        }
        return BASE_RANGE_BLOCKS;
    }

    /** Movement speed bonus while the gun is in the main hand (0.4 = +40%). / 主手持枪时的移速加成（0.4 即 +40%）。 */
    public static double speedBonus(@Nullable BanditAttachment barrel, @Nullable BanditAttachment grip) {
        double bonus = 0.0D;
        if (barrel == BanditAttachment.SHORT_BARREL) {
            bonus += SHORT_BARREL_SPEED_BONUS;
        }
        if (grip == BanditAttachment.SPEED_GRIP) {
            bonus += SPEED_GRIP_SPEED_BONUS;
        }
        return bonus;
    }

    /** Ticks the trigger must be held before the shot leaves; 0 fires on the click. / 开火前须按住扳机的刻数；0 表示点击即开火。 */
    public static int holdTicks(@Nullable BanditAttachment barrel) {
        return barrel == BanditAttachment.LONG_BARREL ? LONG_BARREL_HOLD_TICKS : 0;
    }

    /** Kill attempts of one shot: 1, +1 with the long barrel, +1 for an armour-piercing round. / 一枪的击杀判定次数。 */
    public static int hits(@Nullable BanditAttachment barrel, @Nullable BanditAmmo ammo) {
        int hits = 1;
        if (barrel == BanditAttachment.LONG_BARREL) {
            hits++;
        }
        if (ammo != null) {
            hits += ammo.extraHits();
        }
        return hits;
    }
}
