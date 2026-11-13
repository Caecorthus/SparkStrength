package annina.sparkstrength.client.screen.assassin;

import annina.sparkstrength.client.mixin.assassin.AssassinScreenInvoker;
import annina.sparkstrength.client.mixin.assassin.ScreenInitInvoker;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;
import org.agmas.noellesroles.client.AssassinTargetWidget;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.screen.AssassinScreen;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where the guess screen gets its targets and roles: from NoellesRoles' own {@code AssassinScreen}, never a copy of its
 * rules. SparkWitch and SparkTraits mix into that screen (the bound Vendetta target, the hidden Savior, the guessable
 * Saboteur); asking the native screen keeps all of that working without those mods knowing about this one.
 * 猜测界面的目标与身份来源：直接取自 NoellesRoles 自己的 AssassinScreen，而不是复制它的规则。SparkWitch 与 SparkTraits 都对
 * 该界面打了 mixin（复仇绑定目标、隐藏救世主、可猜破坏者）；向原生界面取数能让这些功能照常工作，而那些模组无需知道本界面。
 */
public final class AssassinGuessSources {
    private static boolean abilityKeyDown;

    private AssassinGuessSources() {
    }

    /** NoellesRoles' own conditions for opening the menu. / NoellesRoles 打开菜单的原条件。 */
    public static boolean canOpen(@Nullable ClientPlayerEntity player) {
        return player != null
                && GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.ASSASSIN)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !SwallowedPlayerComponent.isPlayerSwallowed(player);
    }

    /**
     * END_CLIENT_TICK. NoellesRoles only opens its menu while a guess is available; a press of its ability key that it
     * ignores (cooldown, or no guesses left) opens ours instead, so the pencilled marks can be read at any time. The
     * Guess button stays off for the same reason NoellesRoles kept the menu shut. Reads the held state, never
     * {@code wasPressed()}, so no press is taken from NoellesRoles.
     * END_CLIENT_TICK。NoellesRoles 只在可以猜测时才打开菜单；它忽略的技能键按下（冷却中或次数用完）改为打开我们的界面，
     * 这样随时都能查看铅笔标记。“确认猜测”按钮因同样的原因保持不可用。只读取按住状态，从不调用 wasPressed()，不会抢走
     * NoellesRoles 的按键。
     */
    public static void tickAbilityKey(MinecraftClient client) {
        KeyBinding ability = NoellesrolesClient.abilityBind;
        boolean down = ability != null && ability.isPressed();
        boolean pressedNow = down && !abilityKeyDown;
        abilityKeyDown = down;
        if (!pressedNow || client.currentScreen != null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (canOpen(player) && !AssassinPlayerComponent.KEY.get(player).canGuess()) {
            client.setScreen(new AssassinGuessScreen(player));
        }
    }

    /** The screen to show instead of {@code screen}: ours for NoellesRoles' native menu, otherwise the same one.
     *  应显示的界面：NoellesRoles 原生菜单换成我们的，其余原样返回。 */
    public static @Nullable Screen replaceNativeScreen(@Nullable Screen screen) {
        if (screen == null || screen.getClass() != AssassinScreen.class) {
            return screen;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return canOpen(player) ? new AssassinGuessScreen(player) : screen;
    }

    /**
     * The players NoellesRoles' target phase offers right now, in its order. A throwaway native screen builds its
     * widgets (it is never shown, and never narrated: see {@link ScreenInitInvoker}) and the target widgets are read
     * back, so mixins into its {@code init()} apply. Empty unless {@link #canOpen} holds: the native screen would
     * close the current screen otherwise.
     * NoellesRoles 选目标阶段此刻提供的玩家，顺序与其一致。让一个一次性的原生界面生成控件（从不显示，也不触发旁白，见
     * ScreenInitInvoker），再读回其中的目标控件，使针对其 init() 的 mixin 生效。canOpen 不成立时返回空：否则原生界面会关闭当前界面。
     */
    public static List<UUID> targets(MinecraftClient client, ClientPlayerEntity player, int width, int height) {
        List<UUID> targets = new ArrayList<>();
        if (!canOpen(player)) {
            return targets;
        }
        AssassinScreen probe = new AssassinScreen(player);
        probe.width = width;
        probe.height = height;
        ScreenInitInvoker probeInit = (ScreenInitInvoker) probe;
        probeInit.sparkstrength$setClient(client);
        probeInit.sparkstrength$init();
        for (Element child : probe.children()) {
            if (child instanceof AssassinTargetWidget widget && !targets.contains(widget.targetUuid)) {
                targets.add(widget.targetUuid);
            }
        }
        return targets;
    }

    /**
     * The roles that can be guessed, in registration order: NoellesRoles' own list, plus the killer roles when
     * SparkTraits is installed (its role phase lists them too). The Assassin itself is left out: the server ignores
     * any guess at another Assassin.
     * 可猜的身份，按注册顺序：NoellesRoles 自己的列表；安装了 SparkTraits 时再加上杀手身份（它的猜身份阶段同样列出）。
     * 不含刺客本身：服务端会忽略对其他刺客的任何猜测。
     */
    public static List<Role> roles(ClientPlayerEntity player) {
        List<Role> offered = ((AssassinScreenInvoker) new AssassinScreen(player)).sparkstrength$guessableRoles();
        Set<Identifier> offeredIds = new HashSet<>();
        for (Role role : offered) {
            offeredIds.add(role.identifier());
        }
        boolean withKillers = FabricLoader.getInstance().isModLoaded("sparktraits");
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        List<Role> roles = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>();
        for (Role role : WatheRoles.ROLES) {
            if (role == null || role.equals(Noellesroles.ASSASSIN) || !seen.add(role.identifier())) {
                continue;
            }
            boolean killerExtra = withKillers
                    && role.canUseKiller()
                    && !WatheRoles.SPECIAL_ROLES.contains(role)
                    && !role.equals(WatheRoles.VIGILANTE)
                    && game.isRoleEnabled(role);
            if (offeredIds.contains(role.identifier()) || killerExtra) {
                roles.add(role);
            }
        }
        return roles;
    }
}
