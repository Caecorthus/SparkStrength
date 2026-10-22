package annina.sparkstrength.client.role.engineer;

import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import net.minecraft.client.MinecraftClient;

/**
 * The local stun state read by the client key gate (capture device, Jester Moment freeze). Kept free of other client
 * classes because key bindings are polled from the start.
 * 客户端按键闸门读取的本地定身状态（捕捉装置、小丑时刻定身）。按键从启动起就会被查询，因此这里不引用其他客户端类。
 */
public final class EngineerStunClient {
    private EngineerStunClient() {
    }

    public static boolean isLocalPlayerStunned() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && EngineerStunnedPlayerComponent.KEY.get(client.player).isStunned();
    }
}
