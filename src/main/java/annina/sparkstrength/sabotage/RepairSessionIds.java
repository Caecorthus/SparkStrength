package annina.sparkstrength.sabotage;

/**
 * One counter for every repair panel session (circuit breaker and engine unit), so a session id never names a session
 * of the other kind: both panels share the complete/cancel/close packets. Server thread only; never 0, which is the
 * engine status panel's "no session".
 * 所有维修面板会话（电闸与动力装置）共用的计数器，让一个会话 id 永远不会指向另一种面板的会话：两种面板共用完成、放弃与关闭
 * 数据包。仅限服务端线程；永远不是 0，0 表示动力状态面板的「没有会话」。
 */
final class RepairSessionIds {
    private static int next = 1;

    private RepairSessionIds() {
    }

    static int next() {
        int id = next;
        next = next == Integer.MAX_VALUE ? 1 : next + 1;
        return id;
    }
}
