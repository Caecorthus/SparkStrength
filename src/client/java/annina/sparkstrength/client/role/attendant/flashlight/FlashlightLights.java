package annina.sparkstrength.client.role.attendant.flashlight;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.List;

/**
 * Client facade for every lit flashlight this client can see (any player holding one, no role check).
 * 本客户端可见的所有已开启手电筒的客户端门面（任何手持者都算，不检查身份）。
 */
public final class FlashlightLights {
    public static final int MAX_LIGHTS = 4;

    private FlashlightLights() {
    }

    /** Client init: tick-time ray casting and disconnect cleanup. / 客户端初始化：tick 射线投射与断线清理。 */
    public static void register() {
        // TODO(flashlight-core)
    }

    /** Render thread: lights for this frame, nearest to the camera first, at most MAX_LIGHTS. / 渲染线程：本帧光源，按距相机由近到远。 */
    public static List<FlashlightLight> collect(MinecraftClient client, float tickDelta) {
        return List.of();
    }

    /**
     * Raises an entity's block light to the strongest unoccluded flashlight reaching it (never its own holder's).
     * 将实体方块光提升到照到它的最强未遮挡手电筒亮度（不含持有者自己的手电）。
     */
    public static int boostBlockLight(Entity entity, float tickDelta, int blockLight) {
        return blockLight;
    }
}
