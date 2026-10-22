package annina.sparkstrength.client.viewpoint;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.Nullable;

/**
 * The body hold after a remote view ends: the camera returns before the server re-streams the chunks the re-centred
 * view unloaded around the body, and 1.21.1's {@code ClientWorld.isChunkLoaded} is always true, so vanilla would let
 * the body fall through them. The hold lasts until every chunk under the body is back on this client, capped at
 * {@link #MAX_TICKS}, and only for the session's own body (a respawned player is a new instance). One per feature;
 * render thread only.
 * 远程画面结束后的身体保持：镜头先于服务器重新推送重新居中的视野在身体周围卸载的区块回到身体，而 1.21.1 的
 * ClientWorld.isChunkLoaded 恒为 true，原版会让身体穿过这些区块下落。保持到身体下方区块在本客户端全部重新加载为止，上限
 * MAX_TICKS，且只针对会话本身的身体（重生后的玩家是新实例）。每个功能一个；仅限渲染线程。
 */
public final class BodySettleHold {
    /**
     * Fail-safe cap on the hold (the body's chunks normally return within a few ticks).
     * 保持的兜底上限（身体所在区块通常几刻内就会重新送达）。
     */
    public static final int MAX_TICKS = 200;

    private @Nullable ClientPlayerEntity body;
    private int ticks;

    /** Holds {@code body} from now on. / 从现在起保持该身体。 */
    public void start(@Nullable ClientPlayerEntity body) {
        this.body = body;
        this.ticks = MAX_TICKS;
    }

    public void clear() {
        body = null;
        ticks = 0;
    }

    /** {@code player} is the held body and its chunks are not back yet. / 该玩家是被保持的身体且其区块尚未回来。 */
    public boolean holds(ClientPlayerEntity player) {
        return ticks > 0 && player == body && !chunksLoaded(player);
    }

    /** Ends the hold once the chunks are back, the body changed, or at the cap. / 区块回来、身体变化或到达上限时结束保持。 */
    public void tick(MinecraftClient client) {
        if (ticks <= 0) {
            return;
        }
        ClientPlayerEntity current = client.player;
        if (current == null || current != body || --ticks <= 0 || chunksLoaded(current)) {
            clear();
        }
    }

    /** Every chunk the body's hitbox overlaps is loaded on this client. / 身体碰撞箱覆盖的区块在本客户端均已加载。 */
    private static boolean chunksLoaded(ClientPlayerEntity body) {
        Box box = body.getBoundingBox();
        ClientChunkManager chunks = body.clientWorld.getChunkManager();
        int minX = ChunkSectionPos.getSectionCoord(box.minX);
        int maxX = ChunkSectionPos.getSectionCoord(box.maxX);
        int minZ = ChunkSectionPos.getSectionCoord(box.minZ);
        int maxZ = ChunkSectionPos.getSectionCoord(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!chunks.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
