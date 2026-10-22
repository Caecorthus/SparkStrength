package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.sabotage.BlackoutSightRules;

/**
 * Who sees the Partygoer (派对客) through walls with the instinct key (本能透视). Owner rules, 2026-10-21:
 * 谁能用本能键穿墙看到派对客。主人 2026-10-21 定下的规则：
 * <ul>
 *   <li>Effective killers (SparkTraits Impostor civilians included), spectators and creative players see it in the
 *   Party Animal's role colour. / 有效杀手（含 SparkTraits 内鬼好人）、旁观者与创造模式玩家看到派对狂的职业色。</li>
 *   <li>Every other viewer who has an instinct key right now sees it in Wathe's civilian green; SparkTraits Conscience
 *   killers count here. / 其他此刻拥有本能键的观察者看到 Wathe 的平民绿色；SparkTraits 善良杀手也算在这里。</li>
 *   <li>A viewer without an instinct key (a plain civilian) sees nothing: the Partygoer adds no new x-ray.
 *   / 没有本能键的观察者（普通好人）什么都看不到：派对客不会带来新的透视。</li>
 *   <li>Always key-gated, like ordinary instinct; hidden from everyone while swallowed by a Taotie or invisible.
 *   / 与普通本能一样始终需要按键；被饕餮吞下或隐身时对所有人隐藏。</li>
 *   <li>During a blackout the Partygoer counts as its owner, a killer ({@link #blackoutMayReveal}).
 *   / 熄灯期间派对客按其召唤者（杀手）处理（见 blackoutMayReveal）。</li>
 * </ul>
 * Pure, free of Minecraft types so local tests compile it alone.
 * 纯规则，不依赖 Minecraft 类型，便于本地测试单独编译。
 */
public final class PartygoerVisibilityRules {
    /** How a viewer relates to the Partygoer. / 观察者与派对客的关系。 */
    public enum Viewer {
        /** Effective killer, Impostor included. / 有效杀手（含内鬼）。 */
        KILLER,
        /** Spectator or creative player. / 旁观者或创造模式玩家。 */
        SPECTATOR,
        /** Any other viewer holding an instinct key. / 其他拥有本能键的观察者。 */
        KEY_HOLDER,
        /** No instinct key: no outline. / 没有本能键：无描边。 */
        NONE
    }

    /** Wathe's civilian instinct green. / Wathe 的平民本能绿色。 */
    public static final int KEY_HOLDER_COLOR = 0x4EDD35;
    /**
     * Event priority: below {@code skip()} (100) and SparkWitch's Fear/Obscure suppression (102), so both still hide
     * it, and above Wathe's default 0.
     * 事件优先级：低于 skip()（100）与 SparkWitch 恐惧/障眼压制（102），二者仍能隐藏它；高于 Wathe 默认的 0。
     */
    public static final int HIGHLIGHT_PRIORITY = 80;

    private PartygoerVisibilityRules() {
    }

    /**
     * Classifies the viewer. The three instinct-key sources are alternatives: Wathe's native killer gate as add-ons
     * extend it (true only while the key is held), the viewer's SparkFactionAPI faction capability and role-specific
     * keys; the last two count only for a living viewer.
     * 给观察者分类。三种本能键来源任一成立即可：被附属模组扩展后的 Wathe 原生杀手入口（仅在按住按键时为 true）、
     * 观察者 SparkFactionAPI 阵营能力与职业专属按键；后两者只对存活观察者有效。
     *
     * @param nativeInstinctGate {@code WatheClient.isInstinctEnabledAndIsKiller()} / Wathe 原生本能入口
     * @param factionInstinct    the effective faction declares {@code canUseInstinct} / 有效阵营声明了 canUseInstinct
     * @param roleInstinct       a role-specific instinct key (Insider, unlocked Corrupt Cop, ...) / 职业专属本能键
     */
    public static Viewer viewer(boolean spectatingOrCreative, boolean effectiveKiller, boolean alive,
                                boolean nativeInstinctGate, boolean factionInstinct, boolean roleInstinct) {
        if (effectiveKiller) {
            return Viewer.KILLER;
        }
        if (spectatingOrCreative) {
            return Viewer.SPECTATOR;
        }
        if (nativeInstinctGate || (alive && (factionInstinct || roleInstinct))) {
            return Viewer.KEY_HOLDER;
        }
        return Viewer.NONE;
    }

    /**
     * Colour of the key-gated outline, or -1 for none.
     * 按键描边的颜色；不描边时返回 -1。
     *
     * @param partyAnimalColor the Party Animal role colour (ARGB is fine) / 派对狂职业色（可带 alpha）
     */
    public static int color(Viewer viewer, int partyAnimalColor) {
        return switch (viewer) {
            case KILLER, SPECTATOR -> partyAnimalColor & 0xFFFFFF;
            case KEY_HOLDER -> KEY_HOLDER_COLOR;
            case NONE -> -1;
        };
    }

    /** Inside a Taotie, or invisible: hidden from everyone. / 在饕餮肚里或隐身：对所有人隐藏。 */
    public static boolean hidden(boolean swallowed, boolean invisible) {
        return swallowed || invisible;
    }

    /**
     * Whether the key may still reveal the Partygoer, which counts as its owner: one's own Partygoer always stays,
     * otherwise {@link BlackoutSightRules#keyMayReveal} with a killer target (killers: only a tablet-linked owner;
     * witch faction, Corrupt Cop, Insider and lone viewers: never; exempt viewers: untouched).
     * 本能键是否仍可透视派对客；派对客按其召唤者处理：自己的派对客始终可见，否则按杀手目标套用 keyMayReveal（杀手：只有
     * 召唤者已在平板上互认时可见；魔女阵营、黑警、内应与单独行动者：永不可见；豁免观察者：不受影响）。
     *
     * @param ownPartygoer the viewer summoned it / 观察者就是召唤者
     * @param ownerLinked  the owner is among the viewer's tablet-linked partners / 召唤者在观察者的平板互认对象中
     */
    public static boolean blackoutMayReveal(boolean blackout, boolean viewerExempt, BlackoutSightRules.Team viewer,
                                            boolean ownPartygoer, boolean ownerLinked) {
        return ownPartygoer || BlackoutSightRules.keyMayReveal(blackout, viewerExempt, viewer,
                BlackoutSightRules.Team.KILLER, ownerLinked);
    }

    /**
     * Who may learn the owner's UUID (only needed for the blackout rule): killers and spectators/creative players.
     * 谁可以得知召唤者 UUID（只有熄灯规则需要）：杀手与旁观者/创造模式玩家。
     */
    public static boolean receivesOwner(boolean effectiveKiller, boolean spectatingOrCreative) {
        return effectiveKiller || spectatingOrCreative;
    }
}
