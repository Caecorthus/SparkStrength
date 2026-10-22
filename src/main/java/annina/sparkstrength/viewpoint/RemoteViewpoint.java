package annina.sparkstrength.viewpoint;

import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.role.bomber.drone.DronePilotService;
import annina.sparkstrength.role.spiritualist.SpiritPossessionService;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Where a player's view is centred while its camera is away from its body: the drone it pilots
 * ({@link DronePilotService#droneOf}) or the Wraith it possesses ({@link SpiritPossessionService#targetOf}). The
 * {@code mixin/viewpoint/RemoteViewpoint*} streaming mixins ask here, so every hot method carries one injector; the
 * session bookkeeping behind each answer is {@link RemoteViewSessions}.
 * 玩家镜头离开身体时视野的中心：其驾驶的无人机（DronePilotService#droneOf）或其附身的冤魂（SpiritPossessionService#targetOf）。
 * mixin/viewpoint/RemoteViewpoint* 流式加载 mixin 在此查询，因此每个热点方法只带一个注入；每个答案背后的会话簿记见 RemoteViewSessions。
 *
 * <p>Both at once: a player has one Wathe role, a pilot must be the real Bomber and a possessor the Spiritualist, and
 * each service ends its session on the next world tick once the role no longer matches, so both can only answer for
 * the rest of a tick after a role change. Then the drone wins, as when the drone's mixins (listed first, so applied
 * first) were the outer link of each chained {@code @ModifyExpressionValue} pair, and both anchors stay force-tracked,
 * as with the two separate HEAD injectors. One player piloting while another possesses that player is no conflict:
 * every answer is per viewer.
 * 两者同时成立：一名玩家只有一个 Wathe 身份，驾驶者必须是真实炸弹客、附身者必须是灵界行者，且身份不再匹配时各服务会在下一个世界刻
 * 结束会话，因此只有身份变化后的该刻剩余时间内两者才可能同时成立。此时以无人机为准，与原先无人机 mixin（排在前面、先应用）在每对
 * 串联的 @ModifyExpressionValue 中处于外层时一致；两个锚点都保持强制追踪，与原先两个独立的 HEAD 注入一致。一名玩家驾驶、另一名
 * 玩家附身于他并不冲突：所有答案都按观看者区分。</p>
 */
public final class RemoteViewpoint {
    private RemoteViewpoint() {
    }

    /**
     * The entity {@code player}'s chunk view, chunk batches and entity distances are centred on, or null for vanilla
     * (so any failure reverts to the body on the next tick). Hot path: O(1) with no session.
     * 玩家区块视野、区块批次与实体距离所围绕的实体；null 表示使用原版行为（因此任何失效都会在下一刻回到身体）。热路径：无会话时 O(1)。
     */
    public static @Nullable Entity anchorOf(ServerPlayerEntity player) {
        DroneEntity drone = DronePilotService.droneOf(player);
        return drone != null ? drone : SpiritPossessionService.targetOf(player);
    }

    /**
     * Whether {@code player} always tracks {@code entity}, whatever the distance to its body: the drone it pilots or
     * the Wraith it possesses (never itself).
     * 玩家是否无视与身体的距离始终追踪该实体：其驾驶的无人机或其附身的冤魂（从不包括自身）。
     */
    public static boolean isAnchor(ServerPlayerEntity player, Entity entity) {
        if (entity instanceof DroneEntity) {
            return DronePilotService.droneOf(player) == entity;
        }
        return entity instanceof ServerPlayerEntity && entity != player
                && SpiritPossessionService.targetOf(player) == entity;
    }
}
