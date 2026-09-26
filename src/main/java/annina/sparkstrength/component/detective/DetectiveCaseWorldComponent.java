package annina.sparkstrength.component.detective;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-only crime-scene snapshots keyed by body entity UUID, plus the admin per-case suspect limit.
 * 仅服务端持有的命案现场快照（按尸体实体 UUID 索引），以及管理员设置的每案调查上限。
 *
 * <p>Snapshots are round state and are cleared by {@link #clearRoundState()}; the suspect limit is a setting and
 * survives it. Nothing here is ever synced: kill-time positions are keyed by REAL player uuids and would expose
 * disguises. Saves holding the retired {@code sparkstrength:criminologist_world} id are simply ignored by CCA.
 * 快照属于回合状态，由 clearRoundState 清除；调查上限是设置项，不随之清除。这里绝不同步给客户端：
 * 案发位置按真实玩家 UUID 记录，同步会暴露伪装。旧存档中的 criminologist_world 数据会被 CCA 忽略。</p>
 */
public final class DetectiveCaseWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<DetectiveCaseWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("detective_case_world"),
            DetectiveCaseWorldComponent.class
    );

    private final LinkedHashMap<UUID, CrimeSceneSnapshot> snapshots = new LinkedHashMap<>();
    // Server-only map from a corpse-mode Morphling's (real uuid, apparent uuid) key to a random case id. The case id is
    // synced to the detective, so it must never be derivable from the real uuid. Transient round state.
    // 仅服务端：尸体模式变形者（真实 UUID + 表面 UUID）到随机命案 ID 的映射。命案 ID 会同步给侦探，
    // 因此绝不能由真实 UUID 推导。属于回合临时状态，不写入 NBT。
    private final Map<String, UUID> morphlingCorpseCaseIds = new LinkedHashMap<>();
    private int suspectLimit = DetectiveCaseRules.DEFAULT_SUSPECT_LIMIT;

    public DetectiveCaseWorldComponent(World world) {
    }

    /**
     * Stores the first snapshot for a body; later ENTITY_LOAD re-fires (chunk reloads) keep the original.
     * Returns false when the body already has one. The oldest snapshots are evicted beyond MAX_SNAPSHOTS.
     * 每具尸体只保留首次快照；区块重载再次触发加载时保持原快照。超过上限时淘汰最旧记录。
     */
    public boolean captureIfAbsent(UUID bodyUuid, CrimeSceneSnapshot snapshot) {
        if (bodyUuid == null || snapshot == null || snapshots.containsKey(bodyUuid)) {
            return false;
        }
        snapshots.put(bodyUuid, snapshot);
        trimToCapacity();
        return true;
    }

    public @Nullable CrimeSceneSnapshot get(UUID bodyUuid) {
        return bodyUuid == null ? null : snapshots.get(bodyUuid);
    }

    /** Stable random case id for one corpse-mode Morphling appearance this round. 本回合某个尸体模式变形者外观的随机命案 ID。 */
    public UUID morphlingCorpseCaseId(UUID morphlingUuid, UUID apparentUuid) {
        return morphlingCorpseCaseIds.computeIfAbsent(morphlingUuid + ":" + apparentUuid, key -> UUID.randomUUID());
    }

    public void clearRoundState() {
        snapshots.clear();
        morphlingCorpseCaseIds.clear();
    }

    public int getSuspectLimit() {
        return suspectLimit;
    }

    public void setSuspectLimit(int limit) {
        suspectLimit = DetectiveCaseRules.clampSuspectLimit(limit);
    }

    private void trimToCapacity() {
        Iterator<UUID> oldest = snapshots.keySet().iterator();
        while (snapshots.size() > DetectiveCaseRules.MAX_SNAPSHOTS && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return false;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("DetectiveSuspectLimit", suspectLimit);
        if (snapshots.isEmpty()) {
            return;
        }
        NbtList records = new NbtList();
        for (Map.Entry<UUID, CrimeSceneSnapshot> entry : snapshots.entrySet()) {
            CrimeSceneSnapshot snapshot = entry.getValue();
            NbtCompound record = new NbtCompound();
            record.putUuid("Body", entry.getKey());
            record.putUuid("Victim", snapshot.victimUuid());
            record.putDouble("OriginX", snapshot.origin().x);
            record.putDouble("OriginY", snapshot.origin().y);
            record.putDouble("OriginZ", snapshot.origin().z);
            NbtList positions = new NbtList();
            for (Map.Entry<UUID, Vec3d> position : snapshot.positions().entrySet()) {
                NbtCompound player = new NbtCompound();
                player.putUuid("Player", position.getKey());
                player.putDouble("X", position.getValue().x);
                player.putDouble("Y", position.getValue().y);
                player.putDouble("Z", position.getValue().z);
                positions.add(player);
            }
            record.put("Positions", positions);
            records.add(record);
        }
        tag.put("DetectiveCaseSnapshots", records);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        suspectLimit = tag.contains("DetectiveSuspectLimit", NbtElement.NUMBER_TYPE)
                ? DetectiveCaseRules.clampSuspectLimit(tag.getInt("DetectiveSuspectLimit"))
                : DetectiveCaseRules.DEFAULT_SUSPECT_LIMIT;

        snapshots.clear();
        NbtList records = tag.getList("DetectiveCaseSnapshots", NbtElement.COMPOUND_TYPE);
        // Keep only the newest MAX_SNAPSHOTS entries (list order is insertion order).
        for (int i = Math.max(0, records.size() - DetectiveCaseRules.MAX_SNAPSHOTS); i < records.size(); i++) {
            NbtCompound record = records.getCompound(i);
            if (!record.containsUuid("Body") || !record.containsUuid("Victim")) {
                continue;
            }
            Vec3d origin = readVec(record, "OriginX", "OriginY", "OriginZ");
            if (origin == null) {
                continue;
            }
            LinkedHashMap<UUID, Vec3d> positions = new LinkedHashMap<>();
            NbtList positionRecords = record.getList("Positions", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < positionRecords.size(); j++) {
                NbtCompound player = positionRecords.getCompound(j);
                Vec3d position = readVec(player, "X", "Y", "Z");
                if (player.containsUuid("Player") && position != null) {
                    positions.put(player.getUuid("Player"), position);
                }
            }
            snapshots.putIfAbsent(record.getUuid("Body"), new CrimeSceneSnapshot(record.getUuid("Victim"), origin, positions));
        }
    }

    private static @Nullable Vec3d readVec(NbtCompound tag, String xKey, String yKey, String zKey) {
        if (!tag.contains(xKey, NbtElement.NUMBER_TYPE)
                || !tag.contains(yKey, NbtElement.NUMBER_TYPE)
                || !tag.contains(zKey, NbtElement.NUMBER_TYPE)) {
            return null;
        }
        double x = tag.getDouble(xKey);
        double y = tag.getDouble(yKey);
        double z = tag.getDouble(zKey);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        return new Vec3d(x, y, z);
    }

    /**
     * Kill-time scene: the victim, the measuring origin (victim's death position, not the movable body) and the
     * position of every living participant (plus the owner) keyed by REAL uuid. Positions are copied and immutable.
     * 案发现场：受害者、测距原点（受害者死亡位置，而非可被拖动的尸体）以及按真实 UUID 记录的在场玩家位置；位置表为不可变副本。
     */
    public record CrimeSceneSnapshot(UUID victimUuid, Vec3d origin, Map<UUID, Vec3d> positions) {
        public CrimeSceneSnapshot {
            Objects.requireNonNull(victimUuid, "victimUuid");
            Objects.requireNonNull(origin, "origin");
            LinkedHashMap<UUID, Vec3d> copy = new LinkedHashMap<>();
            if (positions != null) {
                for (Map.Entry<UUID, Vec3d> entry : positions.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        copy.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            positions = Collections.unmodifiableMap(copy);
        }
    }
}
