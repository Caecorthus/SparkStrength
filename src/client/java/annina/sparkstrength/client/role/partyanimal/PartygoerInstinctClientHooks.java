package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.component.corruptcop.CorruptCopAbilityComponent;
import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.role.corruptcop.CorruptCopRules;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import annina.sparkstrength.role.partyanimal.PartygoerVisibilityRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.shadowjester.ShadowJesterPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Instinct x-ray of the Partygoer (派对客), rules in {@link PartygoerVisibilityRules}: killers, spectators and creative
 * players see it in the Party Animal's colour, other instinct-key holders in green, everyone else nothing; always
 * key-gated ({@code withKeybind}) and {@code skip()} while swallowed or invisible.
 * 派对客的本能透视，规则见 PartygoerVisibilityRules：杀手、旁观者与创造模式玩家看到派对狂职业色，其他本能键持有者看到绿色，
 * 其他人看不到；始终需要按键（withKeybind），被吞下或隐身时 skip()。
 *
 * <p>Veto layers stay in force. Wrappers around {@code getInstinctHighlight} (SparkWitch Fear/Obscure/swallow and USEC,
 * SparkStrength Cooling Oil, Corrupt Cop, blackout, Undercover) enclose this answer; {@code skip()} (100) and SparkWitch's
 * suppression (102) outrank {@link PartygoerVisibilityRules#HIGHLIGHT_PRIORITY}; gate vetoes (Fear, Curser confusion,
 * Black Raven Perception, Control Expert, blackout key release) make Wathe drop the keyed result; the Blind's
 * {@code hasOutline} veto is unaffected. The cohort label is player-only, so it never concerns a Partygoer.
 * 否决层全部保留：包住 getInstinctHighlight 的包装器（SparkWitch 恐惧/障眼/吞噬与 USEC、SparkStrength 风油精、黑警、熄灯、
 * 卧底）包住这里的结果；skip()（100）与 SparkWitch 压制（102）高于本监听器优先级；入口否决（恐惧、诅咒混乱、黑羽鸦感知、
 * 控场专家、熄灯松开按键）使 Wathe 丢弃按键结果；盲人的 hasOutline 否决不受影响。同伙标签只针对玩家，与派对客无关。</p>
 *
 * <p>"Has an instinct key right now" has no single public answer, so three sources are combined (decision 2026-10-21):
 * Wathe's native gate {@code isInstinctEnabledAndIsKiller()} as add-ons extend it (Conscience killers, promoted Wind
 * Spirit, Saboteur and Curser; true only while the key is held, which is all a keyed outline needs), SparkFactionAPI's
 * per-faction {@code canUseInstinct} (witch faction, Murderous Witch), and the role keys neither reports: SparkWitch
 * Insider, Corrupt Cop after the task unlock, NoellesRoles Shadow Jester with instinct vision and Vulture. Perfumer, Pig
 * God, Apprentice Witch, Fisher and the like only have keyless skill outlines, so they get nothing.
 * 「此刻是否拥有本能键」没有统一的公开答案，因此合并三种来源（2026-10-21 决定）：被附属模组扩展后的 Wathe 原生入口
 * isInstinctEnabledAndIsKiller()（善良杀手、晋升的风精灵、破坏者与诅咒者；仅在按住按键时为 true，按键描边也只需要这一点）、
 * SparkFactionAPI 的阵营 canUseInstinct（魔女阵营、杀意魔女），以及二者都不报告的职业按键：SparkWitch 内应、任务解锁后的黑警、
 * 拥有本能视野的 NoellesRoles 影小丑与秃鹫。调香师、猪神、预备魔女、渔夫等只有免按键的技能描边，因此看不到。</p>
 */
public final class PartygoerInstinctClientHooks {
    private static final String BLACK_RAVEN_HOOKS = "dev.caecorthus.sparkwitch.client.blackraven.BlackRavenInstinctClientHooks";
    private static boolean registered;
    private static @Nullable Method blackRavenSensed;
    private static boolean blackRavenResolved;

