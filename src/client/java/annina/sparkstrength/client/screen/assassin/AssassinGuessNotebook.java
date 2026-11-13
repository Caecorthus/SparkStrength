package annina.sparkstrength.client.screen.assassin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Assassin's pencilled guesses: one role per target, plus the target last looked at, kept on this client only for
 * the rest of the round so the guess screen doubles as a notebook. Nothing here is synced or sent; only the Guess
 * button sends anything.
 * 刺客用铅笔记下的猜测：每个目标一个身份，外加最后查看的目标；只保存在本客户端、到本局结束为止，让猜测界面兼作笔记本。
 * 这里的内容既不同步也不发送；只有“确认猜测”按钮会发包。
 *
 * <p>Round boundary: the marks are wiped on the first client tick where the local player is no longer a living
 * Assassin (died, round over, Wathe back to STARTING/INACTIVE, role changed) or the client world changed (left,
 * rejoined). Being swallowed by the Taotie keeps them: the Assassin is still in the round. The watcher registers itself
 * the first time anything is marked, which is the only way marks can exist.
 * 局边界：本地玩家不再是存活刺客（死亡、本局结束、Wathe 回到 STARTING/INACTIVE、身份变化）或客户端世界改变（退出、重进）后的
 * 第一个客户端 tick 清空所有标记。被饕餮吞下不清空：刺客仍在本局中。监听器在第一次写入时自行注册，而标记也只能这样产生。</p>
 */
final class AssassinGuessNotebook {
    private static final Map<UUID, Identifier> MARKS = new HashMap<>();
    private static @Nullable UUID focusedTarget;
    private static @Nullable ClientWorld world;
    private static boolean watching;

    private AssassinGuessNotebook() {
    }

    static @Nullable Identifier markOf(UUID target) {
        return MARKS.get(target);
    }

    static @Nullable UUID focusedTarget() {
        return focusedTarget;
    }

    static void mark(UUID target, Identifier role) {
        bindToCurrentWorld();
        MARKS.put(target, role);
    }

    static void erase(UUID target) {
        MARKS.remove(target);
    }

    static void focus(@Nullable UUID target) {
        bindToCurrentWorld();
        focusedTarget = target;
    }

    static void clear() {
        MARKS.clear();
        focusedTarget = null;
        world = null;
    }

    /** Whether the local player still has a stake in this round's notes. / 本地玩家是否仍与本局笔记相关。 */
    static boolean isLivingAssassin(@Nullable ClientPlayerEntity player) {
        return player != null
                && GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.ASSASSIN)
                && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    private static void bindToCurrentWorld() {
        world = MinecraftClient.getInstance().world;
        if (!watching) {
            watching = true;
            ClientTickEvents.END_CLIENT_TICK.register(AssassinGuessNotebook::watchRound);
        }
    }

    private static void watchRound(MinecraftClient client) {
        if (MARKS.isEmpty() && focusedTarget == null) {
            return;
        }
        if (client.world == null || client.world != world || !isLivingAssassin(client.player)) {
            clear();
        }
    }
}
