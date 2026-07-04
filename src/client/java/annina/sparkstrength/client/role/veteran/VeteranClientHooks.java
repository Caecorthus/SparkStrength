package annina.sparkstrength.client.role.veteran;

import annina.sparkstrength.role.veteran.VeteranRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 老兵客户端高亮逻辑。
 *
 * <p>停电状态由服务端同步到 {@link #blackoutActive}。高亮事件本身不检查墙体视线，
 * 因此返回 always 结果后就是需求里的“10 格范围内穿墙透视”。</p>
 */
public final class VeteranClientHooks {
    private static boolean blackoutActive;

    private VeteranClientHooks() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(VeteranClientHooks::highlight);
    }

    public static void setBlackoutActive(boolean active) {
        blackoutActive = active;
    }

    public static void resetBlackoutState() {
        blackoutActive = false;
    }

    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }

        GameWorldComponent game = GameWorldComponent.KEY.get(viewer.getWorld());
        Role role = game.getRole(viewer);
        return VeteranRules.blackoutHighlight(
                role,
                blackoutActive,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                viewer.getUuid().equals(targetPlayer.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(targetPlayer),
                GameFunctions.isPlayerSpectatingOrCreative(targetPlayer),
                viewer.squaredDistanceTo(targetPlayer)
        );
    }
}