    private PartygoerInstinctClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(PartygoerInstinctClientHooks::highlight);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!(target instanceof PartygoerEntity partygoer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return null;
        }
        if (PartygoerVisibilityRules.hidden(partygoer.partyState() == PartygoerState.SWALLOWED, partygoer.isInvisible())) {
            return GetInstinctHighlight.HighlightResult.skip();
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        Role role = game.getRole(viewer);
        PartygoerVisibilityRules.Viewer kind = PartygoerVisibilityRules.viewer(
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                SparkTraitsCompat.isEffectiveKiller(role, viewer),
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                WatheClient.isInstinctEnabledAndIsKiller(),
                SparkFactionCompat.canUseInstinct(viewer, game),
                hasRoleInstinctKey(viewer, game, role));
        int color = PartygoerVisibilityRules.color(kind, Noellesroles.PARTY_ANIMAL.color());
        return color == -1 ? null
                : GetInstinctHighlight.HighlightResult.withKeybind(color, PartygoerVisibilityRules.HIGHLIGHT_PRIORITY);
    }

    /**
     * Instinct keys that neither Wathe's native gate nor a faction capability reports (each mirrors its owner's own
     * keyed outline: SparkWitch {@code InsiderInstinctClientHooks}, {@code CorruptCopClientHooks}, NoellesRoles' Shadow
     * Jester and Vulture listeners).
     * Wathe 原生入口与阵营能力都不报告的本能键（各自对应原模组的按键描边：SparkWitch 内应、黑警、NoellesRoles 影小丑与秃鹫）。
     */
    private static boolean hasRoleInstinctKey(ClientPlayerEntity viewer, GameWorldComponent game, @Nullable Role role) {
        if (CorruptCopRules.isInsider(role)) {
            return true;
        }
        if (CorruptCopRules.isCorruptCop(role)) {
            return CorruptCopAbilityComponent.KEY.get(viewer).isUnlocked();
        }
        if (game.isRole(viewer, Noellesroles.SHADOW_JESTER)) {
            return ShadowJesterPlayerComponent.KEY.get(viewer).hasInstinctVision();
        }
        return game.isRole(viewer, Noellesroles.VULTURE);
    }

    /**
     * HEAD answer for a Conscience viewer, called by {@code PartygoerConscienceInstinctMixin}. SparkTraits' priority-1000
     * HEAD returns -1 for every non-player when the viewer has Conscience, before Wathe's event runs, so this answers
     * first with exactly what Wathe would make of the event (skip, then the key check, then the colour), after the same
     * Black Raven sensed-instinct check SparkTraits makes before its Conscience branch. Null leaves the method alone.
     * 善良观察者的 HEAD 答复，由 PartygoerConscienceInstinctMixin 调用。观察者有善良词条时，SparkTraits 优先级 1000 的 HEAD 会在
     * Wathe 事件之前对所有非玩家返回 -1，因此这里先行作答，结果与 Wathe 处理事件完全一致（先 skip，再查按键，再取颜色），
     * 并先做 SparkTraits 在善良分支之前同样会做的黑羽鸦感知判定。返回 null 表示不干预。
     */
    public static @Nullable Integer beforeConscience(Entity target) {
        if (!(target instanceof PartygoerEntity)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !SparkTraitsCompat.hasConscience(viewer)) {
            return null;
        }
        Integer blackRaven = blackRavenSensedHighlight(target);
        if (blackRaven != null) {
            return blackRaven;
        }
        GetInstinctHighlight.HighlightResult result = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        if (result == null) {
            return null;
        }
        if (result.isSkip() || (result.requiresKeybind() && !WatheClient.isInstinctEnabled())) {
            return -1;
        }
        return result.color();
    }

    /** SparkWitch's Black Raven sensed-mode answer, or null (absent, or not deciding). / 黑羽鸦感知模式的结果，或 null。 */
    private static @Nullable Integer blackRavenSensedHighlight(Entity target) {
        if (!blackRavenResolved) {
            blackRavenResolved = true;
            blackRavenSensed = findBlackRavenSensed();
        }
        if (blackRavenSensed == null) {
            return null;
        }
        try {
            return blackRavenSensed.invoke(null, target) instanceof Integer color ? color : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable Method findBlackRavenSensed() {
        if (!FabricLoader.getInstance().isModLoaded("sparkwitch")) {
            return null;
        }
        try {
            Method method = Class.forName(BLACK_RAVEN_HOOKS, false, PartygoerInstinctClientHooks.class.getClassLoader())
                    .getMethod("resolvePrioritySensedHighlight", Entity.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Integer.class ? method : null;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }
}
