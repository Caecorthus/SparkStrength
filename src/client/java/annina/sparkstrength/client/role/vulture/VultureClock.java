package annina.sparkstrength.client.role.vulture;

import annina.sparkstrength.role.vulture.VultureSkateboardRules;
import net.minecraft.world.World;

/**
 * The client's reading of the server's world time for the Vulture's countdowns (skateboard lock, Super Curse). Both
 * are synced as world-time deadlines, so reading them against one clock makes equal cooldowns show equal seconds, and
 * the display follows the server when it runs slow instead of counting down at the client's own 20 ticks a second.
 * 客户端为秃鹫倒计时（滑板锁、超级骂）读取的服务端世界时间。二者都以世界时间的截止时刻同步，用同一时钟读取时，
 * 相同的冷却显示相同的秒数；服务端变慢时显示跟随服务端，而不是按客户端自己的每秒 20 刻倒数。
 *
 * <p>The client advances world time itself and the server corrects it once a second, so the raw value can step back a
 * tick or two; {@link VultureSkateboardRules#steadyTime} holds through those steps so a countdown never flickers.
 * 客户端自行推进世界时间，服务端每秒校正一次，原始值可能回退一两刻；{@link VultureSkateboardRules#steadyTime}
 * 在这些回退期间保持不动，倒计时不会来回跳。</p>
 */
public final class VultureClock {
    private static long last = Long.MIN_VALUE;

    private VultureClock() {
    }

    public static long now(World world) {
        last = VultureSkateboardRules.steadyTime(last, world.getTime());
        return last;
    }
}
