package annina.sparkstrength.role.bartender;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure tuning for the SparkStrength Bartender buff: longer drink effects, a lighter vodka crash, cheaper
 * shop prices, the two SparkStrength spices and the two signature cocktails they name.
 * Ingredients are matched by the string ids NoellesRoles stores in a base spirit's NBT.
 * SparkStrength 酒保增强的纯数值：更长的酒效、更轻的伏特加副作用、更低的商店价格、两种新调料及其命名的两款特调。
 * 调剂按 NoellesRoles 写入基酒 NBT 的字符串 id 匹配。
 */
public final class BartenderRules {
    public static final String RUM_ID = "rum";
    public static final String GIN_ID = "gin";
    public static final String VODKA_ID = "vodka";
    public static final String TEQUILA_ID = "tequila";
    public static final String WHISKEY_ID = "whiskey";
    public static final String ICE_CUBE_ID = "ice_cube";
    public static final String SPECIAL_LIQUEUR_ID = "special_liqueur";
    public static final String SPECIAL_SPICE_ID = "special_spice";
    /** Spice A: drinking it clears every item cooldown in the drinker's inventory. / 调料 A：喝下后清空饮用者背包内所有物品冷却。 */
    public static final String GHOSTFLAME_BITTERS_ID = "ghostflame_bitters";
    /** Spice B: the drink is downed instantly. / 调料 B：这杯酒瞬间喝完。 */
    public static final String EMBER_SUGAR_ID = "ember_sugar";

    public static final String GHOSTFLAME_BITTERS_ENTRY_ID = "sparkstrength_ghostflame_bitters";
    public static final String EMBER_SUGAR_ENTRY_ID = "sparkstrength_ember_sugar";

    public static final int BASE_SPIRIT_PRICE = 40;
    public static final int GHOSTFLAME_BITTERS_PRICE = 100;
    public static final int EMBER_SUGAR_PRICE = 50;
    private static final Map<String, Integer> INGREDIENT_PRICES = Map.of(
            RUM_ID, 60,
            GIN_ID, 60,
            VODKA_ID, 100,
            TEQUILA_ID, 30,
            WHISKEY_ID, 100,
            ICE_CUBE_ID, 10,
            SPECIAL_LIQUEUR_ID, 75,
            SPECIAL_SPICE_ID, 10,
            GHOSTFLAME_BITTERS_ID, GHOSTFLAME_BITTERS_PRICE,
            EMBER_SUGAR_ID, EMBER_SUGAR_PRICE
    );

    /**
     * Effect length before the special-liqueur multiplier, keyed by ingredient. Upstream: rum 7 s, gin 10 s,
     * vodka 15 s, tequila 7 s, whiskey 20 s.
     * 未乘特调利口酒倍率的效果时长（按调剂）。上游：朗姆 7 秒、金酒 10 秒、伏特加 15 秒、龙舌兰 7 秒、威士忌 20 秒。
     */
    private static final Map<String, Integer> EFFECT_TICKS = Map.of(
            RUM_ID, 10 * 20,
            GIN_ID, 20 * 20,
            VODKA_ID, 20 * 20,
            TEQUILA_ID, 12 * 20,
            WHISKEY_ID, 30 * 20
    );

    /**
     * Vodka's on-drink cut: every running cooldown keeps this fraction (40% off). Upstream's text says 20% off, but
     * it writes the cut back while Stimulation is already active, so its "new cooldowns x0.8" rule hits the write-back
     * too and the real upstream cut is 36%.
     * 伏特加生效瞬间：所有冷却保留此比例（减 40%）。上游文案写减 20%，但它在亢奋已生效时写回冷却，“新冷却 ×0.8”
     * 会再作用一次，上游实际为减 36%。
     */
    public static final float VODKA_INSTANT_COOLDOWN_KEEP = 0.6F;
    /** Upstream StimulationCooldownMixin: cooldowns set during Stimulation are scaled by this. / 上游亢奋期间新设冷却的倍率。 */
    public static final float UPSTREAM_STIMULATION_COOLDOWN_FACTOR = 0.8F;
    /** Vodka's crash keeps stamina 0 + exhaustion but no longer adds Slowness II. / 伏特加结束：保留体力归零与疲劳，不再附加缓慢 II。 */
    public static final boolean VODKA_CRASH_SLOWNESS = false;

    /** Upstream CocktailItem drinks in 40 ticks; 1 tick finishes on the next tick (0 would never finish). / 上游 40 刻喝完；1 刻即下一刻喝完（0 永远喝不完）。 */
    public static final int INSTANT_DRINK_USE_TICKS = 1;

    public static final String CROWNED_DEATH_KEY = "cocktail.sparkstrength.crowned_death";
    public static final String CROWNED_BLOOM_KEY = "cocktail.sparkstrength.crowned_bloom";
    public static final String LIFE_AND_DEATH_KEY = "cocktail.sparkstrength.life_and_death";
    /** Each signature name is translated in this many coloured segments (`<key>.0` … `<key>.3`). / 每款特调名按此数量的着色片段翻译。 */
    public static final int SIGNATURE_NAME_SEGMENTS = 4;
    /** Necrass: cold violet flame to silver-white hair. / 死芒：冷紫焰到银白发。 */
    public static final int NECRASS_FLAME_RGB = 0x8B6CF0;
    public static final int NECRASS_SILVER_RGB = 0xD6D0FF;
    /** Reed the Flame Shadow: flame orange to gold. / 焰影苇草：焰橙到金。 */
    public static final int REED_FLAME_RGB = 0xF07A2E;
    public static final int REED_GOLD_RGB = 0xFFD36A;

    public static final int GHOSTFLAME_BITTERS_RGB = 0x9B7CFF;
    public static final int EMBER_SUGAR_RGB = 0xF2A23A;

