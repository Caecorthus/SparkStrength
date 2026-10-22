package annina.sparkstrength.client.viewpoint;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import annina.sparkstrength.client.role.spiritualist.SpiritPossessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Client hooks that are the same for every remote viewpoint (Bomber drone piloting, Spiritualist Wraith possession),
 * asked by the {@code client/mixin/viewpoint/RemoteViewpoint*} mixins. Camera, input, HUD and session rules stay in
 * {@link DronePilotClient} and {@link SpiritPossessionClient}. The drone is asked first everywhere, as when its
 * mixins (listed first) ran first; the answers only overlap if both sessions were ever on at once (see the server's
 * {@code RemoteViewpoint}).
 * 所有远程视点（炸弹客无人机驾驶、灵界行者附身冤魂）相同的客户端钩子，由 client/mixin/viewpoint/RemoteViewpoint* 查询。镜头、
 * 输入、HUD 与会话规则仍留在 DronePilotClient 与 SpiritPossessionClient。各处都先询问无人机，与其 mixin（排在前面）先执行时一致；
 * 只有两个会话同时存在时答案才会重叠（见服务端 RemoteViewpoint）。
 */
public final class RemoteViewpointClient {
    private RemoteViewpointClient() {
    }

    /**
     * Render-grid centre axis for {@code WorldRenderer.setupTerrain} (vanilla renderer only): the camera while it
     * looks through a drone or a possessed Wraith, otherwise vanilla's player position. Axis 0/1/2 = x/y/z.
     * WorldRenderer.setupTerrain 的渲染网格中心坐标（仅原版渲染器）：镜头通过无人机或被附身冤魂观看时取镜头位置，否则保持原版的
     * 玩家位置。axis 0/1/2 = x/y/z。
     */
    public static double renderGridCenter(double original, Camera camera, int axis) {
        if (!DronePilotClient.isViewingDrone() && !SpiritPossessionClient.isPossessing()) {
            return original;
        }
        Vec3d pos = camera.getPos();
        return axis == 0 ? pos.x : axis == 1 ? pos.y : pos.z;
    }

    /**
     * The local body's own {@code SELF} moves are frozen for a whole remote view and afterwards until its chunks are
     * back ({@link BodySettleHold}).
     * 整个远程画面期间及之后、直到身体区块回来之前（见 BodySettleHold），冻结本地身体自身的 SELF 移动。
     */
    public static boolean freezesBody(ClientPlayerEntity body) {
        return DronePilotClient.freezesBody(body) || SpiritPossessionClient.freezesBody(body);
    }

    /**
     * A vanilla {@code SetCameraEntityS2CPacket} (spectating, NoellesRoles Taotie/Jester...) outranks every remote
     * view: each one running ends locally without restoring the camera and tells the server.
     * 原版 SetCameraEntityS2CPacket（旁观、NoellesRoles 饕餮/小丑等）优先于所有远程画面：正在进行的画面都在本地结束且不恢复镜头，
     * 并通知服务器。
     */
    public static void onServerCameraOverride(MinecraftClient client) {
        DronePilotClient.onServerCameraOverride(client);
        SpiritPossessionClient.onServerCameraOverride(client);
    }
}
