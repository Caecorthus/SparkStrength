package annina.sparkstrength.role.morphling;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Rules for a NoellesRoles Morphling in corpse mode ({@code MorphlingPlayerComponent.corpseMode}), owner decision
 * 2026-11-03: it must pass for a real corpse.
 * <ul>
 *   <li>Instinct: Wathe never outlines a real corpse for a living viewer, so the keyed instinct x-ray skips a
 *   corpse-mode Morphling for every viewer except spectators, creative players and active Wraiths (the viewers
 *   blackout sight exempts too). Outlines that need no key stay. This replaces the old SparkTraits rule that hid only a
 *   Conscience Morphling.</li>
 *   <li>Look: the client draws a Wathe corpse in its place that only slides with the player ("尸体平移"): facing frozen
 *   when it lies down, no walking, sneaking, arm swing or held items. It lies down at once, like NoellesRoles did: Wathe
 *   tips a corpse over around its feet, which sit a block behind the player, so the fall would start with a standing
 *   body a block back. Moving the corpse forward instead would move the player's hitbox from its torso to its feet.</li>
 * </ul>
 * NoellesRoles 变形者尸体模式（{@code MorphlingPlayerComponent.corpseMode}）的规则，所有者 2026-11-03 决定：它必须像一具真尸体。
 * 本能：Wathe 从不为存活观察者描出真尸体，因此除旁观者、创造模式玩家与激活中的冤魂外（熄灯视野同样放行这些观察者），按键
 * 本能透视对所有观察者跳过尸体模式的变形者；无需按键的描边保留。它取代了 SparkTraits 旧有的仅隐藏善良变形者的规则。
 * 外观：客户端在其位置画一具 Wathe 尸体，只随玩家平移（“尸体平移”）：朝向在躺下时固定，不迈腿、不下蹲、不摆手、不显示
 * 手持物品。与 NoellesRoles 原来一样立刻躺平：Wathe 尸体以脚为轴倒下，而脚位于玩家身后一格，播放倒地会先在身后一格出现
 * 一个站着的身体；若把尸体前移，玩家碰撞箱又会从躯干移到脚边。
 */
public final class MorphlingCorpseRules {
    /**
     * Age a stand-in starts at: the end of the 20-tick fall in Wathe's corpse renderer, so it lies flat at once; it
     * decays like a real corpse from there.
     * 替身的起始年龄：Wathe 尸体渲染器 20 tick 倒地动画的终点，因此立刻躺平；之后与真尸体一样腐烂。
     */
    public static final int START_AGE = 20;

    private MorphlingCorpseRules() {
    }

    /**
     * Whether the viewer's keyed instinct must skip this target.
     * 观察者的按键本能是否必须跳过该目标。
     */
    public static boolean hidesFromKeyedInstinct(boolean targetCorpseMode, boolean targetIsViewer, boolean viewerExempt) {
        return targetCorpseMode && !targetIsViewer && !viewerExempt;
    }

    /**
     * Viewers who see what spectators see. / 能看到旁观信息的观察者。
     */
    public static boolean exemptViewer(boolean viewerPlayingAndAlive, boolean viewerSpectatingOrCreative,
                                       boolean viewerWraithActive) {
        return !viewerPlayingAndAlive || viewerSpectatingOrCreative || viewerWraithActive;
    }

    /**
     * Whether a player is drawn as a corpse. A spectator is never drawn as one.
     * 玩家是否画成尸体。旁观者从不画成尸体。
     */
    public static boolean drawnAsCorpse(boolean corpseMode, boolean spectator) {
        return corpseMode && !spectator;
    }

    /**
     * Whose corpse it looks like, in the order the standing player's skin is chosen: NoellesRoles' own morph, then the
     * SparkStrength reagent, then the Coroner disguise, then the player.
     * 尸体长成谁的样子，与站立玩家选择皮肤的顺序相同：NoellesRoles 原变形，然后 SparkStrength 试剂，然后验尸官伪装，最后是玩家本人。
     */
    public static UUID appearanceUuid(UUID self, boolean morphing, @Nullable UUID morphDisguise,
                                      boolean reagentActive, @Nullable UUID reagentSample,
                                      @Nullable UUID coronerDisguise) {
        if (morphing && morphDisguise != null) {
            return morphDisguise;
        }
        if (reagentActive && reagentSample != null) {
            return reagentSample;
        }
        return coronerDisguise != null ? coronerDisguise : self;
    }
}
