package annina.sparkstrength.component.sabotage;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.sabotage.EnginePhase;
import annina.sparkstrength.sabotage.EngineRules;
import annina.sparkstrength.sabotage.OutageMode;
import annina.sparkstrength.sabotage.SabotageRules;
import annina.sparkstrength.sabotage.SabotageType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-only round state of the sabotage system. Nothing here is saved: junction box configuration and looks live
 * in their blockstates, and every field is round-scoped. Clients get the parts they may see through packets.
 * 破坏系统的服务端本局状态。这里不保存任何东西：接线箱的配置与外观在方块状态里，其余字段都只在本局有效。
 * 客户端通过数据包拿到自己可见的部分。
 */
public final class SabotageWorldComponent implements Component {
    public static final ComponentKey<SabotageWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("sabotage_world"), SabotageWorldComponent.class
    );

    /**
     * The one sabotage in progress, lights or engine; only one may exist at a time. {@code mode} only means something
     * for lights.
     * 正在进行的唯一一个破坏，灯光或动力；同一时间只能有一个。mode 只对灯光有意义。
     */
    public record Outage(int id, SabotageType type, OutageMode mode, long startTick, @Nullable UUID initiator,
                         String initiatorName, boolean masterSaboteur) {
    }

    public enum LogKind {
        BOX_BROKEN(0), BOX_HACKED(1), BOX_REPAIRED(2), TRIGGERED(3), REPAIRED(4),
        /** The Engineer's Sabotage Repair from the shop. / 工程师在商店买下的破坏维修。 */
        REMOTE_REPAIRED(5), EXPIRED(6),
        READY(7),
        /** The 60 s ran out: the train halted. / 60 秒已过：列车停驶。 */
        ENGINE_WRECKED(8),
        /** Halted for the cap: emergency power. / 停驶到上限：应急动力。 */
        ENGINE_EMERGENCY(9);

        private final int wire;

        LogKind(int wire) {
            this.wire = wire;
        }

        public int wire() {
            return wire;
        }

        public static @Nullable LogKind byWire(int wire) {
            for (LogKind kind : values()) {
                if (kind.wire == wire) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** One line of the killers' sabotage log; {@code actor} is a game name or empty. / 杀手破坏日志的一行；actor 为游戏名或空。 */
    public record LogEntry(long tick, LogKind kind, String actor, SabotageType type) {
    }

    /** Server-only durability of a damaged or opened box. / 受损或已打开箱子的服务端耐久。 */
    public static final class BoxProgress {
        public int durability = SabotageRules.DURABILITY_MAX;
        public long lastDamageTick = Long.MIN_VALUE / 2;
        public int lastCrackStage = -1;
        public long lastCrackSentTick = Long.MIN_VALUE / 2;
    }

    private boolean mapReady;
    private long roundStartTick;
    private boolean awaitingMapReset;
    private final List<BlockPos> junctionBoxes = new ArrayList<>();
    private final List<BlockPos> breakers = new ArrayList<>();
    private final List<BlockPos> engineUnits = new ArrayList<>();
    private final Map<BlockPos, BoxProgress> boxes = new HashMap<>();
    private final EnumMap<SabotageType, Long> unlockReadyTick = new EnumMap<>(SabotageType.class);
    private final EnumSet<SabotageType> readyAnnounced = EnumSet.noneOf(SabotageType.class);
    private boolean autoUnlocked;
    private long teamCooldownEndTick;
    private @Nullable Outage outage;
    private int outageCounter;
    private final ArrayDeque<LogEntry> log = new ArrayDeque<>();
    private long engineTriggeredAt;
    private @Nullable EnginePhase appliedEnginePhase;

    public SabotageWorldComponent(World world) {
    }

    public void clearRoundState() {
        mapReady = false;
        roundStartTick = 0L;
        awaitingMapReset = false;
        junctionBoxes.clear();
        breakers.clear();
        engineUnits.clear();
        boxes.clear();
        unlockReadyTick.clear();
        readyAnnounced.clear();
        autoUnlocked = false;
        teamCooldownEndTick = 0L;
        outage = null;
        log.clear();
        clearEngine();
    }

    public boolean isMapReady() {
        return mapReady;
    }

    public long roundStartTick() {
        return roundStartTick;
    }

    public void beginRound(long now) {
        clearRoundState();
        roundStartTick = now;
        awaitingMapReset = true;
    }

    public boolean isAwaitingMapReset() {
        return awaitingMapReset;
    }

    public void markMapReady(List<BlockPos> junctionBoxes, List<BlockPos> breakers, List<BlockPos> engineUnits) {
        this.awaitingMapReset = false;
        this.mapReady = true;
        this.junctionBoxes.clear();
        this.junctionBoxes.addAll(junctionBoxes);
        this.breakers.clear();
        this.breakers.addAll(breakers);
        this.engineUnits.clear();
        this.engineUnits.addAll(engineUnits);
        this.boxes.clear();
    }

    public List<BlockPos> junctionBoxes() {
        return Collections.unmodifiableList(junctionBoxes);
    }

    public List<BlockPos> breakers() {
        return Collections.unmodifiableList(breakers);
    }

    public boolean hasBreakers() {
        return !breakers.isEmpty();
    }

    /** Engine units found in the play area when the map was scanned. / 扫描地图时在对局区找到的动力装置。 */
    public List<BlockPos> engineUnits() {
        return Collections.unmodifiableList(engineUnits);
    }

    /**
     * Drops engine units that are gone (removed or replaced since the scan); true if any was dropped.
     * 去掉已经不在的动力装置（扫描后被移除或替换）；有去掉的就返回 true。
     */
    public boolean pruneEngineUnits(java.util.function.Predicate<BlockPos> stillThere) {
        return engineUnits.removeIf(pos -> !stillThere.test(pos));
    }

    /** The map supports the engine sabotage. / 本图支持动力破坏。 */
    public boolean hasEngineUnits() {
        return !engineUnits.isEmpty();
    }

    public boolean hasJunctionBoxes() {
        return !junctionBoxes.isEmpty();
    }

    public void addJunctionBox(BlockPos pos) {
        if (!junctionBoxes.contains(pos)) {
            junctionBoxes.add(pos.toImmutable());
        }
    }

    public void addBreaker(BlockPos pos) {
        if (!breakers.contains(pos)) {
            breakers.add(pos.toImmutable());
        }
    }

    public BoxProgress box(BlockPos pos) {
        return boxes.computeIfAbsent(pos.toImmutable(), ignored -> new BoxProgress());
    }

    public @Nullable BoxProgress existingBox(BlockPos pos) {
        return boxes.get(pos);
    }

    public Map<BlockPos, BoxProgress> boxProgress() {
        return boxes;
    }

    public void forgetBox(BlockPos pos) {
        boxes.remove(pos);
    }

    public boolean isUnlocked(SabotageType type) {
        return unlockReadyTick.containsKey(type);
    }

    public long readyTick(SabotageType type) {
        Long ready = unlockReadyTick.get(type);
        return ready == null ? Long.MAX_VALUE : ready;
    }

    public void unlock(SabotageType type, long readyTick) {
        unlockReadyTick.putIfAbsent(type, readyTick);
    }

    /** Unlocked by a map without junction boxes, not by a hack. / 因地图没有接线箱而解锁，而不是因为入侵。 */
    public void autoUnlock(SabotageType type, long readyTick) {
        if (!unlockReadyTick.containsKey(type)) {
            unlockReadyTick.put(type, readyTick);
            autoUnlocked = true;
        }
    }

    public boolean isAutoUnlocked() {
        return autoUnlocked;
    }

    /**
     * Only the automatic lights unlock is taken back (a fallback scan ran before the copy finished); an engine unlock
     * always comes from a hack and stays.
     * 只撤回自动解锁的灯光（兜底扫描早于复制完成）；动力的解锁一定来自入侵，保持不变。
     */
    public void revokeAutoUnlock() {
        if (autoUnlocked) {
            unlockReadyTick.remove(SabotageType.LIGHTS);
            readyAnnounced.remove(SabotageType.LIGHTS);
            autoUnlocked = false;
        }
    }

    public void setReadyTick(SabotageType type, long readyTick) {
        if (unlockReadyTick.containsKey(type)) {
            unlockReadyTick.put(type, readyTick);
        }
    }

    public EnumSet<SabotageType> unlocked() {
        return unlockReadyTick.isEmpty() ? EnumSet.noneOf(SabotageType.class) : EnumSet.copyOf(unlockReadyTick.keySet());
    }

    /** True the first time a type is seen ready, so the "ready" notice goes out once. / 某类型首次就绪时返回 true，使「已就绪」提示只发一次。 */
    public boolean markReadyAnnounced(SabotageType type) {
        return readyAnnounced.add(type);
    }

    public long teamCooldownEndTick() {
        return teamCooldownEndTick;
    }

    public void setTeamCooldownEndTick(long tick) {
        this.teamCooldownEndTick = tick;
    }

    public @Nullable Outage outage() {
        return outage;
    }

    public Outage startOutage(SabotageType type, OutageMode mode, long now, @Nullable UUID initiator,
                              String initiatorName, boolean masterSaboteur) {
        outage = new Outage(++outageCounter, type, mode, now, initiator, initiatorName, masterSaboteur);
        return outage;
    }

    public @Nullable Outage endOutage() {
        Outage ended = outage;
        outage = null;
        return ended;
    }

    /** An engine sabotage holds the one outage slot, from its trigger until it is repaired or aborted. / 动力破坏占着唯一的破坏名额，从触发直到修好或中止。 */
    public boolean isEngineOn() {
        return outage != null && outage.type() == SabotageType.ENGINE;
    }

    /** World tick the engine sabotage was triggered; 0 when none is on. / 动力破坏触发时的世界 tick；没有时为 0。 */
    public long engineTriggeredAt() {
        return engineTriggeredAt;
    }

    public void setEngineTriggeredAt(long tick) {
        this.engineTriggeredAt = tick;
    }

    /**
     * The engine sabotage's phase at {@code now}, derived from the trigger tick (null when none is on). This is the one
     * phase decision; nothing reads a stored phase.
     * 动力破坏在 now 时刻的阶段，由触发 tick 推导（没有时为 null）。这是唯一的阶段判定，不读取任何保存的阶段。
     */
    public @Nullable EnginePhase enginePhase(long now) {
        return isEngineOn() ? EngineRules.effectivePhase(now, engineTriggeredAt) : null;
    }

    /**
     * The last phase whose side effects ran; only used to detect transitions, never to decide the phase.
     * 最近一次已执行副作用的阶段；只用于发现阶段转换，从不用来判定阶段。
     */
    public @Nullable EnginePhase appliedEnginePhase() {
        return appliedEnginePhase;
    }

    public void setAppliedEnginePhase(@Nullable EnginePhase phase) {
        this.appliedEnginePhase = phase;
    }

    /** Forget the engine sabotage's clock; its outage is ended separately. / 清掉动力破坏的时钟；它的名额另行结束。 */
    public void clearEngine() {
        engineTriggeredAt = 0L;
        appliedEnginePhase = null;
    }

    public void log(long tick, LogKind kind, String actor, SabotageType type) {
        log.addLast(new LogEntry(tick, kind, actor == null ? "" : actor, type));
        while (log.size() > SabotageRules.LOG_LIMIT) {
            log.removeFirst();
        }
    }

    public List<LogEntry> logEntries() {
        return List.copyOf(log);
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }
}
