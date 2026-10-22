package annina.sparkstrength.client.instinct;

import annina.sparkstrength.client.render.ClientFrame;
import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.role.coroner.CoronerService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * What the instinct listeners need to know about the local viewer, independent of the target. Wathe asks for an
 * outline 2-3 times per visible entity per frame and every listener runs each time, so during a world render this is
 * resolved once per frame ({@link ClientFrame}); the trait check alone is two reflective calls into SparkTraits.
 * Outside a world render it is resolved on every call, as before.
 * 本能监听器需要的、与目标无关的本地观察者信息。Wathe 每帧对每个可见实体查询 2-3 次描边，每次都会运行全部监听器，
 * 因此在世界渲染期间每帧只解析一次（见 ClientFrame）；仅词条判定就要两次反射调用 SparkTraits。世界渲染之外仍然每次调用都解析。
 *
 * @param role           {@code getRole}, the real role / 真实身份
 * @param effectiveKiller SparkTraits' effective faction (Impostor/Conscience) / SparkTraits 有效阵营（内鬼/善良）
 * @param disguiseRole   the Coroner's active corpse disguise for rules / 验尸官当前用于规则判定的尸体伪装身份
 */
public record InstinctViewer(ClientPlayerEntity player, GameWorldComponent game, @Nullable Role role,
                             boolean playingAndAlive, boolean effectiveKiller, @Nullable Role disguiseRole) {
    private static @Nullable InstinctViewer cached;
    private static int cachedEpoch;

    /** Render thread: the local viewer, or null without a player. / 渲染线程：本地观察者；没有玩家时为 null。 */
    public static @Nullable InstinctViewer current() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return null;
        }
        boolean rendering = ClientFrame.inWorldRender();
        InstinctViewer viewer = cached;
        if (rendering && viewer != null && viewer.player == player && cachedEpoch == ClientFrame.epoch()) {
            return viewer;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        Role role = game.getRole(player);
        viewer = new InstinctViewer(player, game, role, GameFunctions.isPlayerPlayingAndAlive(player),
                SparkTraitsCompat.isEffectiveKiller(role, player), CoronerService.activeDisguiseRoleForRules(player));
        cached = rendering ? viewer : null;
        cachedEpoch = ClientFrame.epoch();
        return viewer;
    }
}
