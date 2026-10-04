package annina.sparkstrength.client.role.attendant.flashlight;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * One lit flashlight as seen by this client for the current frame. Origin and direction are frame-interpolated
 * world coordinates; {@code rayMap} carries the latest tick's occlusion grid, or null before its first cast.
 * 当前帧本客户端看到的一支已开启手电筒。origin 与 direction 为逐帧插值的世界坐标；
 * {@code rayMap} 为最近一个 tick 的遮挡网格，首次投射前为 null。
 *
 * @param cameraAnchored true when the light sits exactly at the camera (local player, first person): every surface
 *                       the camera sees is lit, so receivers skip the shadow test. / 光源与相机重合（本地玩家第一人称）时为
 *                       true：相机可见的表面都被照亮，因此受光面跳过阴影测试。
 */
public record FlashlightLight(
        int ownerEntityId,
        Vec3d origin,
        Vec3d direction,
        boolean cameraAnchored,
        @Nullable FlashlightRayMap rayMap
) {
}
