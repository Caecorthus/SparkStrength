package annina.sparkstrength.mixin.bomber;

import annina.sparkstrength.role.bomber.TimedBombMarkerSync;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk start-tracking seam for the private timed-bomb markers. Vanilla sends every chunk a player starts to track
 * (join, movement, respawn, dimension change) through this one method, and Fabric API has no per-player chunk event.
 * The traps of that chunk follow its data packet, and only to a trap viewer: the block entities' own client NBT no
 * longer carries the owner. Observes only; the chunk packet itself is untouched. SparkTraits hooks the same TAIL for
 * its blue-poison markers; the two injections are independent.
 * 私有定时炸弹标记的“开始追踪区块”接入点。原版把玩家开始追踪的每个区块（加入、移动、重生、切换维度）都经由这一个方法发送，
 * 而 Fabric API 没有按玩家的区块事件。该区块的陷阱紧随其数据包发送，且只发给陷阱观察者：方块实体自身的客户端 NBT 已不再
 * 携带埋设者。仅作观察，不改动区块数据包本身。SparkTraits 也在同一 TAIL 处为蓝毒标记注入，两者互不影响。
 */
@Mixin(ChunkDataSender.class)
public abstract class TimedBombChunkSendMixin {
    @Inject(method = "sendChunkData", at = @At("TAIL"))
    private static void sparkstrength$sendTimedBombMarkers(
            ServerPlayNetworkHandler handler,
            ServerWorld world,
            WorldChunk chunk,
            CallbackInfo ci
    ) {
        TimedBombMarkerSync.onChunkSent(handler.player, world, chunk);
    }
}
