package annina.sparkstrength.role.attendant;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Round-scoped, in-memory room-door history shared by every door-log viewer of one world. Pure: no Minecraft types.
 * 单个世界内所有房门监控查看者共享的本局内存房门记录。纯逻辑，不含 Minecraft 类型。
 *
 * <p>Ids increase monotonically for the server session (never reset by {@link #clear()}), so a client can track
 * "unread" by the newest id it has seen; a merged repeat takes a fresh id because it is news again.
 * id 在服务端会话内单调递增（clear 不会重置），客户端据此以已读到的最大 id 判断未读；合并的重复事件会取新 id，因为它再次成为新消息。</p>
 */
public final class DoorLog {
    public static final int CAPACITY = 60;
    public static final int DOOR_NAME_MAX_LENGTH = 48;
    public static final int ACTOR_NAME_MAX_LENGTH = 32;
    /** A repeat of the newest entry within this window bumps its count instead of adding a row. / 窗口内重复最新条目时只累加次数。 */
    public static final long MERGE_WINDOW_TICKS = 600L;

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private int lastId;

    /**
     * Appends an event, or folds it into the newest entry when identical. Returns whether the visible log changed.
     * 追加一条事件；若与最新条目相同则合并。返回可见记录是否发生变化。
     *
     * <p>An identical repeat in the same tick is dropped (a double door reports both leaves); within
     * {@link #MERGE_WINDOW_TICKS} it bumps the count. Non-actor kinds always drop {@code actor}.
     * 同一 tick 内的相同重复会被丢弃（双开门的两扇门板各报一次）；在合并窗口内则累加次数。不显示操作者的类型总会丢弃 actor。</p>
     */
    public boolean record(DoorLogKind kind, String doorName, @Nullable Actor actor, long tick) {
        Objects.requireNonNull(kind, "kind");
        String name = truncate(doorName == null ? "" : doorName.strip(), DOOR_NAME_MAX_LENGTH);
        if (name.isEmpty()) {
            return false;
        }
        Actor shown = kind.namesActor() ? actor : null;
        Entry newest = entries.peekLast();
        if (newest != null && newest.kind() == kind && newest.doorName().equals(name)
                && Objects.equals(newest.actor(), shown)) {
            if (tick == newest.lastTick()) {
                return false;
            }
            if (tick > newest.lastTick() && tick - newest.lastTick() <= MERGE_WINDOW_TICKS) {
                entries.pollLast();
                entries.addLast(new Entry(++lastId, kind, name, shown, tick, newest.count() + 1));
                return true;
            }
        }
        entries.addLast(new Entry(++lastId, kind, name, shown, tick, 1));
        while (entries.size() > CAPACITY) {
            entries.pollFirst();
        }
        return true;
    }

    /** Newest first. / 最新在前。 */
    public List<Entry> newestFirst() {
        ArrayList<Entry> result = new ArrayList<>(entries.size());
        Iterator<Entry> iterator = entries.descendingIterator();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return List.copyOf(result);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }

    /**
     * Seconds since the entry last happened, never negative. / 条目最后一次发生至今的秒数，不为负。
     */
    public static int ageSeconds(Entry entry, long nowTick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, nowTick - entry.lastTick()) / 20L);
    }

    static String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        int end = maxChars;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * Who the opener appeared to be when the event happened. Anonymous actors carry no uuid and no name; the real
     * uuid of a disguised player must never be stored here.
     * 事件发生时开门者的表面身份。匿名操作者不带 uuid 与名字；伪装者的真实 uuid 绝不能存入此处。
     */
    public record Actor(@Nullable UUID displayUuid, String displayName, boolean anonymous) {
        public Actor {
            if (anonymous || displayUuid == null) {
                anonymous = true;
                displayUuid = null;
                displayName = "";
            } else {
                displayName = truncate(displayName == null ? "" : displayName, ACTOR_NAME_MAX_LENGTH);
            }
        }

        public static Actor anonymousActor() {
            return new Actor(null, "", true);
        }
    }

    public record Entry(int id, DoorLogKind kind, String doorName, @Nullable Actor actor, long lastTick, int count) {
    }
}