    /** Upstream ingredients whose effect text changes with these numbers. / 效果说明随本类数值改变的上游调剂。 */
    private static final Set<String> RETUNED_EFFECT_TEXT = Set.of(
            RUM_ID, GIN_ID, VODKA_ID, TEQUILA_ID, WHISKEY_ID, SPECIAL_LIQUEUR_ID);

    public enum Signature {
        /** Necrass gradient. / 死芒渐变。 */
        CROWNED_DEATH(CROWNED_DEATH_KEY, gradient(NECRASS_FLAME_RGB, NECRASS_SILVER_RGB)),
        /** Reed the Flame Shadow gradient. / 焰影苇草渐变。 */
        CROWNED_BLOOM(CROWNED_BLOOM_KEY, gradient(REED_FLAME_RGB, REED_GOLD_RGB)),
        /** The twins side by side: 生 Reed flame, 死 Necrass flame, 相 Reed gold, 依 Necrass silver. / 双子交替：生焰橙、死冷紫、相金、依银白。 */
        LIFE_AND_DEATH(LIFE_AND_DEATH_KEY, new int[] {REED_FLAME_RGB, NECRASS_FLAME_RGB, REED_GOLD_RGB, NECRASS_SILVER_RGB});

        private final String translationKey;
        private final int[] segmentRgb;

        Signature(String translationKey, int[] segmentRgb) {
            this.translationKey = translationKey;
            this.segmentRgb = segmentRgb;
        }

        public String translationKey() {
            return translationKey;
        }

        /** First segment's colour, used where upstream wants one colour for the whole name. / 首段颜色，用于上游只取单色的场合。 */
        public int mainRgb() {
            return segmentRgb[0];
        }

        public int segmentRgb(int index) {
            return segmentRgb[index];
        }

        private static int[] gradient(int fromRgb, int toRgb) {
            int[] colors = new int[SIGNATURE_NAME_SEGMENTS];
            for (int i = 0; i < SIGNATURE_NAME_SEGMENTS; i++) {
                colors[i] = blend(fromRgb, toRgb, i / (float) (SIGNATURE_NAME_SEGMENTS - 1));
            }
            return colors;
        }
    }

    private BartenderRules() {
    }

    /** Shop price for an ingredient id, or -1 when the Bartender buff does not price it. / 调剂价格；未定价返回 -1。 */
    public static int ingredientPrice(String ingredientId) {
        Integer price = INGREDIENT_PRICES.get(ingredientId);
        return price == null ? -1 : price;
    }

    /** Effect ticks replacing upstream's base duration, or {@code upstreamTicks} for unknown ids. / 替换上游基础时长的刻数。 */
    public static float effectTicks(String ingredientId, float upstreamTicks) {
        Integer ticks = EFFECT_TICKS.get(ingredientId);
        return ticks == null ? upstreamTicks : ticks;
    }

    /**
     * The spices name the glass outright, whatever else is in it: spice A makes it 冠死以冕, spice B 冠生以花, and both
     * together 生死相依.
     * 调料直接决定酒名，与其他调剂无关：含调料 A 为冠死以冕，含调料 B 为冠生以花，两者同杯为生死相依。
     */
    public static @Nullable Signature signatureOf(List<String> ingredients) {
        boolean bitters = ingredients.contains(GHOSTFLAME_BITTERS_ID);
        boolean sugar = ingredients.contains(EMBER_SUGAR_ID);
        if (bitters && sugar) {
            return Signature.LIFE_AND_DEATH;
        }
        if (bitters) {
            return Signature.CROWNED_DEATH;
        }
        return sugar ? Signature.CROWNED_BLOOM : null;
    }

    /**
     * Factor handed to upstream's reduceAllCooldowns. Its write-back is scaled again by Stimulation, so this divides
     * that out to land exactly on {@link #VODKA_INSTANT_COOLDOWN_KEEP}.
     * 交给上游 reduceAllCooldowns 的倍率。写回时会再被亢奋缩放一次，这里先除掉，使结果恰为 VODKA_INSTANT_COOLDOWN_KEEP。
     */
    public static float vodkaInstantCutFactor() {
        return VODKA_INSTANT_COOLDOWN_KEEP / UPSTREAM_STIMULATION_COOLDOWN_FACTOR;
    }

    public static boolean drinksInstantly(List<String> ingredients) {
        return ingredients.contains(EMBER_SUGAR_ID);
    }

    /**
     * Points upstream's effect line at SparkStrength's retuned text ({@code item.noellesroles.<id>.effect} to
     * {@code item.sparkstrength.bartender.<id>.effect}); other keys pass through. Done in code so it never depends
     * on which mod's language file loads last.
     * 把上游效果说明指向 SparkStrength 的新文案；其他 key 原样返回。在代码里改写，不依赖语言文件的加载顺序。
     */
    public static String effectTextKey(String key) {
        String prefix = "item.noellesroles.";
        String suffix = ".effect";
        if (key.startsWith(prefix) && key.endsWith(suffix)) {
            String id = key.substring(prefix.length(), key.length() - suffix.length());
            if (RETUNED_EFFECT_TEXT.contains(id)) {
                return "item.sparkstrength.bartender." + id + suffix;
            }
        }
        return key;
    }

    static int blend(int fromRgb, int toRgb, float t) {
        int r = Math.round(((fromRgb >> 16) & 0xFF) + (((toRgb >> 16) & 0xFF) - ((fromRgb >> 16) & 0xFF)) * t);
        int g = Math.round(((fromRgb >> 8) & 0xFF) + (((toRgb >> 8) & 0xFF) - ((fromRgb >> 8) & 0xFF)) * t);
        int b = Math.round((fromRgb & 0xFF) + ((toRgb & 0xFF) - (fromRgb & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
